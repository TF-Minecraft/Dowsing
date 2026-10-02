package net.tfminecraft.dowsing.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import dev.lone.itemsadder.api.CustomFurniture;
import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.FurniturePlaceSuccessEvent;
import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.enums.ConfirmType;
import net.tfminecraft.dowsing.loaders.BlockLoader;
import net.tfminecraft.dowsing.loaders.PMLoader;
import net.tfminecraft.dowsing.loaders.TypeLoader;
import net.tfminecraft.dowsing.objects.Level;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeBlock;
import net.tfminecraft.dowsing.objects.NodeSlot;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.dowsing.objects.ProductionMethod;
import net.tfminecraft.dowsing.utils.NodeEngine;
import net.tfminecraft.dowsing.utils.NodeToggleLog;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;

@SuppressWarnings("deprecation")
class NodeManagerEventsTest {
    Fixture f;
    @BeforeEach void setUp() throws Exception { f = new Fixture(); }
    @AfterEach void tearDown() throws Exception { f.close(); }

    @Test
    void ordinaryBlocksAndUnknownFurnitureDoNotCreateNodes() {
        BlockPlaceEvent event = f.place();
        f.blocks.when(() -> BlockLoader.getByBlock(Material.BARRIER)).thenReturn(null);
        f.manager.placeVanillaNode(event); verify(event, never()).setCancelled(anyBoolean());
        FurniturePlaceSuccessEvent furniture = f.furniturePlace();
        when(furniture.getNamespacedID()).thenReturn(null); f.manager.placeFurnitureNode(furniture);
        when(furniture.getNamespacedID()).thenReturn("unknown"); f.manager.placeFurnitureNode(furniture);
        when(furniture.getNamespacedID()).thenReturn("nodes:iron"); when(furniture.getBukkitEntity()).thenReturn(null);
        f.manager.placeFurnitureNode(furniture); assertTrue(NodeManager.nodes.isEmpty());
    }

    @Test
    void placementRejectsMissingPlayerDuplicateLocationAndSameChunk() {
        BlockPlaceEvent event = f.place(); when(event.getPlayer()).thenReturn(null);
        f.manager.placeVanillaNode(event); verify(event).setCancelled(true);
        event = f.place(); NodeManager.nodes.add(f.node); f.manager.placeVanillaNode(event);
        verify(event).setCancelled(true);
        NodeManager.nodes.clear(); Node nearby = f.nodeAt(f.location.clone().add(1, 0, 0)); NodeManager.nodes.add(nearby);
        event = f.place(); f.manager.placeVanillaNode(event); verify(event).setCancelled(true);
        verify(f.player).sendMessage(contains("already a node on this land"));
    }

    @Test
    void placementRequiresGuildMembershipMinimumMembersAndAvailableCapacity() {
        BlockPlaceEvent event = f.place();
        f.factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(null);
        f.manager.placeVanillaNode(event); verify(event).setCancelled(true);
        verify(f.player).sendMessage(contains("need to have a guild"));
        f.factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(f.guild);
        Cache.minMembersForNode = 3; event = f.place(); f.manager.placeVanillaNode(event);
        verify(event).setCancelled(true); verify(f.player).sendMessage(contains("at least 3 members"));
        Cache.minMembersForNode = 1; NodeManager.nodes.add(f.nodeAt(f.remoteLocation()));
        event = f.place(); f.manager.placeVanillaNode(event); verify(event).setCancelled(true);
        verify(f.player).sendMessage(contains("filled its node capacity"));
    }

    @Test
    void successfulPlacementConstructsActivatesAndRefreshesTheNode() {
        BlockPlaceEvent event = f.place();
        try (var nodes = mockConstruction(Node.class, (node, context) -> when(node.getLoc()).thenReturn((Location) context.arguments().get(0)))) {
            f.manager.placeVanillaNode(event);
            Node created = nodes.constructed().getFirst();
            assertEquals(List.of(created), NodeManager.nodes);
            verify(created).activate(); verify(created).update(); verify(event, never()).setCancelled(true);
            verify(f.player).sendMessage("Node created");
        }
    }

    @Test
    void specialFurnitureCanBePlacedBelowTheMembershipMinimum() {
        Cache.minMembersForNode = 3; when(f.block.isSpecial()).thenReturn(true);
        FurniturePlaceSuccessEvent event = f.furniturePlace();
        try (var nodes = mockConstruction(Node.class, (node, context) -> when(node.getLoc()).thenReturn(f.location))) {
            f.manager.placeFurnitureNode(event); assertEquals(1, NodeManager.nodes.size());
            verify(nodes.constructed().getFirst()).activate();
        }
    }

