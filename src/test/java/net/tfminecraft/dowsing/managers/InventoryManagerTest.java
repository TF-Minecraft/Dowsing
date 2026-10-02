package net.tfminecraft.dowsing.managers;

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

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import dev.lone.itemsadder.api.CustomStack;
import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.objects.Level;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeBlock;
import net.tfminecraft.dowsing.objects.NodeSlot;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.dowsing.objects.ProductionMethod;
import net.tfminecraft.dowsing.utils.Database;
import net.tfminecraft.dowsing.utils.ItemCreator;
import net.tfminecraft.simplefactions.guild.Guild;

@SuppressWarnings("deprecation")
class InventoryManagerTest {
    final Map<Field, Object> previousCache = new HashMap<>();
    List<Node> previousNodes;
    HashMap<String, Integer> previousCapacity;
    int previousMaxCapacity;
    DowsingMain previousPlugin;
    ServerMock server;
    PlayerMock player;
    InventoryManager manager;
    MockedStatic<CustomStack> custom;
    CustomStack icon;
    Node node;
    NodeBlock block;
    NodeType type;
    NodeSlot slot;
    ProductionMethod method;
    Guild guild;
    Location location;

    @BeforeEach void setUp() throws Exception {
        for (Field field : Cache.class.getDeclaredFields()) if (Modifier.isStatic(field.getModifiers())) previousCache.put(field, field.get(null));
        previousNodes = NodeManager.nodes; previousCapacity = NodeManager.extraCapacityByGuild;
        previousMaxCapacity = net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity; previousPlugin = DowsingMain.plugin;
        server = MockBukkit.mock(); player = server.addPlayer("Viewer"); location = new Location(server.addSimpleWorld("world"), 1, 64, 2);
        DowsingMain.plugin = mock(DowsingMain.class); when(DowsingMain.plugin.getServer()).thenReturn(server);
        NodeManager.nodes = new ArrayList<>(); NodeManager.extraCapacityByGuild = new HashMap<>();
        net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity = 3;
        Cache.cycleLength = 60; Cache.refundPercentage = 0.8; Cache.naturalYieldEnabled = false;
        custom = mockStatic(CustomStack.class); icon = mock(CustomStack.class);
        when(icon.getItemStack()).thenAnswer(inv -> new ItemStack(Material.PAPER)); custom.when(() -> CustomStack.getInstance(anyString())).thenReturn(icon);
        manager = new InventoryManager(); manager.ic = mock(ItemCreator.class);
        when(manager.ic.createTypeItemNode(any(), any(), anyBoolean())).thenAnswer(inv -> named(Material.IRON_INGOT, "Mine", List.of("details")));
        when(manager.ic.updateMenuItem(any(), any())).thenAnswer(inv -> ((ProductionMethod) inv.getArgument(1)).getMenuItem());
        when(manager.ic.createNaturalYieldItem(any())).thenAnswer(inv -> named(Material.EMERALD, "Natural yield", List.of()));
        when(manager.ic.getUpgradeDownGradeFormatted(any(), anyString())).thenReturn(List.of("effects"));
        when(manager.ic.formatTime(anyInt())).thenAnswer(inv -> inv.getArgument(0) + "m");
        when(manager.ic.getFormattedCost(anyString(), anyInt())).thenAnswer(inv -> "Drop " + inv.getArgument(0));
        node = mock(Node.class); block = mock(NodeBlock.class); type = mock(NodeType.class); slot = mock(NodeSlot.class); method = mock(ProductionMethod.class); guild = mock(Guild.class);
        when(node.getGuild()).thenReturn(guild); when(node.hasGuild()).thenReturn(true); when(node.getLoc()).thenReturn(location);
        when(guild.getId()).thenReturn("guild"); when(guild.getName()).thenReturn("Builders"); when(guild.getLeader()).thenReturn("Leader");
        when(node.getBlock()).thenReturn(block); when(node.getCurrentType()).thenReturn(type); when(node.getLevel()).thenReturn(1);
        when(node.getCapacity()).thenReturn(2); when(node.getMultiplier()).thenReturn(1); when(node.getCostIncrease()).thenReturn(1.0);
        when(node.getNaturalYield()).thenReturn(3); when(node.getNodeCapacityCost()).thenReturn(1250.0);
        when(node.getTimeLeft()).thenReturn(30); when(node.getCycleTime()).thenReturn(20);
        when(block.getResource()).thenReturn("Iron"); when(block.isTransferable()).thenReturn(true); when(block.isBreakable()).thenReturn(true);
        when(block.getTypes()).thenReturn(List.of(type)); when(type.getId()).thenReturn("mine"); when(type.getResource()).thenReturn("Iron");
        when(type.getMaxLevel()).thenReturn(2); when(type.getSlots()).thenReturn(List.of(slot));
        when(slot.getId()).thenReturn("fuel"); when(slot.getSlot()).thenReturn(10); when(slot.getPms()).thenReturn(List.of(method));
        when(slot.getActivePm()).thenReturn(method); when(method.getId()).thenReturn("basic");
        ItemStack methodItem = named(Material.COAL, "Basic", List.of("input")); when(method.getMenuItem()).thenReturn(methodItem);
        Level one = mock(Level.class), two = mock(Level.class); when(one.getCost()).thenReturn(10.0); when(two.getCost()).thenReturn(20.0);
        when(type.getLevels()).thenReturn(List.of(one, two));
    }

