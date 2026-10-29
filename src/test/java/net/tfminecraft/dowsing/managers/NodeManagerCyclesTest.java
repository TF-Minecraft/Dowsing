package net.tfminecraft.dowsing.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import dev.lone.itemsadder.api.CustomFurniture;
import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.loaders.BlockLoader;
import net.tfminecraft.dowsing.loaders.PMLoader;
import net.tfminecraft.dowsing.loaders.TypeLoader;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeBlock;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.dowsing.utils.ItemDropper;
import net.tfminecraft.dowsing.utils.NodeEngine;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.Modifier;

class NodeManagerCyclesTest {
    NodeManagerEventsTest.Fixture f;
    @BeforeEach void setUp() throws Exception { f = new NodeManagerEventsTest.Fixture(); }
    @AfterEach void tearDown() throws Exception { f.close(); }

    @Test
    void factionBenefitsAggregateOnlyActiveOwnedNodesAndClearStaleBenefits() {
        Faction faction = mock(Faction.class), empty = mock(Faction.class);
        Guild main = mock(Guild.class), emptyMain = mock(Guild.class);
        when(faction.getOrCreateMainGuild()).thenReturn(main); when(empty.getOrCreateMainGuild()).thenReturn(emptyMain);
        when(f.guild.getFaction()).thenReturn(faction);
        f.factions.when(FactionManager::getCopy).thenReturn(new ArrayList<>(List.of(faction, empty)));
        Node active = f.node, another = f.nodeAt(f.location), inactive = f.nodeAt(f.location), unowned = f.nodeAt(f.location);
        when(active.getIsActive()).thenReturn(true); when(active.getPrestigeGain()).thenReturn(2.0); when(active.getWealthModifier()).thenReturn(10.0);
        when(another.getIsActive()).thenReturn(true); when(another.getPrestigeGain()).thenReturn(3.5); when(another.getWealthModifier()).thenReturn(20.0);
        when(unowned.hasGuild()).thenReturn(false);
        Node stale = f.nodeAt(f.location); when(stale.getIsActive()).thenReturn(true); when(stale.getGuild()).thenReturn(null);
        Guild withoutFaction = mock(Guild.class); Node independent = f.nodeAt(f.location);
        when(independent.getGuild()).thenReturn(withoutFaction); when(independent.getIsActive()).thenReturn(true);
        NodeManager.nodes.addAll(List.of(active, another, inactive, unowned, stale, independent));
        NodeManager.requestNodeBenefitSync();
        verify(faction).setPersistentPrestigeModifier("Nodes", 5.5); verify(faction).updatePrestige();
        verify(empty).setPersistentPrestigeModifier("Nodes", 0.0); verify(empty).updatePrestige();
        ArgumentCaptor<Modifier> modifier = ArgumentCaptor.forClass(Modifier.class);
        verify(main).addWealthModifier(modifier.capture()); assertEquals("Nodes", modifier.getValue().getType());
        assertEquals(30.0, modifier.getValue().getAmount()); assertTrue(modifier.getValue().isPersistent()); verify(main).updateWealth();
        verify(emptyMain).addWealthModifier(modifier.capture()); assertEquals(0.0, modifier.getValue().getAmount()); verify(emptyMain).updateWealth();
    }

    @Test
    void syncDropsANodeValueLeftOnAGuildThatIsNoLongerItsRealmsOwn() {
        Guild formerRealm = mock(Guild.class), ordinary = mock(Guild.class), realm = mock(Guild.class);
        List<Modifier> stale = new ArrayList<>(List.of(new Modifier("Bank", 5.0, false), new Modifier("Nodes", 40.0, true)));
        when(formerRealm.getWealthModifiers()).thenReturn(stale);
        when(ordinary.getWealthModifiers()).thenReturn(new ArrayList<>(List.of(new Modifier("Bank", 1.0, false))));
        Faction owner = mock(Faction.class); when(owner.getOrCreateMainGuild()).thenReturn(realm);
        f.factions.when(FactionManager::getCopy).thenReturn(new ArrayList<>(List.of(owner)));
        // Only the guild a realm names as its own keeps a value, whatever its type says.
        when(formerRealm.isBase()).thenReturn(true);
        f.factions.when(FactionManager::getAllGuilds).thenReturn(new ArrayList<>(java.util.Arrays.asList(formerRealm, ordinary, realm, null)));
        NodeManager.syncFactionNodeBenefits();
        assertEquals(List.of("Bank"), stale.stream().map(Modifier::getType).toList());
        verify(formerRealm).updateWealth();
        verify(ordinary, never()).updateWealth();
        verify(realm, never()).getWealthModifiers(); verify(realm).updateWealth();
    }

