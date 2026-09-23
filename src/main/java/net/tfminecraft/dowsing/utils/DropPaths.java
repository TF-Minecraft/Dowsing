package net.tfminecraft.dowsing.utils;

public final class DropPaths {

	private DropPaths() {}

	public static String formatStored(String path, double weight) {
		return path + "(" + formatWeight(weight) + ")";
	}

	public static String[] parseAddDropEffect(String effect) {
		if (effect == null) {
			return null;
		}
		String trimmed = effect.trim();
		int open = trimmed.indexOf('(');
		if (open < 0 || !trimmed.endsWith(")")) {
			return null;
		}
		String inner = trimmed.substring(open + 1, trimmed.length() - 1);
		int comma = inner.lastIndexOf(',');
		if (comma < 0) {
			return null;
		}
		String path = inner.substring(0, comma).trim();
		String weight = inner.substring(comma + 1).trim();
		if (path.isEmpty() || weight.isEmpty()) {
			return null;
		}
		return new String[] { path, weight };
	}

	public static String[] parseStored(String token) {
		if (token == null) {
			return null;
		}
		String trimmed = token.trim();
		int open = trimmed.lastIndexOf('(');
		if (open < 0 || !trimmed.endsWith(")")) {
			return null;
		}
		String path = trimmed.substring(0, open).trim();
		String weight = trimmed.substring(open + 1, trimmed.length() - 1).trim();
		if (path.isEmpty() || weight.isEmpty()) {
			return null;
		}
		return new String[] { path, weight };
	}

	static String formatWeight(double weight) {
		if (weight == (long) weight) {
			return String.valueOf((long) weight);
		}
		return String.valueOf(weight);
	}
}
