package net.tfminecraft.dowsing.objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.managers.NodeManager;
import net.tfminecraft.dowsing.utils.Database;
import net.tfminecraft.dowsing.utils.ItemCreator;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;

class NodeBehaviorTest {
    Fixture f;
    @BeforeEach void setUp() throws Exception { f = new Fixture(); }
    @AfterEach void tearDown() throws Exception { f.close(); }

    @Test
    void persistedStateAndAccessorsRetainTheRequestedValues() {
        UUID id = UUID.randomUUID();
        Node node = new Node(id, f.block, f.location, null, true, 2, 7, f.type, 15, 3, 25);
        assertEquals(id, node.getId()); assertSame(f.block, node.getBlock());
        assertSame(f.location, node.getLoc()); assertSame(f.type, node.getCurrentType());
        assertNull(node.getGuild()); assertTrue(node.getIsActive()); assertEquals(2, node.getLevel());
        assertEquals(7, node.getCycleTime()); assertEquals(15, node.getTimeLeft());
        assertEquals(15, node.getModifiedTime()); assertEquals(3, node.getInputCounter());
        assertEquals(25, node.getEfficiency()); assertEquals(0, node.getNaturalYield());
        assertEquals(0, node.getExtraction()); assertEquals(0.0, node.getUpkeep());
        assertEquals(0.0, node.getWealthModifier()); assertEquals(0.0, node.getPrestigeGain());
        assertEquals(0.0, node.getTimeModifier()); assertEquals(1, node.getMultiplier());
        assertEquals(1.0, node.getCostIncrease()); assertTrue(node.getAddedDrops().isEmpty());
        assertTrue(node.getErrors().isEmpty()); assertTrue(node.getLastResult().isEmpty());
        UUID replacement = UUID.randomUUID();
        Location moved = new Location(f.world, 8, 65, 9);
        NodeBlock replacementBlock = mock(NodeBlock.class); NodeType replacementType = mock(NodeType.class);
        node.setId(replacement); node.setBlock(replacementBlock); node.setLoc(moved);
        node.setCurrentType(replacementType); node.setGuild(f.guild); node.setIsActive(false);
        node.setLevel(3); node.setCycleTime(4); node.setTimeLeft(6); node.setModifiedTime(10);
        node.setInputCounter(2); node.setNaturalYield(5); node.setExtraction(4); node.setUpkeep(1.25);
        node.setWealthModifier(2.5); node.setPrestigeGain(3.5); node.setTimeModifier(-20.0);
        node.setMultiplier(3); node.setCostIncrease(1.5); node.setAddedDrops(new ArrayList<>(List.of("v.STONE(1)")));
        node.setErrors(new ArrayList<>(List.of("broken"))); node.setLastResult(new HashMap<>(Map.of("v.STONE", 2)));
        assertEquals(replacement, node.getId()); assertSame(replacementBlock, node.getBlock());
        assertSame(moved, node.getLoc()); assertSame(replacementType, node.getCurrentType());
        assertSame(f.guild, node.getGuild()); assertFalse(node.getIsActive()); assertEquals(3, node.getLevel());
        assertEquals(4, node.getCycleTime()); assertEquals(6, node.getTimeLeft());
        assertEquals(10, node.getModifiedTime()); assertEquals(2, node.getInputCounter());
        assertEquals(5, node.getNaturalYield()); assertEquals(4, node.getExtraction());
        assertEquals(1.25, node.getUpkeep()); assertEquals(2.5, node.getWealthModifier());
        assertEquals(3.5, node.getPrestigeGain()); assertEquals(-20.0, node.getTimeModifier());
        assertEquals(3, node.getMultiplier()); assertEquals(1.5, node.getCostIncrease());
        assertEquals(List.of("v.STONE(1)"), node.getAddedDrops());
        assertEquals(List.of("broken"), node.getErrors()); assertEquals(Map.of("v.STONE", 2), node.getLastResult());
    }