    @Test
    void theMinuteCheckResyncsOnlyWhenANodesGuildOrRealmChanged() {
        Faction first = mock(Faction.class), second = mock(Faction.class);
        Guild firstMain = mock(Guild.class), secondMain = mock(Guild.class);
        when(first.getOrCreateMainGuild()).thenReturn(firstMain); when(second.getOrCreateMainGuild()).thenReturn(secondMain);
        f.factions.when(FactionManager::getCopy).thenReturn(new ArrayList<>(List.of(first, second)));
        when(f.guild.getFaction()).thenReturn(first);
        when(f.node.getIsActive()).thenReturn(true); when(f.node.getWealthModifier()).thenReturn(10.0);
        Node idle = f.nodeAt(f.location);
        NodeManager.nodes.addAll(List.of(f.node, idle));
        NodeManager.syncFactionNodeBenefits();
        verify(first, times(1)).updatePrestige();
        NodeManager.syncIfRealmsChanged();
        verify(first, times(1)).updatePrestige();
        // The guild's realm dissolved into another one: the node's value has to follow it.
        when(f.guild.getFaction()).thenReturn(second);
        NodeManager.syncIfRealmsChanged();
        verify(first, times(2)).updatePrestige();
        ArgumentCaptor<Modifier> modifier = ArgumentCaptor.forClass(Modifier.class);
        verify(secondMain, times(2)).addWealthModifier(modifier.capture());
        assertEquals(10.0, modifier.getValue().getAmount());
        NodeManager.syncIfRealmsChanged();
        verify(first, times(2)).updatePrestige();
        Guild other = mock(Guild.class); when(other.getFaction()).thenReturn(second);
        when(f.node.getGuild()).thenReturn(other);
        NodeManager.syncIfRealmsChanged();
        verify(first, times(3)).updatePrestige();
        when(f.node.getGuild()).thenReturn(null);
        NodeManager.syncIfRealmsChanged();
        verify(first, times(4)).updatePrestige();
        NodeManager.nodes.remove(idle);
        NodeManager.syncIfRealmsChanged();
        verify(first, times(4)).updatePrestige();
        NodeManager.nodes.clear();
        NodeManager.syncIfRealmsChanged();
        verify(first, times(5)).updatePrestige();
        net.tfminecraft.dowsing.testsupport.FactionFixture.api.when(net.tfminecraft.simplefactions.api.SimpleFactionsApi::ready).thenReturn(false);
        NodeManager.nodes.add(f.node);
        NodeManager.syncIfRealmsChanged();
        verify(first, times(5)).updatePrestige();
    }