    @AfterEach void tearDown() throws Exception {
        custom.close(); MockBukkit.unmock(); DowsingMain.plugin = previousPlugin;
        NodeManager.nodes = previousNodes; NodeManager.extraCapacityByGuild = previousCapacity;
        net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity = previousMaxCapacity;
        for (var entry : previousCache.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @Test
    void nodeViewBuildsItsButtonsAndFillsUnusedSlots() {
        manager.nodeView(player, node); Inventory inventory = player.getOpenInventory().getTopInventory();
        assertEquals("§7Iron Node", player.getOpenInventory().getTitle()); assertEquals(27, inventory.getSize());
        assertEquals(Material.IRON_INGOT, inventory.getItem(9).getType()); assertEquals(Material.COAL, inventory.getItem(10).getType());
        assertEquals(Material.EMERALD, inventory.getItem(0).getType()); assertEquals(Material.NETHER_STAR, inventory.getItem(24).getType());
        assertTrue(inventory.getItem(6).getItemMeta().getDisplayName().contains("Transfer Node"));
        assertEquals("§cDelete Node", inventory.getItem(18).getItemMeta().getDisplayName());
        assertEquals("§cINACTIVE", inventory.getItem(17).getItemMeta().getDisplayName());
        for (ItemStack item : inventory.getContents()) assertNotNull(item);
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItem(1).getType());
        verify(manager.ic).updateMenuItem(node, method);
    }

    @Test
    void protectedNodesHideUnavailableButtonsAndAdminsCanSeeDelete() {
        when(node.getNaturalYield()).thenReturn(0); when(block.isTransferable()).thenReturn(false); when(block.isBreakable()).thenReturn(false);
        net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity = 0;
        manager.nodeView(player, node); Inventory inventory = player.getOpenInventory().getTopInventory();
        for (int index : new int[]{0, 6, 18, 24}) assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItem(index).getType());
        player.setOp(true); manager.nodeView(player, node);
        assertEquals("§cDelete Node", player.getOpenInventory().getTopInventory().getItem(18).getItemMeta().getDisplayName());
    }

    @Test
    void productionMethodMenuHighlightsOnlyTheCurrentChoice() {
        ProductionMethod alternative = mock(ProductionMethod.class); when(alternative.getId()).thenReturn("advanced");
        ItemStack alternativeItem = named(Material.DIAMOND, "Advanced", List.of("input")); when(alternative.getMenuItem()).thenReturn(alternativeItem);
        when(slot.getPms()).thenReturn(List.of(method, alternative));
        manager.slotView(player, node, slot); Inventory inventory = player.getOpenInventory().getTopInventory();
        assertEquals("§7Iron Node: Fuel", player.getOpenInventory().getTitle());
        ItemMeta current = inventory.getItem(0).getItemMeta();
        assertEquals(1, current.getEnchantLevel(Enchantment.UNBREAKING)); assertTrue(current.hasItemFlag(ItemFlag.HIDE_ENCHANTS));
        assertTrue(current.getLore().contains("§aCURRENT")); assertFalse(inventory.getItem(1).getItemMeta().hasEnchants());
        assertEquals("§fBack", inventory.getItem(26).getItemMeta().getDisplayName());
        assertFalse(method.getMenuItem().getItemMeta().hasEnchants());
        for (ItemStack item : inventory.getContents()) assertNotNull(item);
    }