    @Test
    void yieldSupportsPercentBonusesMissingValuesAndNegativeTotals() {
        Node node = f.node();
        node.setYield(7); node.runEffect("yield_percent(50)");
        assertEquals(7, node.getBaseYield()); assertEquals(50.0, node.getYieldPercent());
        assertEquals(11, node.getYield());
        node.runEffect("yield_percent(-200)"); assertEquals(0, node.getYield());
        node.setYield(null); node.yieldPercent = null;
        assertEquals(0, node.getYield()); assertEquals(0, node.getBaseYield()); assertEquals(0.0, node.getYieldPercent());
    }

    @Test
    void guildResolutionRefreshesTheLiveOwnerAndDropsDeletedGuilds() {
        Node node = f.node();
        assertTrue(node.isClaimable()); assertFalse(node.hasGuild());
        Guild stale = mock(Guild.class); when(stale.getId()).thenReturn("guild");
        node.setGuild(stale);
        assertSame(f.guild, node.getGuild()); assertFalse(node.isClaimable()); assertTrue(node.hasGuild());
        f.factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(null);
        assertNull(node.getGuild()); assertTrue(node.isClaimable()); assertNull(node.guild);
    }

    @Test
    void claimAndRetentionEnforceMembershipAndCapacityWithAdminAndSpecialExceptions() {
        Node node = f.node(); Player player = mock(Player.class);
        Cache.minMembersForNode = 3;
        assertFalse(node.canHold(null)); assertFalse(node.canHold(f.guild));
        assertFalse(node.canClaim(player, f.guild));
        verify(player).sendMessage(contains("at least 3 members"));
        Cache.minMembersForNode = 1;
        assertTrue(node.canHold(f.guild)); assertTrue(node.canClaim(player, f.guild));
        Node owned = f.owned(); NodeManager.nodes.add(owned);
        assertFalse(node.canClaim(player, f.guild));
        verify(player).sendMessage(contains("filled its node capacity"));
        when(f.block.isSpecial()).thenReturn(true);
        assertTrue(node.canClaim(player, f.guild));
        when(f.block.isSpecial()).thenReturn(false);
        NodeManager.nodes.add(f.owned()); assertFalse(node.canHold(f.guild));
        node.setGuild(f.guild); assertFalse(node.canClaim(player, f.guild));
        when(player.hasPermission("simplefactions.admin")).thenReturn(true);
        assertTrue(node.canClaim(player, f.guild));
        var leader = f.server.addPlayer("Leader"); leader.setOp(true);
        assertTrue(node.canHold(f.guild));
    }

    @Test
    void invalidOwnershipNotifiesTheLeaderAndReleasesTheNode() {
        var leader = f.server.addPlayer("Leader");
        Node node = f.owned();
        Cache.minMembersForNode = 3;
        node.check();
        assertTrue(node.isClaimable());
        assertEquals("§cYou lost control of the Iron Node !", leader.nextMessage());
        node.check();
        Cache.minMembersForNode = 1;
        node.setGuild(f.guild); node.check(); assertTrue(node.hasGuild());
    }

    @Test
    void deletedOwnerDuringResolutionLeavesTheNodeClaimable() {
        Node node = f.owned();
        f.factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(f.guild).thenReturn(null);
        node.check(); assertTrue(node.isClaimable());
    }

    @Test
    void ticksAndEfficiencyOnlyAdvanceOwnedNodesAndRespectTheirLimits() {
        Node node = f.node();
        node.tick(); node.tickCycle(); node.growEfficiency();
        assertEquals(60, node.getTimeLeft()); assertEquals(0, node.getCycleTime());
        assertEquals(50, node.getEfficiency()); assertEquals(0, node.getMaxEfficiency());
        node.setGuild(f.guild); node.tick(); node.tickCycle();
        assertEquals(59, node.getTimeLeft()); assertEquals(1, node.getCycleTime());
        assertEquals(50, node.getMaxEfficiency());
        node.updateEfficiency(-100); assertEquals(0, node.getEfficiency());
        node.growEfficiency(); assertEquals(0.2, node.getEfficiency());
        node.updateEfficiency(100); assertEquals(50, node.getEfficiency());
        when(f.guild.getMembers()).thenReturn(java.util.Collections.nCopies(20, "member"));
        assertEquals(100, node.getMaxEfficiency()); node.growEfficiency(); assertEquals(51, node.getEfficiency());
        node.efficiency = 0; node.setModifiedTime(10); node.updateEfficiencyTime(); assertEquals(40, node.getModifiedTime());
        node.efficiency = 100; node.setModifiedTime(10); node.updateEfficiencyTime(); assertEquals(10, node.getModifiedTime());
    }