    @Test
    void countsCapacityAndUpkeepRespectOwnershipSpecialNodesAndPurchasedLimits() {
        assertEquals(0, NodeManager.getExtraCapacity(null)); assertFalse(NodeManager.canPurchaseCapacity(null));
        assertEquals(0, NodeManager.getTotalUpkeep(null)); assertEquals(0, NodeManager.getNodeAmount(null));
        assertEquals(1, NodeManager.getNodeCapacity(null)); assertEquals(1, NodeManager.getNodeCapacity(f.guild));
        NodeManager.nodes.add(f.node); when(f.node.getIsActive()).thenReturn(true); when(f.node.getDailyUpkeep(any(java.util.function.IntSupplier.class))).thenReturn(2.25);
        Node inactive = f.nodeAt(f.location), unowned = f.nodeAt(f.location), other = f.nodeAt(f.location);
        when(unowned.hasGuild()).thenReturn(false); when(unowned.getIsActive()).thenReturn(true);
        Guild otherGuild = mock(Guild.class); when(otherGuild.getId()).thenReturn("other"); when(other.getGuild()).thenReturn(otherGuild); when(other.getIsActive()).thenReturn(true);
        NodeManager.nodes.addAll(List.of(inactive, unowned, other));
        assertEquals(2, NodeManager.getNodeAmount(f.guild)); assertEquals(2.25, NodeManager.getTotalUpkeep(f.guild));
        when(f.block.isSpecial()).thenReturn(true); assertEquals(0, NodeManager.getNodeAmount(f.guild));
        Cache.extraCapacity = true; Cache.membersPerCapacity = 1; Cache.maxMemberCapacity = 1;
        NodeManager.extraCapacityByGuild.put("guild", 2); assertEquals(4, NodeManager.getNodeCapacity(f.guild));
        assertTrue(NodeManager.canPurchaseCapacity(f.guild)); NodeManager.extraCapacityByGuild.put("guild", 3);
        assertFalse(NodeManager.canPurchaseCapacity(f.guild));
        when(f.guild.isHeld()).thenReturn(true);
        assertEquals(0, NodeManager.getTotalUpkeep(f.guild));
    }

    @Test
    void aHeldRealmsNodesPauseInsteadOfProducing() {
        Node held = f.nodeAt(f.location);
        when(held.getIsActive()).thenReturn(true); when(held.getGuild()).thenReturn(f.guild);
        when(f.guild.isHeld()).thenReturn(true);
        NodeManager.nodes.add(held);
        try (Cycles cycles = new Cycles(); var droppers = mockConstruction(ItemDropper.class)) {
            f.manager.start(); cycles.tasks.get(1200L).run();
            verify(held, never()).tickCycle(); verify(held, never()).tick(); assertTrue(droppers.constructed().isEmpty());
            verify(f.chunk, never()).setForceLoaded(true);
        }
    }

    @Test
    void validationRemovesEveryAdjacentInvalidNodeInTheSamePass() {
        NodeManager.nodes.addAll(List.of(f.node, f.nodeAt(f.location)));
        when(f.worldBlock.getType()).thenReturn(Material.AIR);
        List<Node> invalid = List.copyOf(NodeManager.nodes);
        f.manager.validate();
        assertTrue(NodeManager.nodes.isEmpty(), "Adjacent invalid nodes must not be skipped");
        for (Node node : invalid) verify(node).breakNode();
    }

    @Test
    void locationLookupAndValidationPreserveExistingBarrierNodes() {
        NodeManager.nodes.add(f.node);
        assertSame(f.node, f.manager.getByLocation(f.location)); assertNull(f.manager.getByLocation(f.location.clone().add(1, 0, 0)));
        f.manager.validate(); assertEquals(List.of(f.node), NodeManager.nodes); verify(f.node, never()).breakNode();
    }

    @Test
    void furnitureLookupSearchesOnlyNearbyEntitiesAndReturnsItsNamespace() {
        Entity distant = mock(Entity.class), empty = mock(Entity.class), carrier = mock(Entity.class);
        CustomFurniture furniture = mock(CustomFurniture.class); when(furniture.getNamespace()).thenReturn("nodes");
        when(f.world.getEntities()).thenReturn(List.of(distant, empty, carrier));
        when(f.world.getNearbyEntities(f.location, .2, .2, .2)).thenReturn(List.of(empty, carrier));
        try (var api = mockStatic(CustomFurniture.class)) {
            api.when(() -> CustomFurniture.byAlreadySpawned(carrier)).thenReturn(furniture);
            assertEquals("nodes", f.manager.getClickedFurniture(f.worldBlock));
            api.verify(() -> CustomFurniture.byAlreadySpawned(distant), never());
            api.when(() -> CustomFurniture.byAlreadySpawned(carrier)).thenReturn(null);
            assertEquals("none", f.manager.getClickedFurniture(f.worldBlock));
        }
    }

