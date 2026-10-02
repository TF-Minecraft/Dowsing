package net.tfminecraft.dowsing.objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;

import dev.lone.itemsadder.api.CustomFurniture;
import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.utils.ItemDropper;
import net.tfminecraft.dowsing.utils.NodeEngine;

class NodeProductionTest {
    NodeBehaviorTest.Fixture f;
    @BeforeEach void setUp() throws Exception {
        f = new NodeBehaviorTest.Fixture();
        // MockBukkit's BiomeMock inherits Object.toString rather than the server's legacy biome label.
        Biome plains = mock(Biome.class);
        when(plains.toString()).thenReturn("PLAINS");
        when(f.worldBlock.getBiome()).thenReturn(plains);
    }
    @AfterEach void tearDown() throws Exception { f.close(); }

    @Test
    void successfulInputConsumesOneBatchWithoutChangingActivation() {
        Node node = f.owned(); node.setIsActive(true); node.getErrors().add("stale");
        try (var engines = engines(true, true, true, true, true)) {
            node.input();
            assertTrue(node.getErrors().isEmpty()); assertTrue(node.getIsActive());
            verify(engines.constructed().getFirst()).takeInputs(node);
            assertEquals(1, node.getInputCounter());
        }
    }

    @Test
    void missingInputEquipmentStopsAnActiveNodeAndRecordsTheFailures() {
        Node node = f.owned(); node.setIsActive(true);
        try (var engines = engines(false, false, false, true, false)) {
            node.input();
            assertFalse(node.getIsActive());
            assertEquals(List.of("§7No barrel", "§7No hopper", "§7Lacking resources"), node.getErrors());
            verify(engines.constructed().getFirst(), never()).takeInputs(any());
        }
    }

    @Test
    void insufficientInputRecordsTheMissingResourcesForAnInactiveNode() {
        Node node = f.owned();
        try (var engines = engines(true, true, false, true, false)) {
            node.input();
            assertFalse(node.getIsActive()); assertEquals(List.of("§7Lacking resources"), node.getErrors());
            verify(engines.constructed().getFirst(), never()).takeInputs(any());
        }
    }

    @Test
    void refundsDelegateOnlyWhenTheEquipmentCanAcceptThem() {
        Node node = f.owned(); node.setInputCounter(2); node.getErrors().add("stale");
        try (var engines = engines(true, true, true, true, true)) {
            node.refund();
            assertEquals(1, node.getInputCounter()); assertTrue(node.getErrors().isEmpty());
            verify(engines.constructed().getFirst()).refund(node);
        }
        node.setIsActive(true);
        try (var engines = engines(false, false, true, true, false)) {
            node.refund();
            assertFalse(node.getIsActive()); assertEquals(1, node.getInputCounter());
            assertEquals(List.of("§7No barrel", "§7No hopper", "§7Lacking resources"), node.getErrors());
            for (NodeEngine engine : engines.constructed()) verify(engine, never()).refund(any());
        }
    }

    @Test
    void successfulActivationResetsTheCycleConsumesInputsAndClearsOldErrors() {
        Node node = f.owned(); node.setModifiedTime(25); node.setTimeLeft(3); node.setCycleTime(59);
        node.getErrors().add("stale");
        when(f.type.getBiomes()).thenReturn(List.of("PLAINS"));
        NodeSlot slot = slot(); when(f.type.getSlots()).thenReturn(List.of(slot));
        try (var engines = engines(true, true, true, true, true)) {
            node.activate();
            assertTrue(node.getIsActive(), node.getErrors().toString()); assertEquals(25, node.getTimeLeft());
            assertEquals(0, node.getCycleTime()); assertEquals(1, node.getInputCounter());
            assertTrue(node.getErrors().isEmpty());
            verify(engines.constructed().getFirst()).checkPrerequisite(slot.getActivePm(), node);
            verify(engines.constructed().getFirst()).takeInputs(node);
        }
    }

