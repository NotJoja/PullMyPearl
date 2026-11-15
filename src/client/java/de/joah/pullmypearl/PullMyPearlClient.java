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
import java.net.URI;
import java.nio.file.*;
import java.util.zip.ZipInputStream;

public class PullMyPearlClient implements ClientModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("PullMyPearl");
	static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip";
	static final String MODEL_DIR = "vosk-model-small-de-0.15";
	static final long COOLDOWN_MS = 3000;

	private volatile long lastTrigger = 0;

	@Override
	public void onInitializeClient() {
		Thread t = new Thread(this::run, "PullMyPearl-Voice");
		t.setDaemon(true);
		t.start();
	}

	private void run() {
		try {
			Path dir = FabricLoader.getInstance().getGameDir().resolve("pullmypearl");
			Path model = dir.resolve(MODEL_DIR);
			if (!Files.isDirectory(model)) download(dir);

			LibVosk.setLogLevel(LogLevel.WARNINGS);
			AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
			try (Model m = new Model(model.toString());
			     // Restricted grammar = far more reliable for a fixed phrase
			     Recognizer rec = new Recognizer(m, 16000f, "[\"klaus\", \"pulle\", \"meine\", \"perle\", \"[unk]\"]");
			     TargetDataLine mic = AudioSystem.getTargetDataLine(fmt)) {
				mic.open(fmt);
				mic.start();
				LOG.info("Listening for the trigger phrase...");
				chat("Voice trigger ready. Say \"Klaus, pulle meine Perle\".");
				byte[] buf = new byte[4096];
				while (true) {
					int n = mic.read(buf, 0, buf.length);
					if (n <= 0) continue;
					boolean fin = rec.acceptWaveForm(buf, n);
					String json = fin ? rec.getResult() : rec.getPartialResult();
					if (matches(json)) {
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

	private static boolean matches(String json) {
		String s = json.toLowerCase();
		return s.contains("pulle") && s.contains("perle");
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