    @Test
    void furnitureLookupAcceptsTheCollectionReturnedByBukkit() {
        Entity entity = mock(Entity.class); CustomFurniture furniture = mock(CustomFurniture.class);
        when(furniture.getNamespace()).thenReturn("nodes"); when(f.world.getEntities()).thenReturn(List.of(entity));
        when(f.world.getNearbyEntities(f.location, .2, .2, .2)).thenReturn(new HashSet<>(List.of(entity)));
        try (var api = mockStatic(CustomFurniture.class)) {
            api.when(() -> CustomFurniture.byAlreadySpawned(entity)).thenReturn(furniture);
            assertEquals("nodes", f.manager.getClickedFurniture(f.worldBlock));
        }
    }

    @Test
    void cachingReloadsBlockTypeAndPreviouslySelectedProductionMethods() {
        NodeManager.nodes.add(f.node); f.manager.cacheNodes(); assertEquals(1, f.manager.cached.size());
        NodeBlock replacement = mock(NodeBlock.class); NodeType newType = mock(NodeType.class);
        f.blocks.when(() -> BlockLoader.getByString("iron")).thenReturn(replacement);
        f.types.when(() -> TypeLoader.getByString("mine")).thenReturn(newType);
        f.methods.when(() -> PMLoader.getByString("basic")).thenReturn(f.method);
        f.manager.loadCache();
        verify(f.node).setBlock(replacement); verify(f.node).setCurrentType(newType);
        verify(f.slot).setActivePm(f.method); verify(f.node).update();
        NodeManager.nodes.clear(); f.manager.cacheNodes(); assertTrue(f.manager.cached.isEmpty());
    }

    @Test
    void aParticlePassCountsEachGuildOnceAndTracksNodesLetGo() {
        Node second = f.nodeAt(f.location), unowned = f.nodeAt(f.location), other = f.nodeAt(f.location), special = f.nodeAt(f.location), noId = f.nodeAt(f.location);
        when(unowned.hasGuild()).thenReturn(false);
        Guild otherGuild = mock(Guild.class); when(otherGuild.getId()).thenReturn("OTHER"); when(other.getGuild()).thenReturn(otherGuild);
        NodeBlock specialBlock = mock(NodeBlock.class); when(specialBlock.isSpecial()).thenReturn(true); when(special.getBlock()).thenReturn(specialBlock);
        Guild idless = mock(Guild.class); when(noId.getGuild()).thenReturn(idless);
        NodeManager.nodes.addAll(List.of(f.node, second, unowned, other, special));
        assertEquals(2, NodeManager.getNodeAmount(f.guild));
        NodeManager.nodes.add(noId); // the live walk throws on a guild without an id; a pass skips it
        NodeManager.beginCountPass();
        try {
            assertEquals(2, NodeManager.getNodeAmount(f.guild)); assertEquals(1, NodeManager.getNodeAmount(otherGuild));
            assertEquals(0, NodeManager.getNodeAmount(idless)); assertEquals(0, NodeManager.getNodeAmount(null));
            when(f.node.hasGuild()).thenReturn(false);
            assertEquals(2, NodeManager.getNodeAmount(f.guild), "counted once at the start of the pass");
            NodeManager.released(f.guild, f.node); assertEquals(1, NodeManager.getNodeAmount(f.guild));
            NodeManager.released(f.guild, special); NodeManager.released(null, f.node); NodeManager.released(idless, f.node);
            Guild unknown = mock(Guild.class); when(unknown.getId()).thenReturn("unknown"); NodeManager.released(unknown, f.node);
            assertEquals(1, NodeManager.getNodeAmount(f.guild)); assertEquals(0, NodeManager.getNodeAmount(unknown));
        } finally {
            NodeManager.endCountPass();
        }
        NodeManager.nodes.remove(noId); NodeManager.released(f.guild, second);
        assertEquals(1, NodeManager.getNodeAmount(f.guild), "live again once the pass ends");
    }

    @Test
    void aRealCheckThatLetsANodeGoLowersItsGuildsCountForTheRestOfThePass() {
        Cache.extraCapacity = false;
        Node first = new Node(UUID.randomUUID(), f.block, f.location, f.guild, true, 1, 0, f.type, 60, 0, 25);
        Node second = new Node(UUID.randomUUID(), f.block, f.location, f.guild, true, 1, 0, f.type, 60, 0, 25);
        NodeManager.nodes.addAll(List.of(first, second));
        assertEquals(1, NodeManager.getNodeCapacity(f.guild));
        NodeManager.beginCountPass();
        try {
            assertEquals(2, NodeManager.getNodeAmount(f.guild));
            first.check();
            assertTrue(first.isClaimable(), "over capacity, so the first node is let go");
            assertEquals(1, NodeManager.getNodeAmount(f.guild), "Node.check lowers the pass count");
            second.check();
            assertFalse(second.isClaimable(), "back within capacity, so the second node stays");
        } finally {
            NodeManager.endCountPass();
        }
    }