    @Test
    void rejectedFurniturePlacementRemovesTheSpawnedFurnitureWhenPresent() {
        NodeManager.nodes.add(f.node); FurniturePlaceSuccessEvent event = f.furniturePlace();
        CustomFurniture furniture = mock(CustomFurniture.class);
        try (var api = mockStatic(CustomFurniture.class)) {
            api.when(() -> CustomFurniture.byAlreadySpawned(event.getBukkitEntity())).thenReturn(furniture);
            f.manager.placeFurnitureNode(event); verify(furniture).remove(false);
            api.when(() -> CustomFurniture.byAlreadySpawned(event.getBukkitEntity())).thenReturn(null);
            f.manager.placeFurnitureNode(event); verify(furniture, times(1)).remove(false);
        }
    }

    @Test
    void managedVanillaAndFurnitureNodesCannotBeBrokenDirectly() {
        BlockBreakEvent vanilla = mock(BlockBreakEvent.class); when(vanilla.getBlock()).thenReturn(f.worldBlock);
        f.manager.breakNode(vanilla); verify(vanilla, never()).setCancelled(true);
        NodeManager.nodes.add(f.node); f.manager.breakNode(vanilla); verify(vanilla).setCancelled(true);
        FurnitureBreakEvent furniture = mock(FurnitureBreakEvent.class);
        f.manager.breakFurnitureNode(furniture);
        when(furniture.getNamespacedID()).thenReturn("unknown"); f.manager.breakFurnitureNode(furniture);
        when(furniture.getNamespacedID()).thenReturn("nodes:iron"); f.manager.breakFurnitureNode(furniture);
        Entity carrier = mock(Entity.class); when(carrier.getLocation()).thenReturn(f.location.clone().add(.5, .5, .5));
        when(furniture.getBukkitEntity()).thenReturn(carrier); f.manager.breakFurnitureNode(furniture);
        verify(furniture).setCancelled(true);
        NodeManager.nodes.clear(); f.manager.breakFurnitureNode(furniture); verify(furniture, times(1)).setCancelled(true);
    }

