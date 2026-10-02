package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.objects.Level;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.dowsing.objects.ProductionMethod;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;

@SuppressWarnings("deprecation")
class ItemCreatorTest {
    final Map<Field, Object> previousCache = new HashMap<>();
    Set<String> warnings, previousWarnings;
    MockedStatic<TLibs> tlibs;
    net.tfminecraft.tlibs.objects.api.subapi.ItemCreator provider;
    ItemCreator creator;
    Node node;
    NodeType type;
    List<Level> levels;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        for (Field field : Cache.class.getDeclaredFields()) if (Modifier.isStatic(field.getModifiers())) previousCache.put(field, field.get(null));
        Field field = ItemCreator.class.getDeclaredField("loggedInvalidPaths"); field.setAccessible(true);
        warnings = (Set<String>) field.get(null); previousWarnings = new HashSet<>(warnings); ItemCreator.clearInvalidPathWarnings();
        MockBukkit.mock(); Cache.efficiencyLossPM = 5; Cache.efficiencyLossType = 40;
        tlibs = mockStatic(TLibs.class); ItemAPI api = mock(ItemAPI.class);
        provider = mock(net.tfminecraft.tlibs.objects.api.subapi.ItemCreator.class);
        tlibs.when(TLibs::getItemAPI).thenReturn(api); when(api.getCreator()).thenReturn(provider);
        when(provider.getItemFromPath("v.STONE")).thenAnswer(inv -> new ItemStack(Material.STONE));
        when(provider.getItemFromPath("named")).thenAnswer(inv -> named(Material.DIAMOND, "Gem"));
        creator = new ItemCreator(); node = mock(Node.class); type = mock(NodeType.class);
        when(node.getCurrentType()).thenReturn(type); when(node.getLevel()).thenReturn(1); when(node.getNaturalYield()).thenReturn(4);
        when(node.getExtraction()).thenReturn(2); when(node.getMultiplier()).thenReturn(3); when(node.getModifiedTime()).thenReturn(60);
        when(node.getYield()).thenReturn(5); when(node.getDailyUpkeep()).thenReturn(1.25);
        when(type.getTimer()).thenReturn(60); when(type.getName()).thenReturn("Mine"); when(type.getResource()).thenReturn("iron");
        when(type.getTimeNaturalYield()).thenReturn(-2.0); when(type.getYieldNaturalYield()).thenReturn(2);
        when(type.getMenuItem()).thenAnswer(inv -> new ItemStack(Material.PAPER));
        levels = new ArrayList<>(List.of(level(List.of()), level(List.of()))); when(type.getLevels()).thenReturn(levels);
    }

    @AfterEach void tearDown() throws Exception {
        tlibs.close(); MockBukkit.unmock(); warnings.clear(); warnings.addAll(previousWarnings);
        for (var entry : previousCache.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @Test
    void itemResolutionRejectsBlankPathsAndWarnsOncePerBadProviderPath() {
        assertNull(creator.getItemFromPath(null)); assertNull(creator.getItemFromPath(" "));
        assertEquals(Material.STONE, creator.getItemFromPath("v.STONE").getType());
        when(provider.getItemFromPath("bad")).thenThrow(new IllegalArgumentException("bad syntax"));
        List<String> messages = new ArrayList<>(); Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { messages.add(record.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        Bukkit.getLogger().addHandler(handler);
        try {
            assertNull(creator.getItemFromPath("bad")); assertNull(creator.getItemFromPath("bad"));
            assertEquals(List.of("[Dowsing] Invalid item path: bad (bad syntax)"), messages);
            ItemCreator.clearInvalidPathWarnings(); assertNull(creator.getItemFromPath("bad")); assertEquals(2, messages.size());
        } finally { Bukkit.getLogger().removeHandler(handler); }
    }

    @Test
    void configuredProductionMenuItemsUseFallbacksAndRenderEffectsCostsAndPrerequisites() throws Exception {
        YamlConfiguration config = yaml("material: named\nname: Advanced\nmodel_data: 123\n");
        ItemStack result = creator.createMenuItem(config, List.of("yield(2)"), List.of("v.STONE(2)"), "basic_fuel");
        assertEquals(Material.DIAMOND, result.getType()); assertEquals("Advanced", result.getItemMeta().getDisplayName());
        assertEquals(123, result.getItemMeta().getCustomModelData());
        List<String> lore = result.getItemMeta().getLore();
        assertTrue(lore.contains("§eBase Yield: §a+2")); assertTrue(lore.contains("§7Cost:"));
        assertTrue(lore.contains("§fSTONE x2")); assertTrue(lore.stream().anyMatch(s -> s.contains("Requires at least")));
        assertTrue(lore.contains("§cEfficiency: §4-5.0%"));
        Cache.efficiencyLossPM = 0;
        ItemStack fallback = creator.createMenuItem(yaml("material: missing\nname: Basic\n"), List.of(), List.of(), "none");
        assertEquals(Material.DIRT, fallback.getType()); assertEquals(List.of("§7No Effects", "§7No Cost"), fallback.getItemMeta().getLore());
    }

    @Test
    void configuredMenuItemsDoNotMutateTheProviderTemplate() throws Exception {
        ItemStack template = named(Material.DIAMOND, "Template"); when(provider.getItemFromPath("shared")).thenReturn(template);
        ItemStack menu = creator.createMenuItem(yaml("material: shared\nname: Node\n"), List.of(), List.of(), "none");
        assertNotSame(template, menu); assertEquals("Template", template.getItemMeta().getDisplayName());
        ItemStack typeItem = creator.createTypeItemConfig(yaml("material: shared\n"));
        assertNotSame(template, typeItem); assertFalse(typeItem.getItemMeta().hasLore());
        assertEquals(Material.DIRT, creator.createTypeItemConfig(yaml("material: unavailable\n")).getType());
    }

    @Test
    void liveProductionMenusUseTheInputMultiplierAndSkipExtractionOnEmptyLand() {
        ProductionMethod pm = mock(ProductionMethod.class);
        ItemStack template = named(Material.PAPER, "Method"); when(pm.getMenuItem()).thenReturn(template);
        when(pm.getEffects()).thenReturn(List.of("yield(3)", "extraction(2)"));
        when(pm.getInputs()).thenReturn(List.of("v.STONE(2)")); when(pm.getPrerequisite()).thenReturn("basic_fuel");
        when(node.getNaturalYield()).thenReturn(0);
        List<String> lore = creator.updateMenuItem(node, pm).getItemMeta().getLore();
        assertTrue(lore.contains("§eBase Yield: §a+3")); assertFalse(lore.stream().anyMatch(s -> s.contains("Extraction")));
        assertTrue(lore.contains("§fSTONE x6")); assertTrue(lore.contains("§7Requires at least: §fBasic Fuel"));
        when(pm.getEffects()).thenReturn(List.of()); when(pm.getInputs()).thenReturn(List.of()); when(pm.getPrerequisite()).thenReturn("none");
        assertEquals(List.of("§7No Effects", "§7No Cost"), creator.updateMenuItem(node, pm).getItemMeta().getLore());
    }

    @Test
    void typeAndNaturalYieldItemsDescribeTheCurrentNode() {
        when(node.getCompleteDrop()).thenReturn(Map.of("Nothing", 1.0, "named", 3.0));
        List<String> lore = creator.createTypeItemNode(node, type, true).getItemMeta().getLore();
        assertTrue(lore.contains("§eTime: §f1h ")); assertTrue(lore.contains("§eYield: §a5"));
        assertTrue(lore.contains("§eTotal Upkeep: §f1.25d/day")); assertTrue(lore.stream().anyMatch(s -> s.contains("75.0%")));
        assertTrue(lore.contains("§cEfficiency: §4-40.0%"));
        when(node.getModifiedTime()).thenReturn(30);
        assertTrue(creator.createTypeItemNode(node, type, false).getItemMeta().getLore().getFirst().contains("from 1h"));
        ItemStack natural = creator.createNaturalYieldItem(node);
        assertEquals(Material.EMERALD, natural.getType()); assertTrue(natural.getItemMeta().getDisplayName().contains("natural yield of §e4"));
        assertTrue(natural.getItemMeta().getLore().contains("§eCurrent Extraction: §f2 §7(max 4)"));
    }

    @Test
    void equalLongCycleDurationsDoNotShowAFalseTimeChange() {
        when(node.getModifiedTime()).thenReturn(Integer.valueOf(300)); when(type.getTimer()).thenReturn(Integer.valueOf(300));
        assertEquals("§eTime: §f5h ", creator.createTypeItemNode(node, type, false).getItemMeta().getLore().getFirst());
    }

    @Test
    void upgradeAndDowngradePreviewsIncludeEveryConfiguredEffectDirection() {
        levels.set(0, level(List.of("yield(2)", "yield_percent(10)", "time_modifier(-5)", "prestige(1)", "extraction(1)", "upkeep(2)", "other(1)")));
        levels.set(1, level(List.of("yield(3)", "yield_percent(20)", "time_modifier(-10)", "prestige(2)", "extraction(2)", "upkeep(3)", "other(2)")));
        List<String> up = creator.getUpgradeDownGradeFormatted(node, "upgrade");
        assertTrue(up.contains("§eBase Yield: §a2->3")); assertTrue(up.contains("§eYield: §a10%->20%"));
        assertTrue(up.contains("§eTime Modifier: §a-5.0%->-10.0%")); assertTrue(up.contains("§9Prestige: §a1->2"));
        assertTrue(up.contains("§eExtraction: §a1->2")); assertTrue(up.contains("§eUpkeep: §c2.0d->3.0d"));
        when(node.getLevel()).thenReturn(2);
        List<String> down = creator.getUpgradeDownGradeFormatted(node, "downgrade");
        assertTrue(down.contains("§eYield: §c20%->10%")); assertTrue(down.contains("§eTime Modifier: §c-10.0%->-5.0%"));
        List<String> unchanged = creator.getUpgradeDownGradeFormatted(node, "upgrade");
        assertTrue(unchanged.contains("§eYield: §f20%")); assertTrue(unchanged.contains("§eTime Modifier: §f-10.0%"));
        when(node.getLevel()).thenReturn(1); when(node.getNaturalYield()).thenReturn(0);
        assertFalse(creator.getUpgradeDownGradeFormatted(node, "downgrade").stream().anyMatch(s -> s.contains("Extraction")));
        levels.set(0, level(List.of())); levels.set(1, level(List.of()));
        assertEquals(List.of("§eBase Yield: §f0", "§eTime Modifier: §f0.0%"), creator.getUpgradeDownGradeFormatted(node, "other"));
    }

    @Test
    void numericComparisonHelpersColorEachDirectionAndRecognizeEqualLargeValues() {
        assertEquals("Value: §f1", creator.oldNewInteger(1, 1, "Value", false));
        assertEquals("Value: §a1->2", creator.oldNewInteger(1, 2, "Value", false));
        assertEquals("Value: §c1->2", creator.oldNewInteger(1, 2, "Value", true));
        assertEquals("Value: §c2->1", creator.oldNewInteger(2, 1, "Value", false));
        assertEquals("Value: §a2->1", creator.oldNewInteger(2, 1, "Value", true));
        assertEquals("Value: §f1.0", creator.oldNewUpkeep(1.0, 1.0, "Value", false));
        assertEquals("Value: §a1.0d->2.0d", creator.oldNewUpkeep(1.0, 2.0, "Value", false));
        assertEquals("Value: §c1.0d->2.0d", creator.oldNewUpkeep(1.0, 2.0, "Value", true));
        assertEquals("Value: §c2.0d->1.0d", creator.oldNewUpkeep(2.0, 1.0, "Value", false));
        assertEquals("Value: §a2.0d->1.0d", creator.oldNewUpkeep(2.0, 1.0, "Value", true));
        assertEquals("Value: §f300", creator.oldNewInteger(Integer.valueOf(300), Integer.valueOf(300), "Value", false));
    }

    @Test
    void formattingHandlesTimesPercentagesDropsAndCosts() {
        assertEquals("0m", creator.formatTime(0)); assertEquals("5m ", creator.formatTime(5));
        assertEquals("1h ", creator.formatTime(60)); assertEquals("2h 5m ", creator.formatTime(125));
        assertEquals("+2.0", creator.formatTimeModifier(2.0)); assertEquals("-2.0", creator.formatTimeModifier(-2.0));
        assertEquals("0", creator.formatYieldPercent(null)); assertEquals("2", creator.formatYieldPercent(2.0));
        assertEquals("2.5", creator.formatYieldPercent(2.5));
        assertEquals("§fNothing§f 25.0%", creator.getFormattedDrop("Nothing", 1.0, 4.0));
        assertEquals("§fGem§f 75.0%", creator.getFormattedDrop("named", 3.0, 4.0));
        assertEquals("§fSTONE§f x6", creator.getFormattedCost("v.STONE(2)", 3));
        assertEquals("bad", creator.getFormattedCost("bad", 1));
    }

    @Test
    void formattingCoversEveryEffectAndBothPositiveAndNegativeModifiers() {
        Map<String, String> cases = Map.ofEntries(
                Map.entry("add_drop(Nothing,2)", "§eAdded Drop: §fNothing §7(Weight: 2.0)"),
                Map.entry("add_drop(named,2)", "§eAdded Drop: §fGem §7(Weight: 2.0)"),
                Map.entry("add_drop(bad)", "add_drop(bad)"),
                Map.entry("time_modifier(5)", "§eTime Modifier: §c+5.0%"),
                Map.entry("time_modifier(-5)", "§eTime Modifier: §a-5.0%"),
                Map.entry("yield(2)", "§eBase Yield: §a+2"), Map.entry("yield(-2)", "§eBase Yield: §c-2"),
                Map.entry("yield_percent(2.5)", "§eYield: §a+2.5%"), Map.entry("yield_percent(-2)", "§eYield: §c-2%"),
                Map.entry("prestige(2)", "§9Prestige: §f+2"), Map.entry("prestige(-2)", "§9Prestige: §c-2"),
                Map.entry("extraction(2)", "§eExtraction: §f+2"), Map.entry("extraction(-2)", "§eExtraction: §c-2"),
                Map.entry("upkeep(1.234)", "§eUpkeep: §f1.23d"), Map.entry("other(2)", "other(2)"));
        cases.forEach((effect, expected) -> assertEquals(expected, creator.getFormattedEffect(effect), effect));
    }

    @Test
    void fractionalPrestigeAcceptedByProductionAlsoWorksInItsMenuAndUpgradePreview() {
        assertEquals("§9Prestige: §f+0.5", creator.getFormattedEffect("prestige(0.5)"));
        levels.set(0, level(List.of("prestige(0.5)"))); levels.set(1, level(List.of("prestige(1.25)")));
        assertTrue(creator.getUpgradeDownGradeFormatted(node, "upgrade").stream().anyMatch(s -> s.contains("0.5->1.25")));
    }

    @Test
    void itemNamesUseProviderNamesMagicLabelsAndUsefulFallbacks() {
        assertEquals("", creator.getItemName(null)); assertEquals("", creator.getItemName(" "));
        assertEquals("Nothing", creator.getItemName("nothing")); assertEquals("Gem", creator.getItemName("named"));
        assertEquals("STONE", creator.getItemName("v.STONE")); assertEquals("Missing Item", creator.getItemName("v.missing_item"));
        when(provider.getItemFromPath("plain")).thenAnswer(inv -> new ItemStack(Material.DIAMOND));
        assertEquals("Diamond", creator.getItemName("plain")); assertEquals("missing", creator.getItemName("missing"));
        try (var names = mockStatic(ArtifactDropNames.class)) {
            names.when(() -> ArtifactDropNames.isMagicPath("magic.artifact")).thenReturn(true);
            names.when(() -> ArtifactDropNames.label("magic.artifact")).thenReturn("Random Artifact");
            assertEquals("Random Artifact", creator.getItemName("magic.artifact"));
        }
    }

    private static Level level(List<String> effects) { Level level = mock(Level.class); when(level.getEffects()).thenReturn(effects); return level; }
    private static YamlConfiguration yaml(String text) throws Exception { YamlConfiguration config = new YamlConfiguration(); config.loadFromString(text); return config; }
    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); item.setItemMeta(meta); return item;
    }
}