    @Test
    void timersUseTheirIntendedCadenceAndRunParticlesChecksAndEfficiencyGrowth() {
        Node claimed = f.node, unclaimed = f.nodeAt(f.location);
        when(unclaimed.isClaimable()).thenReturn(true); NodeManager.nodes.addAll(List.of(claimed, unclaimed));
        try (Cycles cycles = new Cycles()) {
            f.manager.start(); assertEquals(java.util.Set.of(5L, 600L, 1200L, 72000L), cycles.tasks.keySet());
            cycles.tasks.get(5L).run(); verify(claimed).check();
            verify(f.world).spawnParticle(Particle.HAPPY_VILLAGER, new Location(f.world, 2.5, 65, 3.5), 10);
            cycles.tasks.get(72000L).run(); verify(claimed).growEfficiency(); verify(unclaimed).growEfficiency();
        }
    }

    @Test
    void productionCycleCompletesRealNodeOutputThenConsumesTheNextInputBatch() {
        Node node = new Node(UUID.randomUUID(), f.block, f.location, null, true, 1, 59, f.type, 0, 3, 50);
        node.setGuild(f.guild); node.setModifiedTime(30); NodeManager.nodes.add(node);
        try (Cycles cycles = new Cycles(); var droppers = mockConstruction(ItemDropper.class, (dropper, context) -> when(dropper.dropItems(node)).thenReturn(true));
             var engines = mockConstruction(NodeEngine.class, (engine, context) -> {
                 when(engine.hasBarrel(node)).thenReturn(true); when(engine.hasHopper(node)).thenReturn(true); when(engine.hasInputs(node)).thenReturn(true);
                 doAnswer(inv -> { node.setInputCounter(node.getInputCounter() + 1); return null; }).when(engine).takeInputs(node);
             })) {
            f.manager.start(); cycles.tasks.get(1200L).run();
            verify(f.chunk).setForceLoaded(true); verify(droppers.constructed().getFirst()).dropItems(node);
            assertEquals(29, node.getTimeLeft()); assertEquals(0, node.getCycleTime()); assertEquals(1, node.getInputCounter());
            verify(engines.constructed().getFirst()).takeInputs(node); assertTrue(node.getIsActive());
        }
    }

    @Test
    void aSyncThatFailsPartWayIsRetriedByTheRealmCheck() {
        Faction realm = mock(Faction.class); Guild main = mock(Guild.class);
        when(realm.getOrCreateMainGuild()).thenReturn(main);
        when(f.guild.getFaction()).thenReturn(realm); when(f.node.getIsActive()).thenReturn(true);
        NodeManager.nodes.add(f.node);
        NodeManager.syncFactionNodeBenefits();
        Faction moved = mock(Faction.class); when(f.guild.getFaction()).thenReturn(moved);
        f.factions.when(FactionManager::getCopy).thenThrow(new IllegalStateException("half way"));
        assertThrows(IllegalStateException.class, NodeManager::syncIfRealmsChanged);
        f.factions.when(FactionManager::getCopy).thenReturn(new ArrayList<>(List.of(moved)));
        Guild movedMain = mock(Guild.class); when(moved.getOrCreateMainGuild()).thenReturn(movedMain);
        NodeManager.syncIfRealmsChanged();
        verify(moved).updatePrestige();
        NodeManager.syncIfRealmsChanged();
        verify(moved, times(1)).updatePrestige();
    }

