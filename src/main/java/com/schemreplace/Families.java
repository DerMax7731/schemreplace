package com.schemreplace;

import java.util.List;
import java.util.function.Predicate;

public final class Families {
	static final List<String> SUFFIXES = List.of(
		"_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_door", "_trapdoor", "_button", "_pressure_plate");

	private Families() {
	}

	static String stem(String path) {
		if (path.endsWith("_planks")) return path.substring(0, path.length() - 7);
		if (path.endsWith("_block")) return path.substring(0, path.length() - 6);
		if (path.endsWith("_bricks")) return path.substring(0, path.length() - 1);
		if (path.endsWith("_tiles")) return path.substring(0, path.length() - 1);
		if (path.equals("bricks")) return "brick";
		return path;
	}

	public static String map(String path, String from, String to, boolean family, Predicate<String> exists) {
		if (path.equals(from)) return to;
		if (!family) return null;
		String fromStem = stem(from);
		String toStem = stem(to);
		for (String suffix : SUFFIXES) {
			if (path.equals(fromStem + suffix)) {
				String target = toStem + suffix;
				return exists.test(target) ? target : "";
			}
		}
		return null;
	}
}