    @Test
    void typeMenuHighlightsCurrentTypeMatchingNaturalResourcesAndBiomeRestrictions() throws Exception {
        NodeType other = mock(NodeType.class); when(other.getId()).thenReturn("other"); when(other.getResource()).thenReturn("Gold");
        when(other.getBiomes()).thenReturn(List.of("DESERT", "BADLANDS")); when(block.getTypes()).thenReturn(List.of(type, other));
        Cache.naturalYieldEnabled = true;
        try (var databases = mockConstruction(Database.class, (db, context) -> {
            when(db.hasResource(location.getChunk())).thenReturn(true); when(db.getResource(location.getChunk())).thenReturn("iron.3");
        })) {
            manager.typeView(player, node); Inventory inventory = player.getOpenInventory().getTopInventory();
            assertEquals("§7Iron Node: Type", player.getOpenInventory().getTitle());
            assertTrue(inventory.getItem(0).getItemMeta().getLore().contains("§2Natural Yield Detected!"));
            assertTrue(inventory.getItem(0).getItemMeta().getLore().contains("§aCURRENT"));
            assertFalse(inventory.getItem(1).getItemMeta().hasEnchants());
            assertTrue(inventory.getItem(1).getItemMeta().getLore().contains("§7- Desert"));
            assertEquals("§fBack", inventory.getItem(26).getItemMeta().getDisplayName());
            for (ItemStack item : inventory.getContents()) assertNotNull(item);
        }
    }

    @Test
    void typeMenuStillOpensWithoutResourceDataOrAfterReadErrors() throws Exception {
        Cache.naturalYieldEnabled = true;
        try (var databases = mockConstruction(Database.class)) {
            manager.typeView(player, node);
            assertFalse(player.getOpenInventory().getTopInventory().getItem(0).getItemMeta().getLore().contains("§2Natural Yield Detected!"));
        }
        try (var databases = mockConstruction(Database.class, (db, context) -> when(db.hasResource(any())).thenThrow(new IOException("unavailable")))) {
            manager.typeView(player, node); assertEquals(27, player.getOpenInventory().getTopInventory().getSize());
        }
        Cache.naturalYieldEnabled = false; manager.typeView(player, node);
        assertEquals(27, player.getOpenInventory().getTopInventory().getSize());
    }

    @Test
    void confirmationViewContainsBothChoicesAndSafeFiller() {
        manager.confirmView(player); Inventory inventory = player.getOpenInventory().getTopInventory();
        assertEquals("§7Confirm Action", player.getOpenInventory().getTitle());
        assertEquals(Material.GREEN_CONCRETE, inventory.getItem(11).getType()); assertEquals("§aConfirm", inventory.getItem(11).getItemMeta().getDisplayName());
        assertEquals(Material.RED_CONCRETE, inventory.getItem(15).getType()); assertEquals("§cCancel", inventory.getItem(15).getItemMeta().getDisplayName());
        for (ItemStack item : inventory.getContents()) assertNotNull(item);
    }

    @Test
    void updatesIgnoreSmallInventoriesAndRefreshAllLiveStatistics() {
        Inventory small = server.createInventory(null, 9); manager.updateNodeView(player, node, small);
        assertTrue(small.isEmpty());
        Inventory inventory = server.createInventory(null, 27); manager.updateNodeView(player, node, inventory);
        assertEquals(Material.EMERALD, inventory.getItem(0).getType()); assertEquals(Material.COAL, inventory.getItem(10).getType());
        when(node.getNaturalYield()).thenReturn(0); when(node.getIsActive()).thenReturn(true);
        manager.updateNodeView(player, node, inventory);
        assertTrue(inventory.getItem(0) == null || inventory.getItem(0).getType() == Material.AIR || inventory.getItem(0).getType() == Material.GRAY_STAINED_GLASS_PANE);
        assertEquals("§aACTIVE", inventory.getItem(17).getItemMeta().getDisplayName());
    }

