package de.joah.pullmypearl;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Mod Menu config screen: pick which microphone to listen to. */
public class ConfigScreen extends Screen {
	private final Screen parent;
	private final List<String> options = new ArrayList<>(); // index 0 = system default
	private int index;
	private Button label;
	private EditBox phraseBox;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Pull My Pearl"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		options.clear();
		options.add("");
		options.addAll(PullMyPearlClient.microphones());
		String current = Config.get().microphone;
		index = Math.max(0, options.indexOf(current));
		if (!current.isEmpty() && !options.contains(current)) { options.add(current); index = options.size() - 1; }

		int cx = width / 2;
		int y = height / 2 - 40;
		addRenderableWidget(new StringWidget(cx - 150, y - 40, 300, 12, title, font));
		addRenderableWidget(new StringWidget(cx - 150, y - 20, 300, 12, Component.literal("Microphone"), font));

		addRenderableWidget(Button.builder(Component.literal("<"), b -> step(-1)).bounds(cx - 155, y, 20, 20).build());
		label = addRenderableWidget(Button.builder(text(), b -> step(1)).bounds(cx - 132, y, 264, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> step(1)).bounds(cx + 135, y, 20, 20).build());

		addRenderableWidget(new StringWidget(cx - 150, y + 35, 300, 12, Component.literal("Trigger sentence"), font));
		phraseBox = addRenderableWidget(new EditBox(font, cx - 150, y + 50, 300, 20, Component.literal("Trigger sentence")));
		phraseBox.setMaxLength(100);
		phraseBox.setValue(Config.get().phrase);

		addRenderableWidget(Button.builder(Component.literal("Reset sentence"), b -> phraseBox.setValue(Config.DEFAULT_PHRASE))
			.bounds(cx - 100, y + 76, 200, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(cx - 100, y + 106, 200, 20).build());
	}

	private Component text() {
		String name = options.get(index);
		return Component.literal(name.isEmpty() ? "System default" : name);
	}

	private void step(int d) {
		index = Math.floorMod(index + d, options.size());
		label.setMessage(text());
	}

	@Override
	public void onClose() {
		String chosen = options.get(index);
		String phrase = phraseBox.getValue().isBlank() ? Config.DEFAULT_PHRASE : phraseBox.getValue().trim();
		if (!chosen.equals(Config.get().microphone) || !phrase.equals(Config.get().phrase)) {
			Config.get().microphone = chosen;
			Config.get().phrase = phrase;
			Config.get().save();
			if (PullMyPearlClient.instance != null) PullMyPearlClient.instance.startListening();
		}
		minecraft.setScreenAndShow(parent);
	}
}
