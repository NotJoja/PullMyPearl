package de.joah.pullmypearl;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;
import java.nio.file.*;
import java.util.zip.ZipInputStream;

public class PullMyPearlClient implements ClientModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("PullMyPearl");
	static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip";
	static final String MODEL_DIR = "vosk-model-small-de-0.15";
	static final long COOLDOWN_MS = 3000;
	private static final String NON_WORD = "[^\\p{L}\\p{N}]+";

	private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);

	private volatile long lastTrigger = 0;
	private volatile int generation = 0;

	static PullMyPearlClient instance;

	@Override
	public void onInitializeClient() {
		instance = this;
		Config.get();
		startListening();
	}

	/** Names of all audio devices that can capture 16 kHz mono audio. */
	static List<String> microphones() {
		List<String> out = new ArrayList<>();
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
		for (Mixer.Info mi : AudioSystem.getMixerInfo())
			if (AudioSystem.getMixer(mi).isLineSupported(info)) out.add(mi.getName());
		return out;
	}

	private static TargetDataLine openMic() throws Exception {
		String wanted = Config.get().microphone.trim().toLowerCase();
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
		if (!wanted.isEmpty()) {
			for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
				Mixer mixer = AudioSystem.getMixer(mi);
				if (mi.getName().toLowerCase().contains(wanted) && mixer.isLineSupported(info))
					return (TargetDataLine) mixer.getLine(info);
			}
			LOG.warn("Microphone '{}' not found, using system default", wanted);
			chat("Microphone '" + Config.get().microphone + "' not found, using system default.");
		}
		return AudioSystem.getTargetDataLine(FORMAT);
	}

	/** (Re)starts the listener thread; any previous one stops. */
	void startListening() {
		int gen = ++generation;
		Thread t = new Thread(() -> run(gen), "PullMyPearl-Voice");
		t.setDaemon(true);
		t.start();
	}

	private void run(int gen) {
		try {
			Path dir = FabricLoader.getInstance().getGameDir().resolve("pullmypearl");
			Path model = dir.resolve(MODEL_DIR);
			if (!Files.isDirectory(model)) download(dir);

			LibVosk.setLogLevel(LogLevel.WARNINGS);
			List<String> words = words(Config.get().phrase);
			StringBuilder grammar = new StringBuilder("[");
			for (String w : words) grammar.append('"').append(w).append("\", ");
			grammar.append("\"[unk]\"]");
			try (Model m = new Model(model.toString());
			     // Restricted grammar = far more reliable for a fixed phrase
			     Recognizer rec = new Recognizer(m, 16000f, grammar.toString());
			     TargetDataLine mic = openMic()) {
				mic.open(FORMAT);
				mic.start();
				LOG.info("Listening for the trigger phrase...");
				chat("Voice trigger ready. Say \"" + Config.get().phrase + "\".");
				byte[] buf = new byte[4096];
				while (gen == generation) {
					int n = mic.read(buf, 0, buf.length);
					if (n <= 0) continue;
					boolean fin = rec.acceptWaveForm(buf, n);
					String json = fin ? rec.getResult() : rec.getPartialResult();
					if (matches(json, words)) {
						rec.reset();
						trigger();
					}
				}
			}
		} catch (Throwable e) {
			LOG.error("Voice recognition failed", e);
			chat("PullMyPearl voice failed: " + e);
		}
	}

	/** Lower-cased words of the phrase, without punctuation or duplicates. */
	static List<String> words(String phrase) {
		List<String> out = new ArrayList<>();
		for (String w : phrase.toLowerCase().split(NON_WORD))
			if (!w.isEmpty() && !out.contains(w)) out.add(w);
		return out;
	}

	/** True when (almost) all words of the phrase were heard: at least 75%, rounded up. */
	private static boolean matches(String json, List<String> words) {
		if (words.isEmpty()) return false;
		var heard = new java.util.HashSet<>(java.util.Arrays.asList(json.toLowerCase().split(NON_WORD)));
		long hits = words.stream().filter(heard::contains).count();
		return hits >= Math.ceil(words.size() * 0.75);
	}

	private void trigger() {
		long now = System.currentTimeMillis();
		if (now - lastTrigger < COOLDOWN_MS) return;
		lastTrigger = now;
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player == null || mc.gameMode == null) return;
			HitResult hit = mc.hitResult;
			if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
				mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
			} else {
				mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
			}
			mc.player.swing(InteractionHand.MAIN_HAND);
		});
	}

	private static void chat(String msg) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[PullMyPearl] " + msg)); });
	}

	private static void download(Path dir) throws Exception {
		LOG.info("Downloading German speech model (~45MB)...");
		chat("Downloading German speech model (~45MB), one time only...");
		Files.createDirectories(dir);
		try (InputStream in = URI.create(MODEL_URL).toURL().openStream();
		     ZipInputStream zip = new ZipInputStream(in)) {
			var e = zip.getNextEntry();
			while (e != null) {
				Path out = dir.resolve(e.getName()).normalize();
				if (!out.startsWith(dir)) throw new SecurityException("bad zip entry");
				if (e.isDirectory()) Files.createDirectories(out);
				else { Files.createDirectories(out.getParent()); Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING); }
				e = zip.getNextEntry();
			}
		}
	}
}