    @Test
    void interactionFiltersActionsAndCancelsAttacksOnNodes() {
        PlayerInteractEvent event = f.interact(Action.RIGHT_CLICK_AIR); when(event.getClickedBlock()).thenReturn(null);
        f.manager.openNode(event); verify(event, never()).setCancelled(true);
        event = f.interact(Action.LEFT_CLICK_BLOCK); f.manager.openNode(event); verify(event, never()).setCancelled(true);
        NodeManager.nodes.add(f.node); f.manager.openNode(event); verify(event).setCancelled(true);
        event = f.interact(Action.PHYSICAL); f.manager.openNode(event); verify(event, never()).setCancelled(true);
        NodeManager.nodes.clear(); event = f.interact(Action.RIGHT_CLICK_BLOCK); f.manager.openNode(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void unclaimedNodesRequireALeaderAndSuccessfulClaimChecks() {
        NodeManager.nodes.add(f.node); when(f.node.isClaimable()).thenReturn(true);
        PlayerInteractEvent event = f.interact(Action.RIGHT_CLICK_BLOCK);
        f.manager.openNode(event); verify(f.player).sendMessage(contains("Must be a guild leader"));
        f.factions.when(() -> FactionManager.getGuildByLeader("Leader")).thenReturn(f.guild);
        f.manager.openNode(event); verify(f.node, never()).setGuild(any());
        when(f.node.canClaim(f.player, f.guild)).thenReturn(true); f.manager.openNode(event);
        verify(f.node).setGuild(f.guild); verify(f.node).update();
        verify(f.lastInventory()).nodeView(f.player, f.node); assertSame(f.node, f.manager.currentNode.get(f.player));
    }

    @Test
    void openingOwnedNodesRefreshesTheirViewAndRemovesOrphans() {
        NodeManager.nodes.add(f.node); PlayerInteractEvent event = f.interact(Action.RIGHT_CLICK_BLOCK);
        f.manager.openNode(event); verify(f.node).update(); verify(f.lastInventory()).nodeView(f.player, f.node);
        assertSame(f.node, f.manager.currentNode.get(f.player));
        when(f.node.hasGuild()).thenReturn(false); f.manager.openNode(event);
        verify(f.node).breakNode(); assertTrue(NodeManager.nodes.isEmpty());
        verify(f.player).sendMessage(contains("no guild"));
    }

    @Test
    void unrelatedInventoryClicksAreIgnored() {
        InventoryClickEvent event = f.click("unrelated", 0); f.manager.currentNode.clear();
        f.manager.invenClick(event); verify(event, never()).setCancelled(true);
        f.manager.currentNode.put(f.player, f.node); f.manager.invenClick(event);
        verify(event, never()).setCancelled(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"§7Iron Node", "§7Iron Node: Fuel", "§7Iron Node: Type", "§7Confirm Action"})
    void everyManagedMenuCancelsBottomInventoryClicksAndRemovesOrphanNodes(String title) {
        f.manager.currentSlot.put(f.player, f.slot);
        InventoryClickEvent event = f.click(title, 0); when(event.getClickedInventory()).thenReturn(mock(Inventory.class));
        f.manager.invenClick(event); verify(event).setCancelled(true); verify(f.node, never()).breakNode();
        when(f.node.hasGuild()).thenReturn(false); NodeManager.nodes.add(f.node);
        event = f.click(title, 0); f.manager.invenClick(event);
        verify(f.node).breakNode(); verify(f.player).closeInventory(); assertTrue(NodeManager.nodes.isEmpty());
    }

    @Test
    void mainMenuRejectsOtherGuildMembersAndLetsAdminsProceed() {
        f.factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(null);
        f.manager.invenClick(f.click("§7Iron Node", 9));
        verify(f.player).sendMessage("§cCannot change another guild's node");
        verify(f.lastInventory(), never()).typeView(any(), any());
        when(f.player.hasPermission("dowsing.admin")).thenReturn(true);
        f.manager.invenClick(f.click("§7Iron Node", 9)); verify(f.lastInventory()).typeView(f.player, f.node);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9, 26, 18, 10})
    void activeNodesRejectMenuChangesThatRequireStopping(int slot) {
        when(f.node.getIsActive()).thenReturn(true);
        f.manager.invenClick(f.click("§7Iron Node", slot));
        verify(f.player).sendMessage(startsWith("§cCannot"));
        verify(f.node, never()).setLevel(any()); verify(f.lastInventory(), never()).typeView(any(), any());
    }

    @Test
    void inactiveMainMenuOpensTypeSlotDeletionAndTransferActions() {
        f.manager.invenClick(f.click("§7Iron Node", 9)); verify(f.lastInventory()).typeView(f.player, f.node);
        f.manager.invenClick(f.click("§7Iron Node", 10)); verify(f.lastInventory()).slotView(f.player, f.node, f.slot);
        assertSame(f.slot, f.manager.currentSlot.get(f.player));
        f.manager.invenClick(f.click("§7Iron Node", 18)); assertEquals(ConfirmType.DELETE_NODE, f.manager.confirm.get(f.player));
        verify(f.lastInventory()).confirmView(f.player);
        f.manager.invenClick(f.click("§7Iron Node", 6)); verify(f.node).setGuild(null);
        verify(f.player).closeInventory(); verify(f.player).sendMessage("§aNode set as claimable");
        f.manager.invenClick(f.click("§7Iron Node", 11));
    }

    @Test
    void protectedNodesCannotBeDeletedOrTransferredWithoutTheRequiredOverride() {
        when(f.block.isBreakable()).thenReturn(false); when(f.block.isTransferable()).thenReturn(false);
        f.manager.invenClick(f.click("§7Iron Node", 18)); assertTrue(f.manager.confirm.isEmpty());
        f.manager.invenClick(f.click("§7Iron Node", 6)); verify(f.node, never()).setGuild(any());
        when(f.player.hasPermission("simplefactions.admin")).thenReturn(true);
        f.manager.invenClick(f.click("§7Iron Node", 18)); assertEquals(ConfirmType.DELETE_NODE, f.manager.confirm.get(f.player));
    }

    @Test
    void statusButtonConfirmsStoppingRetriesRefundsAndReportsActivationOutcome() {
        when(f.node.getIsActive()).thenReturn(true);
        f.manager.invenClick(f.click("§7Iron Node", 17)); assertEquals(ConfirmType.DEACTIVATE, f.manager.confirm.get(f.player));
        verify(f.lastInventory()).confirmView(f.player);
        when(f.node.getIsActive()).thenReturn(false); when(f.node.hasPendingRefund()).thenReturn(true);
        f.manager.invenClick(f.click("§7Iron Node", 17)); verify(f.player).sendMessage(contains("Refund still pending"));
        when(f.node.hasPendingRefund()).thenReturn(true, false);
        f.manager.invenClick(f.click("§7Iron Node", 17)); verify(f.player).sendMessage(contains("Refund complete"));
        when(f.node.hasPendingRefund()).thenReturn(false);
        f.manager.invenClick(f.click("§7Iron Node", 17)); verify(f.node).activate();
        doAnswer(inv -> { when(f.node.getIsActive()).thenReturn(true); return null; }).when(f.node).activate();
        f.manager.invenClick(f.click("§7Iron Node", 17));
        f.toggles.verify(() -> NodeToggleLog.activated(f.player, f.node));
        verify(f.lastInventory()).updateNodeView(f.player, f.node, f.inventory);
    }

    @Test
    void capacityPurchasesValidateTheLimitBankAndFundsBeforeCharging() {
        when(f.node.getNodeCapacityCost()).thenReturn(25.0);
        when(f.guild.getBank()).thenReturn(null);
        f.manager.invenClick(f.click("§7Iron Node", 24)); verify(f.player).sendMessage("§cNo bank");
        when(f.guild.getBank()).thenReturn(f.bank); when(f.bank.getWealth()).thenReturn(10.0);
        f.manager.invenClick(f.click("§7Iron Node", 24)); verify(f.player).sendMessage("§cNot enough funds");
        when(f.bank.getWealth()).thenReturn(100.0);
        f.manager.invenClick(f.click("§7Iron Node", 24)); verify(f.bank).withdraw(25.0);
        assertEquals(1, NodeManager.getExtraCapacity(f.guild));
        net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity = 1;
        InventoryClickEvent event = f.click("§7Iron Node", 24); when(event.getCurrentItem()).thenReturn(new ItemStack(Material.NETHER_STAR));
        f.manager.invenClick(event); verify(f.player).sendMessage(contains("maximum extra capacity"));
        f.manager.invenClick(f.click("§7Iron Node", 24)); verify(f.bank, times(1)).withdraw(25.0);
    }

    @Test
    void purchasedCapacityAppliesWithMemberCapacityDisabledAndStopsAtThePurchaseLimit() {
        Cache.extraCapacity = false; Cache.membersPerCapacity = -1;
        net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity = 2;
        when(f.node.getNodeCapacityCost()).thenReturn(25.0);
        assertEquals(1, NodeManager.getNodeCapacity(f.guild));
        f.manager.invenClick(f.click("§7Iron Node", 24));
        assertEquals(2, NodeManager.getNodeCapacity(f.guild));
        f.manager.invenClick(f.click("§7Iron Node", 24));
        assertEquals(3, NodeManager.getNodeCapacity(f.guild));
        assertFalse(NodeManager.canPurchaseCapacity(f.guild));
        f.manager.invenClick(f.click("§7Iron Node", 24));
        assertEquals(3, NodeManager.getNodeCapacity(f.guild));
        verify(f.bank, times(2)).withdraw(25.0);
    }

    @Test
    void upgradeAndDowngradeChargeAndRefundTheCorrectLevelAndRefreshTheMenu() {
        f.manager.invenClick(f.click("§7Iron Node", 8)); verify(f.bank).withdraw(20.0);
        verify(f.node).setLevel(2); verify(f.node).update(); verify(f.lastInventory()).updateNodeView(f.player, f.node, f.inventory);
        when(f.node.getLevel()).thenReturn(2);
        f.manager.invenClick(f.click("§7Iron Node", 26)); verify(f.bank).deposit(16.0); verify(f.node).setLevel(1);
        f.manager.upgradeNode(f.player, f.node, f.inventory); verify(f.player).sendMessage(contains("already at max level"));
        when(f.node.getLevel()).thenReturn(1); f.manager.downgradeNode(f.player, f.node, f.inventory);
        verify(f.player).sendMessage(contains("cannot go below level 1"));
        when(f.bank.getWealth()).thenReturn(1.0); f.manager.upgradeNode(f.player, f.node, f.inventory);
        verify(f.player).sendMessage(contains("does not have enough funds"));
        when(f.node.getLevel()).thenReturn(2); when(f.node.getGuild()).thenReturn(null);
        f.manager.downgradeNode(f.player, f.node, f.inventory); verify(f.bank, times(1)).deposit(16.0);
    }

    @Test
    void productionMethodMenuHandlesBackEmptyUnknownAndAlreadySelectedItems() {
        f.manager.currentSlot.put(f.player, f.slot);
        f.manager.invenClick(f.click("§7Iron Node: Fuel", 26)); verify(f.lastInventory()).nodeView(f.player, f.node);
        f.manager.invenClick(f.click("§7Iron Node: Fuel", 0));
        InventoryClickEvent event = f.click("§7Iron Node: Fuel", 0); doReturn(f.item("unknown")).when(event).getCurrentItem();
        f.manager.invenClick(event); verify(f.slot, never()).setActivePm(any());
        f.methods.when(() -> PMLoader.getByItemName("unknown")).thenReturn(f.method);
        f.manager.invenClick(event); verify(f.slot, never()).setActivePm(any());
    }

    @Test
    void methodChangesRequirePrerequisitesAndApplyTheEfficiencyPenalty() {
        f.manager.currentSlot.put(f.player, f.slot);
        ProductionMethod selected = mock(ProductionMethod.class);
        when(selected.getId()).thenReturn("advanced"); when(selected.getPrerequisite()).thenReturn("basic_fuel");
        f.methods.when(() -> PMLoader.getByItemName("Advanced")).thenReturn(selected);
        InventoryClickEvent event = f.click("§7Iron Node: Fuel", 0); doReturn(f.item("Advanced")).when(event).getCurrentItem();
        try (var engines = mockConstruction(NodeEngine.class)) {
            f.manager.invenClick(event); verify(f.player).sendMessage(contains("requires at least"));
            verify(f.slot, never()).setActivePm(any());
        }
        try (var engines = mockConstruction(NodeEngine.class, (engine, context) -> when(engine.checkPrerequisite(selected, f.node)).thenReturn(true))) {
            f.manager.invenClick(event); verify(f.slot).setActivePm(selected);
            verify(f.node).updateEfficiency(-5.0); verify(f.node).update(); verify(f.lastInventory()).nodeView(f.player, f.node);
        }
        when(selected.getPrerequisite()).thenReturn("none"); f.manager.invenClick(event); verify(f.slot, times(2)).setActivePm(selected);
    }

    @Test
    void typeMenuHandlesBackEmptyUnknownAndCurrentTypesAndChecksBiomes() {
        f.manager.invenClick(f.click("§7Iron Node: Type", 26)); verify(f.lastInventory()).nodeView(f.player, f.node);
        f.manager.invenClick(f.click("§7Iron Node: Type", 0));
        InventoryClickEvent event = f.click("§7Iron Node: Type", 0); doReturn(f.item("Mine")).when(event).getCurrentItem();
        f.manager.invenClick(event); assertTrue(f.manager.confirm.isEmpty());
        f.types.when(() -> TypeLoader.getByItemName("Mine")).thenReturn(f.type);
        f.manager.invenClick(event); assertTrue(f.manager.confirm.isEmpty());
        NodeType next = mock(NodeType.class); when(next.getId()).thenReturn("other");
        when(next.getBiomes()).thenReturn(List.of("DESERT", "BADLANDS"));
        f.types.when(() -> TypeLoader.getByItemName("Mine")).thenReturn(next);
        f.manager.invenClick(event); assertTrue(f.manager.confirm.isEmpty());
        verify(f.player).sendMessage("§f- DESERT"); verify(f.player).sendMessage("§f- BADLANDS");
        when(next.getBiomes()).thenReturn(List.of("PLAINS")); f.manager.invenClick(event);
        assertEquals(ConfirmType.CHANGE_TYPE, f.manager.confirm.get(f.player));
        assertSame(next, f.manager.currentType.get(f.player)); verify(f.lastInventory()).confirmView(f.player);
    }

    @Test
    void confirmationMenusSupportCancelAcceptAndAbsentActions() {
        f.manager.invenClick(f.click("§7Confirm Action", 11)); verify(f.node, never()).breakNode();
        f.manager.confirm.put(f.player, ConfirmType.DELETE_NODE);
        f.manager.invenClick(f.click("§7Confirm Action", 15)); verify(f.lastInventory()).nodeView(f.player, f.node);
        f.manager.invenClick(f.click("§7Confirm Action", 0)); verify(f.node, never()).breakNode();
        NodeManager.nodes.add(f.node); f.manager.invenClick(f.click("§7Confirm Action", 11));
        verify(f.node).breakNode(); assertTrue(NodeManager.nodes.isEmpty()); assertFalse(f.manager.confirm.containsKey(f.player));
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "refund"})
    void rejectedConfirmationsStillAllowCancel(String reason) {
        f.manager.confirm.put(f.player, ConfirmType.DELETE_NODE);
        when(f.node.getIsActive()).thenReturn(reason.equals("active"));
        when(f.node.hasPendingRefund()).thenReturn(reason.equals("refund"));
        f.manager.invenClick(f.click("§7Confirm Action", 11));
        verify(f.node, never()).breakNode();
        f.manager.invenClick(f.click("§7Confirm Action", 15));
        verify(f.lastInventory()).nodeView(f.player, f.node);
        assertSame(f.node, f.manager.currentNode.get(f.player));
    }