    @Test
    void unclaimedNodesCannotStartAndPendingRefundsAreRetriedBeforeStarting() {
        Node node = f.node();
        try (var engines = engines(true, true, true, true, true)) {
            node.activate(); assertFalse(node.getIsActive()); assertTrue(engines.constructed().isEmpty());
            node.setGuild(f.guild); node.setInputCounter(2);
            assertTrue(node.hasPendingRefund());
            node.activate();
            assertFalse(node.getIsActive()); assertEquals(0, node.getInputCounter());
            assertEquals(2, engines.constructed().size());
            for (NodeEngine engine : engines.constructed()) verify(engine, never()).takeInputs(any());
        }
    }

    @Test
    void activationReportsEquipmentBankBiomeAndPrerequisiteFailuresTogether() {
        Node node = f.owned();
        when(f.guild.getBank()).thenReturn(null);
        when(f.type.getBiomes()).thenReturn(List.of("DESERT"));
        NodeSlot slot = slot();
        when(f.type.getSlots()).thenReturn(List.of(slot));
        try (var engines = engines(false, false, false, false, false)) {
            node.activate();
            assertFalse(node.getIsActive());
            assertTrue(node.getErrors().containsAll(List.of("§7No barrel", "§7No hopper", "§7No bank", "§7Wrong biome, change type")));
            assertTrue(node.getErrors().stream().anyMatch(s -> s.contains("Requires At Least")));
            verify(engines.constructed().getFirst(), never()).takeInputs(any());
        }
    }

    @Test
    void activationRejectsMissingInputsAndInsufficientUpkeep() {
        Node node = f.owned(); node.setUpkeep(100.0);
        when(f.guild.getBank().getWealth()).thenReturn(10.0);
        try (var engines = engines(true, true, false, true, false)) {
            node.activate();
            assertFalse(node.getIsActive());
            assertEquals(List.of("§7Lacking resources", "§7Lacking upkeep"), node.getErrors());
        }
        when(f.guild.isBankrupt()).thenReturn(true);
        try (var engines = engines(true, true, true, true, false)) {
            node.activate();
            assertFalse(node.getIsActive());
            assertEquals(List.of("§7No bank", "§7Lacking upkeep"), node.getErrors());
        }
    }

    @Test
    void deactivationDrainsRefundsAndStopsAtTheFirstUnavailableRefund() {
        Node node = f.owned(); node.setIsActive(true); node.setInputCounter(3);
        try (var engines = engines(true, true, true, true, true)) {
            node.deActivate();
            assertFalse(node.getIsActive()); assertEquals(0, node.getInputCounter());
            assertFalse(node.hasPendingRefund()); assertEquals(3, engines.constructed().size());
        }
        node.setInputCounter(2); node.setIsActive(true);
        try (var engines = engines(true, true, true, true, false)) {
            node.deActivate();
            assertFalse(node.getIsActive()); assertEquals(2, node.getInputCounter());
            assertTrue(node.hasPendingRefund()); assertEquals(1, engines.constructed().size());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 60, 61})
    void completedOrInvalidCyclesDoNotRefundConsumedInputs(int cycleTime) {
        Node node = f.owned(); node.setIsActive(true); node.setInputCounter(2); node.setCycleTime(cycleTime);
        try (var engines = engines(true, true, true, true, true)) {
            node.deActivate();
            assertFalse(node.getIsActive()); assertFalse(node.hasPendingRefund());
            assertEquals(2, node.getInputCounter()); assertTrue(engines.constructed().isEmpty());
        }
        node.setGuild(null); node.deActivate(); assertFalse(node.getIsActive());
    }

