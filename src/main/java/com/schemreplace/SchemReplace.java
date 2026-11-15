package com.schemreplace;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class SchemReplace implements ClientModInitializer {
	private static final String PREFIX = "§6[SchemReplace] §r";

	private static final SuggestionProvider<FabricClientCommandSource> IN_SCHEMATIC = (c, b) -> {
		SchematicPlacement p = Replacer.placement();
		List<String> names = new ArrayList<>();
		if (p != null && p.getSchematic() != null) {
			for (Block block : Replacer.blocksIn(p.getSchematic())) names.add(Replacer.id(block).getPath());
		}
		return SharedSuggestionProvider.suggest(names, b);
	};

	private static final SuggestionProvider<FabricClientCommandSource> ALL_BLOCKS = (c, b) -> SharedSuggestionProvider.suggest(
		Stream.concat(Stream.of("hand"), BuiltInRegistries.BLOCK.keySet().stream().map(Identifier::getPath)), b);

	@Override
	public void onInitializeClient() {
		MenuHook.init();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(build("schemreplace"));
			dispatcher.register(build("sr"));
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
		return literal(name)
			.executes(c -> help())
			.then(literal("help").executes(c -> help()))
			.then(literal("list").executes(c -> list()))
			.then(literal("undo").executes(c -> undo()))
			.then(literal("gui").executes(c -> {
				MenuHook.openLater = true;
				return 1;
			}))
			.then(literal("exact")
				.then(argument("from", StringArgumentType.word()).suggests(IN_SCHEMATIC)
					.then(argument("to", StringArgumentType.word()).suggests(ALL_BLOCKS)
						.executes(c -> replace(c, false)))))
			.then(argument("from", StringArgumentType.word()).suggests(IN_SCHEMATIC)
				.then(argument("to", StringArgumentType.word()).suggests(ALL_BLOCKS)
					.executes(c -> replace(c, true))));
	}

	static void say(Component c) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal(PREFIX).append(c));
	}

	static int say(String s) {
		say(Component.literal(s));
		return 1;
	}

	private static int help() {
		say("§eSwap blocks in the selected Litematica schematic:");
		say("§b/sr list §7– which blocks it uses and how many (click one)");
		say("§b/sr <from> <to> §7– swaps the block + matching stairs/slabs/walls");
		say("§b/sr <from> hand §7– swaps it for the block in your hand");
		say("§b/sr exact <from> <to> §7– only exactly this block");
		say("§b/sr undo §7– revert the last swap");
		say("§7The file is saved; the original stays next to it as .litematic.bak");
		return 1;
	}

	private static SchematicPlacement needPlacement() {
		SchematicPlacement p = Replacer.placement();
		if (p == null || p.getSchematic() == null) {
			say("§cNo schematic selected. Select a placement in Litematica (M + P).");
			return null;
		}
		return p;
	}

	private static int list() {
		SchematicPlacement p = needPlacement();
		if (p == null) return 0;
		List<Map.Entry<Block, Long>> rows = new ArrayList<>(Replacer.count(p.getSchematic()).entrySet());
		rows.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		say("§e" + p.getName() + " §7(" + rows.size() + " block types, click to swap)");
		int shown = 0;
		for (Map.Entry<Block, Long> e : rows) {
			if (shown++ >= 25) {
				say("§7… and " + (rows.size() - 25) + " more");
				break;
			}
			String path = Replacer.id(e.getKey()).getPath();
			MutableComponent line = Component.literal("§f" + String.format("%,d", e.getValue()) + "× ")
				.append(e.getKey().getName().copy().withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" §8" + path));
			line.withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/sr " + path + " "))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click: /sr " + path + " <to>"))));
			say(line);
		}
		return 1;
	}

	private static Block target(String name) {
		if (name.equalsIgnoreCase("hand")) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return null;
			ItemStack held = mc.player.getMainHandItem();
			Block b = Block.byItem(held.getItem());
			if (b == Blocks.AIR) {
				say("§cYou are not holding a block.");
				return null;
			}
			return b;
		}
		Block b = Replacer.find(name);
		if (b == null) say("§cBlock not found: " + name);
		return b;
	}

	private static int replace(CommandContext<FabricClientCommandSource> c, boolean family) {
		SchematicPlacement p = needPlacement();
		if (p == null) return 0;
		String fromName = StringArgumentType.getString(c, "from");
		Block from = Replacer.find(fromName);
		if (from == null) return say("§cBlock not found: " + fromName);
		Block to = target(StringArgumentType.getString(c, "to"));
		if (to == null) return 0;
		if (from == to) return say("§7That is already the same block.");

		return report(Replacer.replace(p, from, to, family), from) ? 1 : 0;
	}

	static boolean report(Replacer.Result r, Block from) {
		if (r.changed().isEmpty()) {
			say("§7No " + from.getName().getString() + " found in " + r.file() + ".");
			if (!r.skipped().isEmpty()) skipped(r);
			return false;
		}
		say("§a" + String.format("%,d", r.total()) + " blocks swapped in §f" + r.file() + "§a:");
		r.changed().forEach((k, v) -> say("§7  " + String.format("%,d", v) + "× " + k));
		if (!r.skipped().isEmpty()) skipped(r);
		say(r.saved() ? "§7Saved. Undo with §b/sr undo" : "§eChanged in game only, the file could not be saved.");
		return true;
	}

	private static void skipped(Replacer.Result r) {
		r.skipped().forEach((k, v) -> say("§6  " + String.format("%,d", v) + "× " + k + " §7kept (no matching variant, swap it with /sr exact)"));
	}

	private static int undo() {
		String label = Replacer.undo();
		return say(label == null ? "§7Nothing to undo." : "§aUndone: " + label);
	}
}