    @Test
    void confirmedDeactivationAndTypeChangeRefreshTheNodeAndAuditRealStops() {
        when(f.node.getIsActive()).thenReturn(true);
        f.manager.confirm.put(f.player, ConfirmType.DEACTIVATE);
        f.manager.confirmClick(f.player, f.node, ConfirmType.DEACTIVATE);
        verify(f.node).deActivate(); f.toggles.verify(() -> NodeToggleLog.deactivated(f.player, f.node));
        verify(f.lastInventory()).nodeView(f.player, f.node);
        assertFalse(f.manager.confirm.containsKey(f.player));
        when(f.node.getIsActive()).thenReturn(false); f.manager.confirmClick(f.player, f.node, ConfirmType.DEACTIVATE);
        f.toggles.verify(() -> NodeToggleLog.deactivated(f.player, f.node), times(1));
        NodeType selected = mock(NodeType.class); f.manager.currentType.put(f.player, selected);
        f.manager.confirm.put(f.player, ConfirmType.CHANGE_TYPE);
        f.manager.confirmClick(f.player, f.node, ConfirmType.CHANGE_TYPE);
        verify(f.node).setCurrentType(selected); verify(f.node).updateEfficiency(-40.0); verify(f.node).update();
        assertSame(f.node, f.manager.currentNode.get(f.player));
        assertFalse(f.manager.confirm.containsKey(f.player));
    }

