package com.schemreplace;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SwapScreen extends Screen {
	private static final int ROW_H = 20;
	private static final int CELL = 20;
	private static final int TOP = 40;
	private static boolean family = true;

	private record Row(Block block, ItemStack icon, String name, String path, long count) {
	}

	private record Cell(Block block, ItemStack icon, String name, String path) {
	}

	private final Screen parent;
	private SchematicPlacement placement;
	private final List<Row> allRows = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private static List<Cell> allCells;
	private final List<Cell> cells = new ArrayList<>();
	private EditBox leftSearch;
	private EditBox rightSearch;
	private Button swapBtn;
	private Button familyBtn;
	private Block from;
	private Block to;
	private int leftScroll;
	private int rightScroll;
	private String status = "";
	private int statusColor = 0xFFAAAAAA;

	public SwapScreen(Screen parent) {
		super(Component.literal("Swap Blocks"));
		this.parent = parent;
	}

	private int leftX() {
		return 10;
	}

	private int leftW() {
		return this.width / 2 - 15;
	}

	private int rightX() {
		return this.width / 2 + 5;
	}

	private int rightW() {
		return this.width / 2 - 15;
	}

	private int listBottom() {
		return this.height - 58;
	}

	private int cols() {
		return Math.max(1, rightW() / CELL);
	}

	@Override
	protected void init() {
		placement = Replacer.placement();
		leftSearch = new EditBox(this.font, leftX(), 20, leftW(), 16, Component.literal("Search schematic"));
		leftSearch.setHint(Component.literal("Search blocks in schematic…"));
		leftSearch.setResponder(s -> {
			leftScroll = 0;
			filterLeft();
		});
		addRenderableWidget(leftSearch);

		rightSearch = new EditBox(this.font, rightX(), 20, rightW() - 64, 16, Component.literal("Search all blocks"));
		rightSearch.setHint(Component.literal("Search block to use…"));
		rightSearch.setResponder(s -> {
			rightScroll = 0;
			filterRight();
		});
		addRenderableWidget(rightSearch);
		addRenderableWidget(Button.builder(Component.literal("Held block"), b -> useHeld())
			.bounds(rightX() + rightW() - 60, 18, 60, 20).build());

		int by = this.height - 26;
		int cx = this.width / 2;
		familyBtn = addRenderableWidget(Button.builder(familyLabel(), b -> {
			family = !family;
			b.setMessage(familyLabel());
		}).bounds(cx - 210, by, 150, 20).build());
		swapBtn = addRenderableWidget(Button.builder(Component.literal("Swap"), b -> doSwap())
			.bounds(cx - 56, by, 90, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Undo"), b -> doUndo())
			.bounds(cx + 38, by, 70, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(cx + 112, by, 98, 20).build());

		if (allCells == null) buildCells();
		reloadRows();
		filterRight();
		setInitialFocus(rightSearch);
		updateSwap();
	}

	private Component familyLabel() {
		return Component.literal("Stairs/slabs/walls too: " + (family ? "§aON" : "§cOFF"));
	}

	private static void buildCells() {
		allCells = new ArrayList<>();
		for (Block b : BuiltInRegistries.BLOCK) {
			if (b.asItem() == Items.AIR) continue;
			ItemStack icon = new ItemStack(b.asItem());
			allCells.add(new Cell(b, icon, b.getName().getString(), Replacer.id(b).getPath()));
		}
	}

	private void reloadRows() {
		allRows.clear();
		if (placement == null || placement.getSchematic() == null) {
			filterLeft();
			return;
		}
		List<Map.Entry<Block, Long>> counts = new ArrayList<>(Replacer.count(placement.getSchematic()).entrySet());
		counts.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		for (Map.Entry<Block, Long> e : counts) {
			Block b = e.getKey();
			ItemStack icon = b.asItem() == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(b.asItem());
			allRows.add(new Row(b, icon, b.getName().getString(), Replacer.id(b).getPath(), e.getValue()));
		}
		filterLeft();
	}

	private static boolean matches(String q, String name, String path) {
		return q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || path.replace('_', ' ').contains(q) || path.contains(q);
	}

	private void filterLeft() {
		rows.clear();
		String q = leftSearch == null ? "" : leftSearch.getValue().trim().toLowerCase(Locale.ROOT);
		for (Row r : allRows) if (matches(q, r.name(), r.path())) rows.add(r);
	}

	private void filterRight() {
		cells.clear();
		String q = rightSearch == null ? "" : rightSearch.getValue().trim().toLowerCase(Locale.ROOT);
		for (Cell c : allCells) if (matches(q, c.name(), c.path())) cells.add(c);
	}

	private void useHeld() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Block b = Block.byItem(mc.player.getMainHandItem().getItem());
		if (b == Blocks.AIR) {
			setStatus("You are not holding a block.", 0xFFFF7777);
			return;
		}
		to = b;
		updateSwap();
	}

	private void setStatus(String s, int color) {
		status = s;
		statusColor = color;
	}

	private void updateSwap() {
		if (swapBtn != null) swapBtn.active = placement != null && from != null && to != null && from != to;
	}

	private void doSwap() {
		if (placement == null || from == null || to == null || from == to) return;
		Replacer.Result r = Replacer.replace(placement, from, to, family);
		SchemReplace.report(r, from);
		if (r.changed().isEmpty()) {
			setStatus("Nothing to swap.", 0xFFFFAA55);
		} else {
			String extra = r.skipped().isEmpty() ? "" : "  (" + r.skipped().size() + " shape(s) kept, see chat)";
			setStatus(String.format("Swapped %,d blocks%s", r.total(), r.saved() ? ", saved." + extra : " (not saved)." + extra), 0xFF77FF77);
		}
		from = null;
		reloadRows();
		updateSwap();
	}

	private void doUndo() {
		String label = Replacer.undo();
		setStatus(label == null ? "Nothing to undo." : "Undone: " + label, label == null ? 0xFFAAAAAA : 0xFF77FF77);
		reloadRows();
		updateSwap();
	}

	private int leftVisible() {
		return Math.max(1, (listBottom() - TOP) / ROW_H);
	}

	private int rightVisibleRows() {
		return Math.max(1, (listBottom() - TOP) / CELL);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int d = -(int) Math.signum(sy);
		if (mx < this.width / 2.0) {
			int max = Math.max(0, rows.size() - leftVisible());
			leftScroll = Math.max(0, Math.min(max, leftScroll + d));
		} else {
			int totalRows = (cells.size() + cols() - 1) / cols();
			int max = Math.max(0, totalRows - rightVisibleRows());
			rightScroll = Math.max(0, Math.min(max, rightScroll + d));
		}
		return true;
	}

	private Row rowAt(double mx, double my) {
		if (mx < leftX() || mx >= leftX() + leftW() || my < TOP || my >= TOP + leftVisible() * ROW_H) return null;
		int i = (int) ((my - TOP) / ROW_H) + leftScroll;
		return i < rows.size() ? rows.get(i) : null;
	}

	private Cell cellAt(double mx, double my) {
		if (mx < rightX() || mx >= rightX() + cols() * CELL || my < TOP || my >= TOP + rightVisibleRows() * CELL) return null;
		int i = ((int) ((my - TOP) / CELL) + rightScroll) * cols() + (int) ((mx - rightX()) / CELL);
		return i < cells.size() ? cells.get(i) : null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
		if (super.mouseClicked(ev, dbl)) return true;
		Row r = rowAt(ev.x(), ev.y());
		if (r != null) {
			from = r.block();
			setStatus("", 0xFFAAAAAA);
			updateSwap();
			return true;
		}
		Cell c = cellAt(ev.x(), ev.y());
		if (c != null) {
			to = c.block();
			setStatus("", 0xFFAAAAAA);
			updateSwap();
			return true;
		}
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		String title = placement == null ? "Swap Blocks" : "Swap Blocks — " + placement.getName();
		g.centeredText(this.font, title, this.width / 2, 6, 0xFFFFFFFF);

		if (placement == null) {
			g.centeredText(this.font, "No schematic selected. Select a placement in Litematica first (M + P).", this.width / 2, TOP + 20, 0xFFFF7777);
			return;
		}

		int lx = leftX();
		int lw = leftW();
		g.fill(lx, TOP, lx + lw, listBottom(), 0x50000000);
		int vis = leftVisible();
		for (int i = 0; i < vis && i + leftScroll < rows.size(); i++) {
			Row r = rows.get(i + leftScroll);
			int y = TOP + i * ROW_H;
			boolean sel = r.block() == from;
			boolean hover = mx >= lx && mx < lx + lw && my >= y && my < y + ROW_H;
			if (sel || hover) g.fill(lx, y, lx + lw, y + ROW_H - 1, sel ? 0xA03A6EA5 : 0x40FFFFFF);
			g.item(r.icon(), lx + 2, y + 1);
			g.text(this.font, String.format("%,d×", r.count()), lx + 22, y + 6, 0xFFFFDD77);
			g.text(this.font, r.name(), lx + 22 + 52, y + 6, 0xFFFFFFFF);
		}
		if (rows.isEmpty()) g.centeredText(this.font, "No blocks", lx + lw / 2, TOP + 10, 0xFF888888);

		int rx = rightX();
		int cols = cols();
		g.fill(rx, TOP, rx + cols * CELL, listBottom(), 0x50000000);
		int rvis = rightVisibleRows();
		Cell hovered = null;
		for (int row = 0; row < rvis; row++) {
			for (int col = 0; col < cols; col++) {
				int i = (row + rightScroll) * cols + col;
				if (i >= cells.size()) break;
				Cell c = cells.get(i);
				int x = rx + col * CELL;
				int y = TOP + row * CELL;
				boolean sel = c.block() == to;
				boolean hover = mx >= x && mx < x + CELL && my >= y && my < y + CELL;
				if (hover) hovered = c;
				if (sel || hover) g.fill(x, y, x + CELL, y + CELL, sel ? 0xC03A6EA5 : 0x40FFFFFF);
				g.item(c.icon(), x + 2, y + 2);
			}
		}

		int py = listBottom() + 6;
		String fromName = from == null ? "§7(click a block on the left)" : "§b" + from.getName().getString();
		String toName = to == null ? "§7(click a block on the right)" : "§a" + to.getName().getString();
		g.centeredText(this.font, "Replace " + fromName + " §fwith " + toName, this.width / 2, py, 0xFFFFFFFF);

		String line = hovered != null ? hovered.name() : status;
		int color = hovered != null ? 0xFFFFFFFF : statusColor;
		Row hr = rowAt(mx, my);
		if (hr != null) {
			line = hr.name() + "  §8" + hr.path();
			color = 0xFFFFFFFF;
		}
		if (!line.isEmpty()) g.centeredText(this.font, line, this.width / 2, py + 12, color);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}
}
