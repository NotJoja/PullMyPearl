package de.joah.pullmypearl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Stored in .minecraft/config/pullmypearl.json */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pullmypearl.json");

	/** Name of the microphone to use (matched case-insensitively). Empty = system default. */
	public String microphone = "";

	/** Sentence that triggers the right-click. */
	public String phrase = DEFAULT_PHRASE;

	public static final String DEFAULT_PHRASE = "Klaus, pulle meine Perle";

	private static Config instance;

	public static Config get() {
		if (instance == null) {
			try {
				if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), Config.class);
			} catch (Exception e) {
				PullMyPearlClient.LOG.warn("Could not read config, using defaults", e);
			}
			if (instance == null) instance = new Config();
			if (instance.microphone == null) instance.microphone = "";
			if (instance.phrase == null || instance.phrase.isBlank()) instance.phrase = DEFAULT_PHRASE;
			instance.save();
		}
		return instance;
	}

	public void save() {
		try { Files.writeString(FILE, GSON.toJson(this)); }
		catch (Exception e) { PullMyPearlClient.LOG.warn("Could not save config", e); }
	}
}