    @ParameterizedTest
    @ValueSource(strings = {"§7Iron Node: Fuel", "§7Iron Node: Type", "§7Confirm Action"})
    void staleSubmenusCannotChangeNodesAfterThePlayersGuildMembershipEnds(String title) {
        f.manager.currentSlot.put(f.player, f.slot); f.manager.confirm.put(f.player, ConfirmType.DELETE_NODE);
        f.factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(null);
        InventoryClickEvent event = f.click(title, title.equals("§7Confirm Action") ? 11 : 0);
        doReturn(f.item("Next")).when(event).getCurrentItem();
        ProductionMethod nextMethod = mock(ProductionMethod.class); when(nextMethod.getId()).thenReturn("next");
        when(nextMethod.getPrerequisite()).thenReturn("none"); f.methods.when(() -> PMLoader.getByItemName("Next")).thenReturn(nextMethod);
        NodeType nextType = mock(NodeType.class); when(nextType.getId()).thenReturn("next");
        f.types.when(() -> TypeLoader.getByItemName("Next")).thenReturn(nextType);
        f.manager.invenClick(event);
        verify(f.slot, never()).setActivePm(any()); verify(f.node, never()).breakNode();
        verify(f.lastInventory(), never()).confirmView(any());
        verify(f.player).sendMessage("§cCannot change another guild's node");
    }

