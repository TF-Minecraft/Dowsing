package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeSlot;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.dowsing.objects.ProductionMethod;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemChecker;

class NodeEngineTest {
    ServerMock server;
    MockedStatic<TLibs> tlibs;
    NodeEngine engine;
    Node node;
    NodeType type;
    World world;
    Block below, above;
    Inventory inventory;
    AtomicInteger inputs;

    @BeforeEach void setUp() {
        server = MockBukkit.mock(); engine = new NodeEngine(); inventory = server.createInventory(null, 9);
        node = mock(Node.class); type = mock(NodeType.class); world = mock(World.class);
        below = mock(Block.class); above = mock(Block.class); Barrel barrel = mock(Barrel.class);
        when(node.getLoc()).thenReturn(new Location(world, 2, 64, 3)); when(node.getCurrentType()).thenReturn(type);
        when(node.getMultiplier()).thenReturn(2); inputs = new AtomicInteger(2);
        when(node.getInputCounter()).thenAnswer(inv -> inputs.get()); doAnswer(inv -> { inputs.set(inv.getArgument(0)); return null; }).when(node).setInputCounter(anyInt());
        when(world.getBlockAt(any(Location.class))).thenAnswer(inv -> ((Location) inv.getArgument(0)).getBlockY() == 63 ? below : above);
        when(below.getType()).thenReturn(Material.BARREL); when(below.getState()).thenReturn(barrel); when(barrel.getInventory()).thenReturn(inventory);
        when(above.getType()).thenReturn(Material.HOPPER);
        tlibs = mockStatic(TLibs.class); ItemAPI api = mock(ItemAPI.class); ItemChecker checker = mock(ItemChecker.class);
        tlibs.when(TLibs::getItemAPI).thenReturn(api); when(api.getChecker()).thenReturn(checker);
        when(checker.checkItemWithPath(any(), anyString())).thenAnswer(inv -> {
            ItemStack item = inv.getArgument(0); String path = inv.getArgument(1);
            return path.equalsIgnoreCase("v." + item.getType().name());
        });
    }

    @AfterEach void tearDown() { tlibs.close(); MockBukkit.unmock(); }

    @Test
    void equipmentChecksTheBlocksImmediatelyBelowAndAboveTheNode() {
        assertTrue(engine.hasBarrel(node)); assertTrue(engine.hasHopper(node));
        when(below.getType()).thenReturn(Material.STONE); when(above.getType()).thenReturn(Material.AIR);
        assertFalse(engine.hasBarrel(node)); assertFalse(engine.hasHopper(node));
        assertEquals(new Location(world, 2, 64, 3), node.getLoc());
    }

    @Test
    void inputChecksAggregateRepeatedCostsAcrossSlotsAndMultipleInventoryStacks() {
        configureInputs(List.of("v.STONE(2)", "v.COAL(1)", "malformed"), List.of("v.STONE(1)"));
        inventory.setItem(0, new ItemStack(Material.STONE, 1)); inventory.setItem(2, new ItemStack(Material.STONE, 3));
        inventory.setItem(3, new ItemStack(Material.STONE, 2)); inventory.setItem(4, new ItemStack(Material.COAL, 2));
        inventory.setItem(5, new ItemStack(Material.DIAMOND, 5));
        assertTrue(engine.hasInputs(node)); assertEquals(6, count(Material.STONE)); assertEquals(2, inputs.get());
        inventory.setItem(3, null); assertFalse(engine.hasInputs(node));
        assertFalse(engine.check(node, "v.EMERALD", 1, inventory));
        assertTrue(engine.compareItem("v.COAL", new ItemStack(Material.COAL)));
        assertFalse(engine.compareItem("v.COAL", new ItemStack(Material.STONE)));
    }

    @Test
    void takingInputsConsumesExactlyOneAggregatedBatchAndLeavesUnrelatedItems() {
        configureInputs(List.of("v.STONE(2)", "v.COAL(1)", "malformed"), List.of("v.STONE(1)"));
        inventory.setItem(0, new ItemStack(Material.STONE, 2)); inventory.setItem(1, new ItemStack(Material.STONE, 7));
        inventory.setItem(2, new ItemStack(Material.COAL, 3)); inventory.setItem(3, new ItemStack(Material.DIAMOND, 4));
        engine.takeInputs(node);
        assertEquals(3, inputs.get()); assertEquals(3, count(Material.STONE)); assertEquals(1, count(Material.COAL)); assertEquals(4, count(Material.DIAMOND));
        engine.take(node, "v.EMERALD", 1, inventory); assertEquals(4, count(Material.DIAMOND));
        engine.take(node, "v.STONE", 100, inventory); assertEquals(0, count(Material.STONE));
    }

    @Test
    void successfulRefundRestoresAnAggregatedBatchBeforeConsumingItsCredit() {
        configureInputs(List.of("v.STONE(2)", "v.COAL(1)", "malformed"), List.of("v.STONE(1)"));
        try (var creators = mockConstruction(ItemCreator.class, (creator, context) -> {
            when(creator.getItemFromPath("v.STONE")).thenAnswer(inv -> new ItemStack(Material.STONE));
            when(creator.getItemFromPath("v.COAL")).thenAnswer(inv -> new ItemStack(Material.COAL));
        })) {
            engine.refund(node);
            assertEquals(6, count(Material.STONE)); assertEquals(2, count(Material.COAL)); assertEquals(1, inputs.get());
        }
    }

