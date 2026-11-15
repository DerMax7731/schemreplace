package com.schemreplace;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStatePalette;
import fi.dy.masa.litematica.schematic.container.LitematicaBitArray;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Replacer {
	private static final int UNDO_LIMIT = 10;
	private static final Deque<Undo> UNDO = new ArrayDeque<>();

	record RegionUndo(String region, LongArrayList index, IntArrayList old) {
	}

	record Undo(LitematicaSchematic schematic, List<RegionUndo> regions, String label) {
	}

	public record Result(Map<String, Long> changed, Map<String, Long> skipped, boolean saved, String file) {
		public long total() {
			return changed.values().stream().mapToLong(Long::longValue).sum();
		}
	}

	private Replacer() {
	}

	public static SchematicPlacement placement() {
		SchematicPlacementManager mgr = DataManager.getSchematicPlacementManager();
		SchematicPlacement sel = mgr.getSelectedSchematicPlacement();
		if (sel != null) return sel;
		List<SchematicPlacement> all = mgr.getAllSchematicsPlacements();
		return all.size() == 1 ? all.get(0) : null;
	}

	public static Identifier id(Block b) {
		return BuiltInRegistries.BLOCK.getKey(b);
	}

	public static Block find(String name) {
		String n = name.toLowerCase().trim();
		Identifier exact = Identifier.tryParse(n.contains(":") ? n : "minecraft:" + n);
		if (exact != null && BuiltInRegistries.BLOCK.containsKey(exact)) return BuiltInRegistries.BLOCK.getValue(exact);
		for (Identifier key : BuiltInRegistries.BLOCK.keySet()) {
			if (key.getPath().equals(n)) return BuiltInRegistries.BLOCK.getValue(key);
		}
		return null;
	}

	public static Map<Block, Long> count(LitematicaSchematic s) {
		Map<Block, Long> out = new HashMap<>();
		for (String region : s.getAreas().keySet()) {
			LitematicaBlockStateContainer c = s.getSubRegionContainer(region);
			if (c == null) continue;
			ILitematicaBlockStatePalette palette = c.getPalette();
			int size = palette.getPaletteSize();
			long[] perId = new long[size];
			LitematicaBitArray arr = c.getArray();
			long n = arr.size();
			for (long i = 0; i < n; i++) {
				int v = arr.getAt(i);
				if (v < size) perId[v]++;
			}
			for (int i = 0; i < size; i++) {
				if (perId[i] == 0) continue;
				BlockState st = palette.getBlockState(i);
				if (st == null || st.isAir()) continue;
				out.merge(st.getBlock(), perId[i], Long::sum);
			}
		}
		return out;
	}

	public static List<Block> blocksIn(LitematicaSchematic s) {
		List<Block> out = new ArrayList<>();
		for (String region : s.getAreas().keySet()) {
			LitematicaBlockStateContainer c = s.getSubRegionContainer(region);
			if (c == null) continue;
			for (BlockState st : c.getPalette().fromMapping()) {
				if (st != null && !st.isAir() && !out.contains(st.getBlock())) out.add(st.getBlock());
			}
		}
		return out;
	}

	public static Result replace(SchematicPlacement placement, Block from, Block to, boolean family) {
		LitematicaSchematic s = placement.getSchematic();
		Identifier fromId = id(from);
		Identifier toId = id(to);
		Map<String, Long> changed = new LinkedHashMap<>();
		Map<String, Long> skipped = new LinkedHashMap<>();
		List<RegionUndo> undo = new ArrayList<>();

		for (String region : s.getAreas().keySet()) {
			LitematicaBlockStateContainer c = s.getSubRegionContainer(region);
			if (c == null) continue;
			ILitematicaBlockStatePalette palette = c.getPalette();
			List<BlockState> states = new ArrayList<>(palette.fromMapping());
			int size = states.size();
			int[] remap = new int[size];
			String[] label = new String[size];
			String[] skipLabel = new String[size];
			boolean any = false;
			for (int i = 0; i < size; i++) {
				remap[i] = i;
				BlockState st = states.get(i);
				if (st == null) continue;
				Identifier key = id(st.getBlock());
				if (!key.getNamespace().equals(fromId.getNamespace())) continue;
				String target = Families.map(key.getPath(), fromId.getPath(), toId.getPath(), family,
					p -> BuiltInRegistries.BLOCK.containsKey(Identifier.fromNamespaceAndPath(toId.getNamespace(), p)));
				if (target == null) continue;
				if (target.isEmpty()) {
					skipLabel[i] = key.getPath();
					continue;
				}
				Block tb = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(toId.getNamespace(), target));
				BlockState ns = copyProps(st, tb);
				if (ns == st) continue;
				remap[i] = c.getPalette().idFor(ns);
				label[i] = key.getPath() + " → " + target;
				any = true;
			}

			LitematicaBitArray arr = c.getArray();
			long n = arr.size();
			long[] perId = new long[size];
			LongArrayList idx = any ? new LongArrayList() : null;
			IntArrayList old = any ? new IntArrayList() : null;
			for (long i = 0; i < n; i++) {
				int v = arr.getAt(i);
				if (v >= size) continue;
				perId[v]++;
				if (any && remap[v] != v) {
					idx.add(i);
					old.add(v);
					arr.setAt(i, remap[v]);
				}
			}
			for (int i = 0; i < size; i++) {
				if (perId[i] == 0) continue;
				if (label[i] != null) changed.merge(label[i], perId[i], Long::sum);
				if (skipLabel[i] != null) skipped.merge(skipLabel[i], perId[i], Long::sum);
			}
			if (any && !idx.isEmpty()) undo.add(new RegionUndo(region, idx, old));
		}

		if (undo.isEmpty()) return new Result(changed, skipped, false, fileName(s));
		UNDO.push(new Undo(s, undo, fromId.getPath() + " → " + toId.getPath()));
		while (UNDO.size() > UNDO_LIMIT) UNDO.removeLast();
		return new Result(changed, skipped, finish(s), fileName(s));
	}

	public static String undo() {
		Undo u = UNDO.poll();
		if (u == null) return null;
		for (RegionUndo r : u.regions()) {
			LitematicaBlockStateContainer c = u.schematic().getSubRegionContainer(r.region());
			if (c == null) continue;
			LitematicaBitArray arr = c.getArray();
			for (int i = 0; i < r.index().size(); i++) arr.setAt(r.index().getLong(i), r.old().getInt(i));
		}
		finish(u.schematic());
		return u.label();
	}

	private static String fileName(LitematicaSchematic s) {
		Path f = s.getFile();
		return f == null ? s.getMetadata().getName() : f.getFileName().toString();
	}

	private static boolean finish(LitematicaSchematic s) {
		SchematicPlacementManager mgr = DataManager.getSchematicPlacementManager();
		mgr.markAllPlacementsOfSchematicForRebuild(s);
		for (SchematicPlacement p : mgr.getAllPlacementsOfSchematic(s)) {
			try {
				MaterialListBase list = p.getMaterialList();
				if (list != null) list.reCreateMaterialList();
			} catch (RuntimeException ignored) {
			}
		}
		try {
			MaterialListBase open = DataManager.getMaterialList();
			if (open != null) open.reCreateMaterialList();
		} catch (RuntimeException ignored) {
		}
		return save(s);
	}

	private static boolean save(LitematicaSchematic s) {
		Path file = s.getFile();
		if (file == null || file.getParent() == null) return false;
		try {
			Path backup = file.resolveSibling(file.getFileName() + ".bak");
			if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
		} catch (IOException e) {
			return false;
		}
		return s.writeToFile(file.getParent(), file.getFileName().toString(), true);
	}

	static BlockState copyProps(BlockState from, Block to) {
		if (from.getBlock() == to) return from;
		BlockState out = to.defaultBlockState();
		for (Property<?> p : from.getProperties()) {
			Property<?> tp = to.getStateDefinition().getProperty(p.getName());
			if (tp != null) out = set(out, tp, valueName(from, p));
		}
		return out;
	}

	private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) {
		return p.getName(s.getValue(p));
	}

	private static <T extends Comparable<T>> BlockState set(BlockState s, Property<T> p, String v) {
		return p.getValue(v).map(x -> s.setValue(p, x)).orElse(s);
	}
}