    @ParameterizedTest
    @ValueSource(strings = {"§7Iron Node", "§7Iron Node: Fuel", "§7Iron Node: Type", "§7Confirm Action"})
    void pendingRefundsPreventChangesFromEveryMenu(String title) {
        f.manager.currentSlot.put(f.player, f.slot); f.manager.confirm.put(f.player, ConfirmType.DELETE_NODE);
        when(f.node.hasPendingRefund()).thenReturn(true);
        f.manager.invenClick(f.click(title, title.equals("§7Confirm Action") ? 11 : 0));
        verify(f.player).sendMessage(contains("Retry Refund"));
        verify(f.node, never()).breakNode(); verify(f.node, never()).setCurrentType(any());
        verify(f.slot, never()).setActivePm(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"§7Iron Node: Fuel", "§7Iron Node: Type", "§7Confirm Action"})
    void foreignGuildViewersCanStillReturnFromOrCancelTheMenu(String title) {
        f.manager.currentSlot.put(f.player, f.slot); f.manager.confirm.put(f.player, ConfirmType.DELETE_NODE);
        f.factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(null);
        f.manager.invenClick(f.click(title, title.equals("§7Confirm Action") ? 15 : 26));
        verify(f.lastInventory()).nodeView(f.player, f.node);
        verify(f.node, never()).breakNode(); verify(f.player, never()).sendMessage(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"method", "type", "delete", "confirm-type"})
    void staleMenusCannotMutateANodeActivatedByAnotherPlayer(String action) {
        when(f.node.getIsActive()).thenReturn(true);
        f.manager.currentSlot.put(f.player, f.slot);
        f.manager.currentType.put(f.player, mock(NodeType.class));
        f.manager.confirm.put(f.player, action.equals("delete") ? ConfirmType.DELETE_NODE : ConfirmType.CHANGE_TYPE);
        String title = action.equals("method") ? "§7Iron Node: Fuel" : action.equals("type") ? "§7Iron Node: Type" : "§7Confirm Action";
        InventoryClickEvent event = f.click(title, title.equals("§7Confirm Action") ? 11 : 0);
        doReturn(f.item("Next")).when(event).getCurrentItem();
        ProductionMethod nextMethod = mock(ProductionMethod.class); when(nextMethod.getId()).thenReturn("next");
        when(nextMethod.getPrerequisite()).thenReturn("none"); f.methods.when(() -> PMLoader.getByItemName("Next")).thenReturn(nextMethod);
        NodeType nextType = mock(NodeType.class); when(nextType.getId()).thenReturn("next");
        f.types.when(() -> TypeLoader.getByItemName("Next")).thenReturn(nextType);
        f.manager.invenClick(event);
        verify(f.slot, never()).setActivePm(any()); verify(f.node, never()).breakNode();
        verify(f.node, never()).setCurrentType(any()); verify(f.node, never()).update();
        verify(f.lastInventory(), never()).confirmView(any());
        verify(f.player).sendMessage(contains("active"));
    }

