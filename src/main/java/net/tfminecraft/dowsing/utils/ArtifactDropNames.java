package net.tfminecraft.dowsing.utils;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;

public final class ArtifactDropNames {

	private ArtifactDropNames() {}

	public static boolean isMagicPath(String path) {
		return path != null && path.toLowerCase().startsWith("magic.");
	}

	public static String label(String path) {
		if (!isMagicPath(path)) {
			return null;
		}
		try {
			if (Bukkit.getPluginManager() == null || Bukkit.getPluginManager().getPlugin("Magic") == null) {
				return fallback(path);
			}
			return ArtifactDropNamesMagic.label(path);
		} catch (Throwable ignored) {
			return fallback(path);
		}
	}

	private static String fallback(String path) {
		String rest = path.substring("magic.".length()).trim();
		if (rest.isEmpty() || rest.equalsIgnoreCase("artifact")) {
			return "Random Artifact";
		}
		if (!rest.startsWith("(") || !rest.endsWith(")") || rest.length() < 2) {
			return "Random Artifact";
		}
		String rarityId = null;
		String elementId = null;
		for (String token : rest.substring(1, rest.length() - 1).split(";")) {
			String part = token.trim();
			int eq = part.indexOf('=');
			String key = (eq < 0 ? part : part.substring(0, eq)).trim();
			String value = eq < 0 ? null : part.substring(eq + 1).trim();
			if (key.equalsIgnoreCase("rarity") && value != null && !value.isBlank()) {
				rarityId = value;
			} else if ((key.equalsIgnoreCase("primary") || key.equalsIgnoreCase("element"))
					&& value != null && !value.isBlank()) {
				elementId = value;
			} else if (eq < 0 && !key.isEmpty() && !key.equalsIgnoreCase("rarity")) {
				elementId = key;
			}
		}
		String rarityName = rarityId == null ? null : WordUtils.capitalize(rarityId.replace('_', ' '));
		String elementName = elementId == null ? null : WordUtils.capitalize(elementId.replace('_', ' '));
		if (rarityName != null && elementName != null) {
			return rarityName + " " + elementName + " Artifact";
		}
		if (rarityName != null) {
			return "Random " + rarityName + " Artifact";
		}
		if (elementName != null) {
			return "Random " + elementName + " Artifact";
		}
		return "Random Artifact";
	}
}
