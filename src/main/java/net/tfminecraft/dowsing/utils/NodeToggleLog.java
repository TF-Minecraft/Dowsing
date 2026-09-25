package net.tfminecraft.dowsing.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.simplefactions.managers.FactionManager;

/**
 * Upkeep is charged for the nodes active at the new day, so switching a node off just
 * before it and on again after skips a day's upkeep. Every manual toggle is recorded with
 * the time left until the new day, and one close to it is logged as a warning.
 */
public final class NodeToggleLog {
	public static final int WARN_SECONDS_BEFORE_NEW_DAY = 60 * 60;
	private static final String FILE_NAME = "node-toggles.log";
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private NodeToggleLog() {}

	public static void activated(Player player, Node node) {
		record(player, node, "activated");
	}

	public static void deactivated(Player player, Node node) {
		record(player, node, "deactivated");
	}

	private static void record(Player player, Node node, String action) {
		if (player == null || node == null) return;
		int secondsUntilNewDay = FactionManager.getSecondsUntilNewDay();
		String line = format(player.getName(), action, node, secondsUntilNewDay);
		Logger logger = DowsingMain.plugin.getLogger();
		if (secondsUntilNewDay <= WARN_SECONDS_BEFORE_NEW_DAY) {
			logger.warning(line);
		} else {
			logger.info(line);
		}
		append(LocalDateTime.now().format(TIMESTAMP) + " " + line);
	}

	static String format(String player, String action, Node node, int secondsUntilNewDay) {
		String guild = node.getGuild() == null ? "none" : node.getGuild().getId();
		String resource = node.getBlock() == null ? "unknown" : node.getBlock().getResource();
		return String.format(Locale.ROOT, "[NodeToggle] %s %s %s node %s of guild %s at %s, upkeep %.2fd/day, %s until new day",
				player, action, resource, node.getId(), guild, location(node.getLoc()),
				node.getDailyUpkeep(), duration(secondsUntilNewDay));
	}

	static String duration(int seconds) {
		int clamped = Math.max(0, seconds);
		return String.format(Locale.ROOT, "%dh %02dm", clamped / 3600, (clamped % 3600) / 60);
	}

	private static String location(Location loc) {
		if (loc == null) return "unknown";
		String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();
		return world + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
	}

	private static void append(String line) {
		File file = new File(DowsingMain.plugin.getDataFolder(), FILE_NAME);
		try {
			Files.writeString(file.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			DowsingMain.plugin.getLogger().log(Level.WARNING, "Could not write " + FILE_NAME, e);
		}
	}
}