    @Test
    void missingRefundItemsKeepTheEntireCreditAndDoNotPartiallyRefundOtherItems() {
        configureInputs(List.of("v.STONE(2)", "missing(1)"));
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 3));
        ItemStack[] before = copyContents();
        try (var creators = mockConstruction(ItemCreator.class, (creator, context) -> when(creator.getItemFromPath("v.STONE")).thenAnswer(inv -> new ItemStack(Material.STONE)))) {
            engine.refund(node);
            assertAll(() -> assertEquals(2, inputs.get()), () -> assertArrayEquals(before, inventory.getContents()));
        }
    }

    @Test
    void fullInventoriesKeepTheRefundCreditWithoutLosingOrDuplicatingItems() {
        configureInputs(List.of("v.STONE(1)"));
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, new ItemStack(Material.DIAMOND, 64));
        ItemStack[] before = copyContents();
        try (var creators = mockConstruction(ItemCreator.class, (creator, context) -> when(creator.getItemFromPath("v.STONE")).thenAnswer(inv -> new ItemStack(Material.STONE)))) {
            engine.refund(node);
            assertAll(() -> assertEquals(2, inputs.get()), () -> assertArrayEquals(before, inventory.getContents()));
            inventory.setItem(0, null); engine.refund(node);
            assertEquals(1, inputs.get()); assertEquals(2, count(Material.STONE)); assertEquals(8 * 64, count(Material.DIAMOND));
        }
    }

    @Test
    void partiallyFittingRefundsRollBackUntilTheWholeBatchCanFit() {
        configureInputs(List.of("v.STONE(2)"));
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, new ItemStack(Material.DIAMOND, 64));
        inventory.setItem(0, new ItemStack(Material.STONE, 63)); ItemStack[] before = copyContents();
        try (var creators = mockConstruction(ItemCreator.class, (creator, context) -> when(creator.getItemFromPath("v.STONE")).thenAnswer(inv -> new ItemStack(Material.STONE)))) {
            engine.refund(node);
            assertAll(() -> assertEquals(2, inputs.get()), () -> assertArrayEquals(before, inventory.getContents()));
            inventory.setItem(1, null); engine.refund(node);
            assertEquals(1, inputs.get()); assertEquals(67, count(Material.STONE));
        }
    }

    @Test
    void directItemAdditionHandlesAvailableAndMissingDefinitions() {
        try (var creators = mockConstruction(ItemCreator.class, (creator, context) -> when(creator.getItemFromPath("v.STONE")).thenAnswer(inv -> new ItemStack(Material.STONE)))) {
            engine.addItem("missing", 3, inventory); assertTrue(inventory.isEmpty());
            engine.addItem("v.STONE", 3, inventory); assertEquals(3, count(Material.STONE));
        }
    }

    @Test
    void prerequisitesCompareTheActiveWeightWithTheRequiredMethodAcrossSlots() {
        ProductionMethod selected = mock(ProductionMethod.class); when(selected.getPrerequisite()).thenReturn("none");
        assertTrue(engine.checkPrerequisite(selected, node)); when(selected.getPrerequisite()).thenReturn("required");
        assertFalse(engine.checkPrerequisite(selected, node));
        ProductionMethod required = mock(ProductionMethod.class), active = mock(ProductionMethod.class), other = mock(ProductionMethod.class);
        when(required.getId()).thenReturn("required"); when(required.getWeight()).thenReturn(2);
        when(active.getId()).thenReturn("active"); when(active.getWeight()).thenReturn(1); when(other.getId()).thenReturn("other");
        NodeSlot slot = mock(NodeSlot.class); when(slot.getPms()).thenReturn(List.of(other, required)); when(slot.getActivePm()).thenReturn(active);
        when(type.getSlots()).thenReturn(List.of(slot)); assertFalse(engine.checkPrerequisite(selected, node));
        when(active.getWeight()).thenReturn(2); assertTrue(engine.checkPrerequisite(selected, node));
        when(active.getWeight()).thenReturn(3); assertTrue(engine.checkPrerequisite(selected, node));
    }

    @SafeVarargs private void configureInputs(List<String>... costs) {
        List<NodeSlot> slots = new java.util.ArrayList<>();
        for (List<String> entries : costs) {
            NodeSlot slot = mock(NodeSlot.class); ProductionMethod pm = mock(ProductionMethod.class);
            when(pm.getInputs()).thenReturn(entries); when(slot.getActivePm()).thenReturn(pm); slots.add(slot);
        }
        when(type.getSlots()).thenReturn(slots);
    }
    private int count(Material material) { return Arrays.stream(inventory.getContents()).filter(item -> item != null && item.getType() == material).mapToInt(ItemStack::getAmount).sum(); }
    private ItemStack[] copyContents() { return Arrays.stream(inventory.getContents()).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new); }
}