    @Test
    void upkeepRoundsHalfUpAndRecalculatesFromLiveGuildCapacity() {
        Node node = f.node(); node.setUpkeep(null); node.setCostIncrease(null);
        assertEquals(0, node.getDailyUpkeep()); assertEquals(1.0, node.currentCostIncrease());
        node.setUpkeep(0.29); node.setCostIncrease(1.5); assertEquals(0.44, node.getDailyUpkeep());
        node.setGuild(f.guild); NodeManager.nodes.add(node); NodeManager.nodes.add(f.owned());
        assertEquals(1.5, node.currentCostIncrease()); assertEquals(0.44, node.getDailyUpkeep());
        Cache.extraCapacity = true; Cache.membersPerCapacity = 2; Cache.maxMemberCapacity = 5;
        assertEquals(2, node.getCapacity()); assertEquals(0.29, node.getDailyUpkeep());
        Cache.extraCapacityCost = 12.345;
        NodeManager.extraCapacityByGuild.put("guild", 2);
        assertEquals(37.04, node.getNodeCapacityCost());
        node.setGuild(null); assertEquals(1, node.getCapacity());
    }

    @Test
    void nodeCapacityUsesTheSameMemberCapAsClaimAndRetentionChecks() {
        Node node = f.owned();
        Cache.extraCapacity = true; Cache.membersPerCapacity = 1; Cache.maxMemberCapacity = 1;
        when(f.guild.getMembers()).thenReturn(java.util.Collections.nCopies(20, "member"));
        NodeManager.extraCapacityByGuild.put("guild", 2);
        assertEquals(4, NodeManager.getNodeCapacity(f.guild));
        assertEquals(4, node.getCapacity());
    }

    @Test
    void constructorsInitializeNewSpecialAndOwnedNodes() {
        Node newNode = new Node(f.location, null, f.block);
        assertNotNull(newNode.getId()); assertTrue(newNode.isClaimable()); assertFalse(newNode.getIsActive());
        assertEquals(1, newNode.getLevel()); assertEquals(50, newNode.getEfficiency());
        assertEquals(60, newNode.getTimeLeft()); assertEquals(0, newNode.getInputCounter());
        Node owned = new Node(f.location, f.guild, f.block);
        assertSame(f.guild, owned.getGuild()); assertEquals(150, owned.getModifiedTime());
        Node restored = new Node(UUID.randomUUID(), f.block, f.location, f.guild, true, 1, 2, f.type, 10, 3, 100);
        assertSame(f.guild, restored.getGuild()); assertEquals(60, restored.getModifiedTime());
        when(f.block.isSpecial()).thenReturn(true);
        assertNull(new Node(f.location, f.guild, f.block).getGuild());
    }

    @Test
    void naturalYieldLooksUpMatchingResourceAndHonorsItsFeatureToggle() throws Exception {
        Node node = f.node();
        try (var databases = mockConstruction(Database.class, (db, context) -> {
            when(db.hasResource(f.chunk)).thenReturn(true);
            when(db.getResource(f.chunk)).thenReturn("iron.8");
        })) {
            assertEquals(0, node.getNaturalYieldFromChunk("Iron", f.location));
            assertTrue(databases.constructed().isEmpty());
            Cache.naturalYieldEnabled = true;
            assertEquals(8, node.getNaturalYieldFromChunk("Iron", f.location));
            assertEquals(0, node.getNaturalYieldFromChunk("Gold", f.location));
            assertEquals(8, node.getNaturalYieldFromChunk(f.block, f.location));
            when(f.block.getResource()).thenReturn("Gold");
            assertEquals(0, node.getNaturalYieldFromChunk(f.block, f.location));
        }
        try (var databases = mockConstruction(Database.class)) {
            assertEquals(0, node.getNaturalYieldFromChunk("Iron", f.location));
            assertEquals(0, node.getNaturalYieldFromChunk(f.block, f.location));
        }
    }

