package com.schemreplace;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.litematica.gui.GuiSchematicPlacementsList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public final class MenuHook {
	static final int BTN_W = 96;
	static final int BTN_H = 20;
	static boolean openLater;
	private static KeyMapping key;

	private MenuHook() {
	}

	static void init() {
		KeyMapping.Category category = new KeyMapping.Category(Identifier.fromNamespaceAndPath("schemreplace", "main"));
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.schemreplace.open", InputConstants.KEY_PERIOD, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				if (mc.gui.screen() == null) open(mc, null);
			}
			if (openLater) {
				openLater = false;
				open(mc, null);
			}
		});

		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof GuiMainMenu) && !(screen instanceof GuiSchematicPlacementsList)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
				int x = x(s);
				int y = y(s);
				boolean hover = mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
				g.fill(x, y, x + BTN_W, y + BTN_H, hover ? 0xFF3A6EA5 : 0xFF2A2A2A);
				g.fill(x, y, x + BTN_W, y + 1, 0xFF8FB8E8);
				g.fill(x, y + BTN_H - 1, x + BTN_W, y + BTN_H, 0xFF101010);
				g.centeredText(mc.font, "Swap Blocks", x + BTN_W / 2, y + 6, 0xFFFFFFFF);
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, ev) -> {
				int x = x(s);
				int y = y(s);
				if (ev.x() >= x && ev.x() < x + BTN_W && ev.y() >= y && ev.y() < y + BTN_H) {
					open(mc, s);
					return false;
				}
				return true;
			});
		});
	}

	private static int x(Screen s) {
		return s.width - BTN_W - 6;
	}

	private static int y(Screen s) {
		return s.height - BTN_H - 6;
	}

	private static void open(Minecraft mc, Screen parent) {
		mc.gui.setScreen(new SwapScreen(parent));
	}
}