    @Test
    void theRealmCycleResyncsWhenReadyAndLogsAFailureInsteadOfStopping() {
        Faction realm = mock(Faction.class); Guild main = mock(Guild.class);
        when(realm.getOrCreateMainGuild()).thenReturn(main);
        f.factions.when(FactionManager::getCopy).thenReturn(new ArrayList<>(List.of(realm)));
        when(f.guild.getFaction()).thenReturn(realm); when(f.node.getIsActive()).thenReturn(true);
        NodeManager.nodes.add(f.node);
        try (Cycles cycles = new Cycles()) {
            f.manager.start();
            when(DowsingMain.plugin.nodesReady()).thenReturn(false);
            cycles.tasks.get(600L).run();
            verify(realm, never()).updatePrestige();
            when(DowsingMain.plugin.nodesReady()).thenReturn(true);
            cycles.tasks.get(600L).run();
            verify(realm).updatePrestige();
            when(f.node.getIsActive()).thenThrow(new IllegalStateException("broken node"));
            cycles.tasks.get(600L).run();
            verify(f.logger).log(eq(java.util.logging.Level.SEVERE), eq("Failed to check node realms"), any(IllegalStateException.class));
        }
    }

    @Test
    void failedOutputKeepsTheCyclePendingAndExceptionsDoNotStopOtherNodes() {
        Node inactive = f.nodeAt(f.location), unowned = f.nodeAt(f.location), broken = f.nodeAt(f.location), pending = f.node;
        when(unowned.getIsActive()).thenReturn(true); when(unowned.hasGuild()).thenReturn(false);
        when(broken.getIsActive()).thenThrow(new IllegalStateException("broken node"));
        when(pending.getIsActive()).thenReturn(true); when(pending.getTimeLeft()).thenReturn(0);
        NodeManager.nodes.addAll(List.of(inactive, unowned, broken, pending));
        try (Cycles cycles = new Cycles(); var droppers = mockConstruction(ItemDropper.class)) {
            f.manager.start(); cycles.tasks.get(1200L).run();
            verify(inactive, never()).tick(); verify(unowned, never()).tick(); verify(pending, never()).tickCycle();
            verify(droppers.constructed().getFirst()).dropItems(pending);
            verify(f.logger).log(eq(java.util.logging.Level.SEVERE), contains("Failed to process node"), any(IllegalStateException.class));
        }
    }

    @Test
    void inputFailureStopsTickingAndSuccessfulTicksUpdateMatchingOpenMenusOnly() {
        Node node = f.node; when(node.getIsActive()).thenReturn(true, false); when(node.getTimeLeft()).thenReturn(10);
        when(node.getCycleTime()).thenReturn(60); NodeManager.nodes.add(node); when(f.chunk.isForceLoaded()).thenReturn(true);
        try (Cycles cycles = new Cycles()) {
            f.manager.start(); cycles.tasks.get(1200L).run(); verify(node).input(); verify(node, never()).tick();
            when(node.getIsActive()).thenReturn(true); when(node.getCycleTime()).thenReturn(1);
            cycles.bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));
            InventoryView view = mock(InventoryView.class); when(f.player.getOpenInventory()).thenReturn(view);
            when(view.getTopInventory()).thenReturn(f.inventory); when(view.getTitle()).thenReturn("§7Iron Node");
            f.manager.currentNode.put(f.player, node); cycles.tasks.get(1200L).run();
            verify(f.lastInventory()).updateNodeView(f.player, node, f.inventory);
            when(view.getTitle()).thenReturn("other"); cycles.tasks.get(1200L).run();
            verify(f.lastInventory(), never()).updateNodeView(any(), any(), any());
            when(view.getTopInventory()).thenReturn(null); cycles.tasks.get(1200L).run();
            verify(f.lastInventory(), never()).updateNodeView(any(), any(), any());
            f.manager.currentNode.put(f.player, f.nodeAt(f.location)); cycles.tasks.get(1200L).run();
            f.manager.currentNode.clear(); cycles.tasks.get(1200L).run();
        }
    }

    private static final class Cycles implements AutoCloseable {
        final Map<Long, Runnable> tasks = new HashMap<>();
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        Cycles() {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(DowsingMain.plugin), any(Runnable.class), eq(0L), anyLong())).thenAnswer(inv -> {
                tasks.put(inv.getArgument(3), inv.getArgument(1)); return mock(BukkitTask.class);
            });
        }
        @Override public void close() { bukkit.close(); }
    }
}