    static final class Fixture implements AutoCloseable {
        final Map<Field, Object> previousCache = new HashMap<>();
        final List<Node> previousNodes = NodeManager.nodes;
        final HashMap<String, Integer> previousCapacity = NodeManager.extraCapacityByGuild;
        final DowsingMain previousPlugin = DowsingMain.plugin;
        final int previousMaxCapacity = net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity;
        final ServerMock server;
        final NodeManager manager = new NodeManager();
        final Guild guild = mock(Guild.class);
        final net.tfminecraft.simplefactions.objects.Bank bank = mock(net.tfminecraft.simplefactions.objects.Bank.class);
        final World world = mock(World.class);
        final Chunk chunk = mock(Chunk.class);
        final Block worldBlock = mock(Block.class);
        final Location location = new Location(world, 2, 64, 3);
        final Player player = mock(Player.class);
        final NodeBlock block = mock(NodeBlock.class);
        final NodeType type = mock(NodeType.class);
        final NodeSlot slot = mock(NodeSlot.class);
        final ProductionMethod method = mock(ProductionMethod.class);
        final Inventory inventory = mock(Inventory.class);
        final Logger logger = mock(Logger.class);
        final Node node;
        final MockedStatic<FactionManager> factions;
        final MockedStatic<BlockLoader> blocks;
        final MockedStatic<PMLoader> methods;
        final MockedStatic<TypeLoader> types;
        final MockedStatic<NodeToggleLog> toggles;
        final MockedConstruction<InventoryManager> inventories;

        Fixture() throws Exception {
            for (Field field : Cache.class.getDeclaredFields()) if (Modifier.isStatic(field.getModifiers())) previousCache.put(field, field.get(null));
            server = MockBukkit.mock();
            DowsingMain.plugin = mock(DowsingMain.class);
            when(DowsingMain.plugin.getLogger()).thenReturn(logger); when(DowsingMain.plugin.getServer()).thenReturn(server);
            when(DowsingMain.plugin.getName()).thenReturn("Dowsing"); when(DowsingMain.plugin.isEnabled()).thenReturn(true);
            NodeManager.nodes = new ArrayList<>(); NodeManager.extraCapacityByGuild = new HashMap<>();
            Cache.extraCapacity = false; Cache.naturalYieldEnabled = false; Cache.cycleLength = 60;
            Cache.membersPerCapacity = 2; Cache.maxMemberCapacity = 5; Cache.minMembersForNode = 1;
            Cache.efficiencyLossPM = 5; Cache.efficiencyLossType = 40; Cache.refundPercentage = 0.8;
            net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity = 3;
            when(guild.getId()).thenReturn("guild"); when(guild.getLeader()).thenReturn("Leader");
            when(guild.getMembers()).thenReturn(List.of("Leader", "Member")); when(guild.getBank()).thenReturn(bank);
            when(bank.getWealth()).thenReturn(1000.0); when(player.getName()).thenReturn("Leader"); when(player.getLocation()).thenReturn(location);
            when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(worldBlock); when(world.getBlockAt(any(Location.class))).thenReturn(worldBlock);
            when(worldBlock.getLocation()).thenReturn(location); when(worldBlock.getWorld()).thenReturn(world); when(worldBlock.getType()).thenReturn(Material.BARRIER);
            Biome biome = mock(Biome.class); when(biome.toString()).thenReturn("PLAINS"); when(worldBlock.getBiome()).thenReturn(biome);
            when(block.getResource()).thenReturn("Iron"); when(block.getId()).thenReturn("iron"); when(block.isBreakable()).thenReturn(true); when(block.isTransferable()).thenReturn(true);
            when(type.getId()).thenReturn("mine"); when(slot.getId()).thenReturn("fuel"); when(slot.getSlot()).thenReturn(10);
            when(slot.getActivePm()).thenReturn(method); when(method.getId()).thenReturn("basic"); when(method.getPrerequisite()).thenReturn("none");
            when(type.getSlots()).thenReturn(List.of(slot));
            Level first = mock(Level.class), second = mock(Level.class); when(first.getCost()).thenReturn(10.0); when(second.getCost()).thenReturn(20.0);
            when(type.getLevels()).thenReturn(List.of(first, second));
            node = nodeAt(location);
            factions = mockStatic(FactionManager.class); factions.when(FactionManager::getCopy).thenReturn(new ArrayList<>());
            factions.when(() -> FactionManager.getGuildByMember("Leader")).thenReturn(guild);
            factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
            blocks = mockStatic(BlockLoader.class); blocks.when(() -> BlockLoader.getByBlock(Material.BARRIER)).thenReturn(block);
            blocks.when(() -> BlockLoader.getByPath("nodes:iron")).thenReturn(block);
            methods = mockStatic(PMLoader.class); types = mockStatic(TypeLoader.class); toggles = mockStatic(NodeToggleLog.class);
            inventories = mockConstruction(InventoryManager.class);
        }