    @Test
    void resourceIoFailureDoesNotPreventConstructionOrUpdating() throws Exception {
        Cache.naturalYieldEnabled = true;
        try (var databases = mockConstruction(Database.class, (db, context) -> {
            when(db.hasResource(any())).thenThrow(new IOException("resource unavailable"));
        })) {
            Node node = new Node(f.location, null, f.block);
            assertEquals(60, node.getTimeLeft());
            assertEquals(0, node.getNaturalYield());
            node.setGuild(f.guild); node.update();
            assertEquals(0, node.getNaturalYield()); assertEquals(150, node.getModifiedTime());
        }
    }

    @Test
    void effectsCombineYieldTimingInputsAndDropWeights() {
        Node node = f.node();
        node.runEffect("time_modifier(-25)"); node.runEffect("yield(5)"); node.runEffect("yield_percent(20)");
        node.runEffect("extraction(3)"); node.runEffect("upkeep(1.25)"); node.runEffect("prestige(2.5)");
        node.runEffect("add_drop(v.STONE,2.5)"); node.runEffect("add_drop(bad)"); node.runEffect("unknown(9)");
        assertEquals(-25.0, node.getTimeModifier()); assertEquals(6, node.getYield());
        assertEquals(3, node.getExtraction()); assertEquals(1.25, node.getUpkeep());
        assertEquals(2.5, node.getPrestigeGain()); assertEquals(0, node.getAddedPrestige("yield(1)"));
        assertEquals(List.of("v.STONE(2.5)"), node.getAddedDrops());
        when(f.type.getDrops()).thenReturn(List.of("v.STONE(1.5)", "v.COAL(5)", "bad"));
        node.setCompleteDrops(); assertEquals(Map.of("v.STONE", 4.0, "v.COAL", 5.0), node.getCompleteDrop());
        node.addCompleteDrop(null); node.addCompleteDrop("v.COAL(2)");
        assertEquals(7.0, node.getCompleteDrop().get("v.COAL"));
    }

    @Test
    void updateRebuildsEffectsExtractsNaturalYieldAndClampsRemainingTime() throws Exception {
        Node node = f.owned();
        node.setLevel(2); node.efficiency = 100; node.setTimeLeft(1000);
        ProductionMethod pm = mock(ProductionMethod.class); NodeSlot slot = mock(NodeSlot.class);
        when(slot.getActivePm()).thenReturn(pm);
        when(pm.getEffects()).thenReturn(List.of("yield(2)", "extraction(9)", "prestige(1.5)", "add_drop(v.STONE,3)"));
        when(f.type.getSlots()).thenReturn(List.of(slot));
        when(f.type.getDrops()).thenReturn(List.of("v.STONE(2)"));
        Level second = mock(Level.class); when(second.getLevel()).thenReturn(2); when(second.getCost()).thenReturn(20.0);
        when(second.getEffects()).thenReturn(List.of("upkeep(1.234)", "yield_percent(25)"));
        Level future = mock(Level.class); when(future.getLevel()).thenReturn(3); when(future.getCost()).thenReturn(40.0);
        when(f.type.getLevels()).thenReturn(List.of(f.level, second, future));
        Cache.naturalYieldEnabled = true;
        try (var databases = mockConstruction(Database.class, (db, context) -> {
            when(db.hasResource(f.chunk)).thenReturn(true); when(db.getResource(f.chunk)).thenReturn("Iron.5");
        })) {
            node.update();
            assertEquals(5, node.getNaturalYield()); assertEquals(5, node.getExtraction());
            assertEquals(4, node.getBaseYield()); assertEquals(5, node.getYield());
            assertEquals(-10.0, node.getTimeModifier()); assertEquals(54, node.getModifiedTime());
            assertEquals(54, node.getTimeLeft()); assertEquals(30.0, node.getWealthModifier());
            assertEquals(1.5, node.getPrestigeGain()); assertEquals(1.23, node.getUpkeep());
            assertEquals(Map.of("v.STONE", 5.0), node.getCompleteDrop());
            node.update(); assertEquals(5, node.getYield()); assertEquals(1.5, node.getPrestigeGain());
        }
        Cache.naturalYieldEnabled = false; node.setTimeLeft(1); node.update();
        assertEquals(0, node.getExtraction()); assertEquals(2, node.getBaseYield()); assertEquals(1, node.getTimeLeft());
        when(pm.getEffects()).thenReturn(List.of("time_modifier(-100)")); node.update();
        assertEquals(1, node.getModifiedTime());
        node.setGuild(null); node.setYield(99); node.update(); assertEquals(99, node.getBaseYield());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void configurationRejectsAnExtractionDivisorThatWouldPreventProgress(int divisor) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("natural-yield-per-yield: " + divisor + "\nitem: {}\nlevels: {}\n");
        try (var creators = mockConstruction(ItemCreator.class)) {
            assertThrows(IllegalArgumentException.class, () -> new NodeType("unsafe", config));
        }
    }