    @Test
    void breakingAVanillaNodeClearsTheBlockAndReturnsItsItemAtTheBlockCenter() {
        Node node = f.node();
        try (var droppers = mockConstruction(ItemDropper.class)) {
            node.breakNode();
            verify(f.worldBlock).setType(Material.AIR);
            verify(droppers.constructed().getFirst()).dropItem(new Location(f.world, 2.5, 64, 3.5), "v.IRON_BLOCK", false);
            verify(f.world).playSound(new Location(f.world, 2.5, 64, 3.5), Sound.ENTITY_GLOW_ITEM_FRAME_REMOVE_ITEM, 0.5f, 1f);
        }
    }

    @Test
    void furnitureRemovalSkipsUnrelatedEntitiesAndHandlesItemsAdderLookupFailures() {
        Node node = f.node(); when(f.block.getBlock()).thenReturn("ia.nodes:iron");
        when(f.worldBlock.getType()).thenReturn(Material.BARRIER);
        Entity unrelated = mock(Entity.class); ArmorStand invalid = mock(ArmorStand.class);
        ItemFrame empty = mock(ItemFrame.class); ItemDisplay carrier = mock(ItemDisplay.class);
        ItemDisplay after = mock(ItemDisplay.class); CustomFurniture furniture = mock(CustomFurniture.class);
        Location center = new Location(f.world, 2.5, 64.5, 3.5);
        when(f.world.getNearbyEntities(center, 1.5, 1.5, 1.5)).thenReturn(List.of(unrelated, invalid, empty, carrier, after));
        try (var furnitureApi = mockStatic(CustomFurniture.class); var droppers = mockConstruction(ItemDropper.class)) {
            furnitureApi.when(() -> CustomFurniture.byAlreadySpawned(invalid)).thenThrow(new IllegalArgumentException("not furniture"));
            furnitureApi.when(() -> CustomFurniture.byAlreadySpawned(carrier)).thenReturn(furniture);
            node.breakNode();
            verify(furniture).remove(false); verify(f.worldBlock).setType(Material.AIR);
            furnitureApi.verify(() -> CustomFurniture.byAlreadySpawned(unrelated), never());
            furnitureApi.verify(() -> CustomFurniture.byAlreadySpawned(after), never());
            verify(droppers.constructed().getFirst()).dropItem(new Location(f.world, 2.5, 64, 3.5), "ia.nodes:iron", false);
        }
    }

    @Test
    void absentFurnitureAndAlreadyEmptyBlocksStillReturnTheNodeItem() {
        Node node = f.node(); when(f.block.getBlock()).thenReturn("ia.nodes:iron");
        when(f.worldBlock.getType()).thenReturn(Material.AIR);
        when(f.world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        try (var droppers = mockConstruction(ItemDropper.class)) {
            node.breakNode(); verify(f.worldBlock, never()).setType(any());
            verify(droppers.constructed().getFirst()).dropItem(any(), eq("ia.nodes:iron"), eq(false));
        }
    }

    private NodeSlot slot() {
        NodeSlot slot = mock(NodeSlot.class); ProductionMethod pm = mock(ProductionMethod.class);
        when(slot.getActivePm()).thenReturn(pm); when(pm.getId()).thenReturn("advanced_fuel");
        when(pm.getPrerequisite()).thenReturn("basic_fuel"); return slot;
    }

    private MockedConstruction<NodeEngine> engines(boolean barrel, boolean hopper, boolean inputs,
                                                   boolean prerequisite, boolean refundSuccess) {
        return mockConstruction(NodeEngine.class, (engine, context) -> {
            when(engine.hasBarrel(any())).thenReturn(barrel); when(engine.hasHopper(any())).thenReturn(hopper);
            when(engine.hasInputs(any())).thenReturn(inputs); when(engine.checkPrerequisite(any(), any())).thenReturn(prerequisite);
            doAnswer(inv -> { Node node = inv.getArgument(0); node.setInputCounter(node.getInputCounter() + 1); return null; }).when(engine).takeInputs(any());
            doAnswer(inv -> {
                Node node = inv.getArgument(0);
                if (refundSuccess) node.setInputCounter(node.getInputCounter() - 1);
                return null;
            }).when(engine).refund(any());
        });
    }
}