        Node nodeAt(Location at) {
            Node node = mock(Node.class); when(node.getId()).thenReturn(UUID.randomUUID()); when(node.getLoc()).thenReturn(at);
            when(node.getBlock()).thenReturn(block); when(node.getCurrentType()).thenReturn(type); when(node.getGuild()).thenReturn(guild);
            when(node.hasGuild()).thenReturn(true); when(node.getIsActive()).thenReturn(false); when(node.getLevel()).thenReturn(1);
            when(node.getCostIncrease()).thenReturn(1.0); return node;
        }
        Location remoteLocation() {
            World other = mock(World.class); when(other.getChunkAt(any(Location.class))).thenReturn(mock(Chunk.class));
            return new Location(other, 2, 64, 3);
        }
        InventoryManager lastInventory() { return inventories.constructed().getLast(); }
        BlockPlaceEvent place() {
            BlockPlaceEvent event = mock(BlockPlaceEvent.class); when(event.getBlock()).thenReturn(worldBlock); when(event.getPlayer()).thenReturn(player); return event;
        }
        FurniturePlaceSuccessEvent furniturePlace() {
            FurniturePlaceSuccessEvent event = mock(FurniturePlaceSuccessEvent.class); when(event.getNamespacedID()).thenReturn("nodes:iron");
            Entity entity = mock(Entity.class); when(entity.getLocation()).thenReturn(location.clone().add(.5, .5, .5));
            when(event.getBukkitEntity()).thenReturn(entity); when(event.getPlayer()).thenReturn(player); return event;
        }
        PlayerInteractEvent interact(Action action) {
            PlayerInteractEvent event = mock(PlayerInteractEvent.class); when(event.getClickedBlock()).thenReturn(worldBlock);
            when(event.getAction()).thenReturn(action); when(event.getPlayer()).thenReturn(player); return event;
        }
        InventoryClickEvent click(String title, int index) {
            manager.currentNode.put(player, node);
            InventoryClickEvent event = mock(InventoryClickEvent.class); InventoryView view = mock(InventoryView.class);
            when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view); when(view.getTitle()).thenReturn(title);
            when(view.getTopInventory()).thenReturn(inventory); when(event.getClickedInventory()).thenReturn(inventory); when(event.getSlot()).thenReturn(index); return event;
        }
        ItemStack item(String label) {
            ItemStack item = mock(ItemStack.class); ItemMeta meta = mock(ItemMeta.class);
            when(item.getItemMeta()).thenReturn(meta); when(meta.getDisplayName()).thenReturn(label); return item;
        }
        @Override public void close() throws Exception {
            inventories.close(); toggles.close(); types.close(); methods.close(); blocks.close(); factions.close(); MockBukkit.unmock();
            DowsingMain.plugin = previousPlugin; NodeManager.nodes = previousNodes; NodeManager.extraCapacityByGuild = previousCapacity;
            net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity = previousMaxCapacity;
            for (var entry : previousCache.entrySet()) entry.getKey().set(null, entry.getValue());
        }
    }
}