    static final class Fixture implements AutoCloseable {
        final Map<Field, Object> previousCache = new HashMap<>();
        final List<Node> previousNodes = NodeManager.nodes;
        final HashMap<String, Integer> previousCapacity = NodeManager.extraCapacityByGuild;
        final DowsingMain previousPlugin = DowsingMain.plugin;
        final ServerMock server;
        final MockedStatic<FactionManager> factions;
        final Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
        final World world = mock(World.class);
        final Chunk chunk = mock(Chunk.class);
        final Block worldBlock = mock(Block.class);
        final Location location = new Location(world, 2, 64, 3);
        final NodeBlock block = mock(NodeBlock.class);
        final NodeType type = mock(NodeType.class);
        final Level level = mock(Level.class);

        Fixture() throws Exception {
            for (Field field : Cache.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) previousCache.put(field, field.get(null));
            }
            server = MockBukkit.mock();
            DowsingMain.plugin = mock(DowsingMain.class);
            when(DowsingMain.plugin.getLogger()).thenReturn(Logger.getLogger("NodeTest"));
            when(DowsingMain.plugin.getServer()).thenReturn(server);
            NodeManager.nodes = new ArrayList<>(); NodeManager.extraCapacityByGuild = new HashMap<>();
            Cache.extraCapacity = false; Cache.naturalYieldEnabled = false; Cache.cycleLength = 60;
            Cache.membersPerCapacity = 2; Cache.maxMemberCapacity = 5; Cache.minMembersForNode = 1;
            Cache.efficiencyGrowthPerMember = 0.1; Cache.maxEfficiencyPerMember = 25;
            when(guild.getId()).thenReturn("guild"); when(guild.getLeader()).thenReturn("Leader");
            when(guild.getMembers()).thenReturn(List.of("Leader", "Member"));
            when(guild.getBank().getWealth()).thenReturn(1000.0);
            factions = mockStatic(FactionManager.class);
            factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
            factions.when(FactionManager::getCopy).thenReturn(new ArrayList<>());
            when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(worldBlock);
            when(world.getBlockAt(any(Location.class))).thenReturn(worldBlock);
            when(block.getResource()).thenReturn("Iron"); when(block.getBlock()).thenReturn("v.IRON_BLOCK");
            when(block.getTypes()).thenReturn(List.of(type));
            when(type.getResource()).thenReturn("Iron"); when(type.getTimer()).thenReturn(60);
            when(type.getYieldNaturalYield()).thenReturn(2); when(type.getTimeNaturalYield()).thenReturn(-2.0);
            when(type.getLevels()).thenReturn(List.of(level));
            when(level.getLevel()).thenReturn(1); when(level.getCost()).thenReturn(10.0);
        }

        Node node() { return new Node(UUID.randomUUID(), block, location, null, false, 1, 0, type, 60, 0, 50); }
        Node owned() { Node node = node(); node.setGuild(guild); return node; }

        @Override public void close() throws Exception {
            factions.close(); MockBukkit.unmock(); DowsingMain.plugin = previousPlugin;
            NodeManager.nodes = previousNodes; NodeManager.extraCapacityByGuild = previousCapacity;
            for (var entry : previousCache.entrySet()) entry.getKey().set(null, entry.getValue());
        }
    }
}
