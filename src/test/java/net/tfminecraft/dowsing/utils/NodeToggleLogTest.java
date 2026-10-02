package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeBlock;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;

class NodeToggleLogTest {
	@TempDir Path directory;
	DowsingMain previousPlugin;
	Logger logger;
	Player player;
	Node node;

	@BeforeEach
	void setup() {
		previousPlugin = DowsingMain.plugin;
		DowsingMain.plugin = mock(DowsingMain.class);
		logger = mock(Logger.class);
		when(DowsingMain.plugin.getLogger()).thenReturn(logger);
		when(DowsingMain.plugin.getDataFolder()).thenReturn(directory.toFile());
		player = mock(Player.class); when(player.getName()).thenReturn("Leader");
		node = mock(Node.class); when(node.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
		when(node.getDailyUpkeep()).thenReturn(12.5);
	}

	@AfterEach
	void restore() { DowsingMain.plugin = previousPlugin; }

	@Test
	void durationShowsHoursAndMinutes() {
		assertEquals("0h 00m", NodeToggleLog.duration(0));
		assertEquals("0h 59m", NodeToggleLog.duration(3599));
		assertEquals("23h 30m", NodeToggleLog.duration(23 * 3600 + 30 * 60));
		assertEquals("0h 00m", NodeToggleLog.duration(-5));
	}

	@Test
	void recordsBothActionsToTheAuditFileAndWarnsAtTheUpkeepBoundary() throws Exception {
		Guild guild = mock(Guild.class); when(guild.getId()).thenReturn("iron-guild"); when(node.getGuild()).thenReturn(guild);
		NodeBlock block = mock(NodeBlock.class); when(block.getResource()).thenReturn("Iron"); when(node.getBlock()).thenReturn(block);
		World world = mock(World.class); when(world.getName()).thenReturn("mines");
		when(node.getLoc()).thenReturn(new Location(world, -1.5, 64.9, 3.1));
		try (var factions = mockStatic(FactionManager.class)) {
			factions.when(FactionManager::getSecondsUntilNewDay).thenReturn(3601, 3600);
			NodeToggleLog.activated(player, node); NodeToggleLog.deactivated(player, node);
		}
		String activated = "[NodeToggle] Leader activated Iron node 00000000-0000-0000-0000-000000000001 of guild iron-guild at mines -2,64,3, upkeep 12.50d/day, 1h 00m until new day";
		String deactivated = activated.replace("activated Iron", "deactivated Iron");
		verify(logger).info(activated); verify(logger).warning(deactivated);
		List<String> lines = Files.readAllLines(directory.resolve("node-toggles.log"));
		assertEquals(2, lines.size());
		assertTrue(lines.get(0).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} " + java.util.regex.Pattern.quote(activated)));
		assertTrue(lines.get(1).endsWith(deactivated));
	}

	@Test
	void formattingHandlesUnknownOwnershipAndLocationsAndUsesStableDecimalSeparators() {
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.GERMANY);
			String unknown = NodeToggleLog.format("Leader", "activated", node, -1);
			assertTrue(unknown.contains("unknown node")); assertTrue(unknown.contains("of guild none at unknown"));
			assertTrue(unknown.endsWith("upkeep 12.50d/day, 0h 00m until new day"));
			when(node.getLoc()).thenReturn(new Location(null, 1, 2, 3));
			assertTrue(NodeToggleLog.format("Leader", "activated", node, 60).contains("at ? 1,2,3"));
		} finally { Locale.setDefault(previous); }
	}

	@Test
	void nullActorsAndNodesDoNotWriteAuditRecords() {
		NodeToggleLog.activated(null, node); NodeToggleLog.deactivated(player, null);
		verifyNoInteractions(logger); assertFalse(Files.exists(directory.resolve("node-toggles.log")));
	}

	@Test
	void auditWriteFailuresAreLoggedWithoutPreventingTheAction() throws Exception {
		Files.createDirectory(directory.resolve("node-toggles.log"));
		try (var factions = mockStatic(FactionManager.class)) {
			factions.when(FactionManager::getSecondsUntilNewDay).thenReturn(7200);
			assertDoesNotThrow(() -> NodeToggleLog.activated(player, node));
		}
		verify(logger).info(contains("Leader activated"));
		verify(logger).log(eq(Level.WARNING), eq("Could not write node-toggles.log"), isA(IOException.class));
	}
}