    @Test
    void liveUpdatesPreserveDeletionRestrictionsAndClearUnavailableCapacityPurchases() {
        manager.nodeView(player, node); Inventory inventory = player.getOpenInventory().getTopInventory();
        when(block.isBreakable()).thenReturn(false); net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity = 0;
        manager.updateNodeView(player, node, inventory);
        assertAll(() -> assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItem(18).getType()),
                () -> assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItem(24).getType()));
    }

    @Test
    void ownershipCapacityAndCostItemsDistinguishUnderAtAndOverCapacity() {
        assertTrue(manager.createGlobe(node).getItemMeta().getLore().contains("§eNodes: §a0/2"));
        NodeManager.nodes.add(node); when(node.getCapacity()).thenReturn(1);
        assertTrue(manager.createGlobe(node).getItemMeta().getLore().contains("§eNodes: 1/1"));
        when(node.getCapacity()).thenReturn(0); when(node.getCostIncrease()).thenReturn(1.5);
        List<String> lore = manager.createGlobe(node).getItemMeta().getLore();
        assertTrue(lore.contains("§eNodes: §c1/0")); assertTrue(lore.stream().anyMatch(s -> s.contains("50.0%")));
        assertEquals("§eBelongs to: Builders", manager.createGlobe(node).getItemMeta().getDisplayName());
        assertTrue(manager.createCapacityButton(node).getItemMeta().getLore().contains("§7Cost: §61250.0d"));
        assertEquals("§fBack", manager.createBackButton().getItemMeta().getDisplayName());
        assertTrue(manager.createDeleteButton().getItemMeta().getLore().contains("§7Cannot be undone"));
    }

    @Test
    void cycleResultAndStatusItemsExplainEmptyResultsErrorsAndPendingRefunds() {
        assertTrue(manager.createCycle(node).getItemMeta().getLore().contains("§fNothing"));
        when(node.getLastResult()).thenReturn(Map.of("v.STONE", 3));
        assertTrue(manager.createCycle(node).getItemMeta().getLore().contains("Drop v.STONE(3)"));
        assertEquals("§cINACTIVE", manager.createStatus(node).getItemMeta().getDisplayName());
        when(node.getErrors()).thenReturn(List.of("missing barrel"));
        assertEquals(List.of("missing barrel"), manager.createStatus(node).getItemMeta().getLore());
        when(node.hasPendingRefund()).thenReturn(true); ItemStack retry = manager.createStatus(node);
        assertEquals("§eRetry Refund", retry.getItemMeta().getDisplayName());
        assertTrue(retry.getItemMeta().getLore().stream().anyMatch(s -> s.contains("click to retry")));
        when(node.getIsActive()).thenReturn(true);
        double[] efficiencies = {0, 20, 40, 60, 80}; String[] colors = {"§4", "§c", "§e", "§a", "§2"};
        for (int index = 0; index < efficiencies.length; index++) {
            when(node.getEfficiency()).thenReturn(efficiencies[index]);
            List<String> lore = manager.createStatus(node).getItemMeta().getLore();
            assertTrue(lore.contains("§7Time until next output: §f30m")); assertTrue(lore.contains("§7Time until next input: §f40m"));
            assertTrue(lore.get(3).contains(colors[index]));
        }
    }

    @Test
    void upgradeAndDowngradeItemsDescribeCostsRefundsAndBoundaryLevels() {
        when(node.getCostIncrease()).thenReturn(1.5);
        List<String> upgrade = manager.createUpgrade(node).getItemMeta().getLore();
        assertTrue(upgrade.contains("§7Upgrade to level 2: §f30.0d")); assertTrue(upgrade.contains("§bClick to Upgrade!")); assertTrue(upgrade.contains("effects"));
        assertTrue(manager.createDowngrade(node).getItemMeta().getLore().contains("§7Lowest level"));
        when(node.getLevel()).thenReturn(2);
        assertTrue(manager.createUpgrade(node).getItemMeta().getLore().contains("§7Max level"));
        List<String> downgrade = manager.createDowngrade(node).getItemMeta().getLore();
        assertTrue(downgrade.contains("§7Refunds: §f16.0d §7(80.0%)")); assertTrue(downgrade.contains("§bClick to Downgrade!"));
    }

    @Test
    void itemHelpersPreserveNamesAndExposeUnavailableItemsAdderDefinitions() {
        assertEquals("Named", manager.createItemStack(Material.STONE, "Named").getItemMeta().getDisplayName());
        assertEquals(Material.PAPER, manager.getItemsAdderItem("available").getType());
        custom.when(() -> CustomStack.getInstance("missing")).thenReturn(null);
        assertNull(manager.getItemsAdderItem("missing"));
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }
}
