package net.tfminecraft.dowsing.objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.loaders.BlockLoader;
import net.tfminecraft.dowsing.loaders.PMLoader;
import net.tfminecraft.dowsing.loaders.SlotLoader;
import net.tfminecraft.dowsing.loaders.TypeLoader;
import net.tfminecraft.dowsing.utils.ItemCreator;
import net.tfminecraft.dowsing.utils.Sorter;

class ConfiguredModelsTest {
    private final Map<Field, Object> globals = new HashMap<>();
    private List<ProductionMethod> methods;
    private List<NodeSlot> slots;
    private List<NodeType> types;
    private List<NodeBlock> blocks;
    private MockedConstruction<ItemCreator> creators;
    private MockedStatic<Bukkit> bukkit;
    private Logger logger;
    private ItemStack menu;
    private DowsingMain previousPlugin;

    @BeforeEach
    void setUp() throws Exception {
        methods = new ArrayList<>(PMLoader.getPMs());
        slots = new ArrayList<>(SlotLoader.getNodeSlots());
        types = new ArrayList<>(TypeLoader.getNodeTypes());
        blocks = new ArrayList<>(BlockLoader.getNodeBlocks());
        PMLoader.clear(); SlotLoader.clear(); TypeLoader.clear(); BlockLoader.clear();
        for (Field field : Cache.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) globals.put(field, field.get(null));
        }
        previousPlugin = DowsingMain.plugin;
        logger = mock(Logger.class);
        DowsingMain.plugin = mock(DowsingMain.class);
        when(DowsingMain.plugin.getLogger()).thenReturn(logger);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getLogger).thenReturn(logger);
        menu = mock(ItemStack.class);
        creators = mockConstruction(ItemCreator.class, (creator, context) -> {
            when(creator.createMenuItem(any(), anyList(), anyList(), anyString())).thenReturn(menu);
            when(creator.createTypeItemConfig(any())).thenReturn(menu);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        creators.close(); bukkit.close();
        DowsingMain.plugin = previousPlugin;
        PMLoader.clear(); PMLoader.getPMs().addAll(methods);
        SlotLoader.clear(); SlotLoader.getNodeSlots().addAll(slots);
        TypeLoader.clear(); TypeLoader.getNodeTypes().addAll(types);
        BlockLoader.clear(); BlockLoader.getNodeBlocks().addAll(blocks);
        for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @Test
    void productionMethodLoadsOptionalFieldsAndCopiesItsConfiguration() throws Exception {
        ProductionMethod method = method("advanced", 7);
        assertEquals("advanced", method.getId());
        assertEquals(7, method.getWeight());
        assertEquals(List.of("yield(2)"), method.getEffects());
        assertEquals(List.of("v.COAL(3)"), method.getInputs());
        assertEquals("basic", method.getPrerequisite());
        assertSame(menu, method.getMenuItem());
        ProductionMethod copy = new ProductionMethod(method);
        assertEquals(method.getId(), copy.getId());
        assertEquals(method.getWeight(), copy.getWeight());
        assertEquals(method.getEffects(), copy.getEffects());
        assertEquals(method.getInputs(), copy.getInputs());
        assertEquals(method.getPrerequisite(), copy.getPrerequisite());
        assertSame(menu, copy.getMenuItem());
        ItemStack replacement = mock(ItemStack.class);
        copy.setId("custom"); copy.setWeight(9); copy.setMenuItem(replacement);
        copy.setEffects(List.of("upkeep(5)")); copy.setInputs(List.of("v.STONE(1)"));
        copy.setPrerequisite("advanced");
        assertAll(() -> assertEquals("custom", copy.getId()),
                () -> assertEquals(9, copy.getWeight()), () -> assertSame(replacement, copy.getMenuItem()),
                () -> assertEquals(List.of("upkeep(5)"), copy.getEffects()),
                () -> assertEquals(List.of("v.STONE(1)"), copy.getInputs()),
                () -> assertEquals("advanced", copy.getPrerequisite()));
        ProductionMethod defaults = new ProductionMethod("basic", yaml("item: {}\n"));
        assertEquals(0, defaults.getWeight());
        assertEquals(List.of(), defaults.getEffects());
        assertEquals(List.of(), defaults.getInputs());
        assertEquals("none", defaults.getPrerequisite());
    }

    @Test
    void slotsSortProductionMethodsAndStartCopiesAtTheDefaultMethod() throws Exception {
        PMLoader.getPMs().add(method("advanced", 10));
        PMLoader.getPMs().add(method("basic", 1));
        NodeSlot slot = slot();
        assertEquals("fuel", slot.getId());
        assertEquals(12, slot.getSlot());
        assertEquals(List.of("basic", "advanced"), slot.getPms().stream().map(ProductionMethod::getId).toList());
        assertSame(slot.getPms().getFirst(), slot.getActivePm());
        slot.setActivePm(slot.getPms().getLast());
        NodeSlot copy = new NodeSlot(slot);
        assertEquals("fuel", copy.getId());
        assertEquals(12, copy.getSlot());
        assertNotSame(slot.getPms(), copy.getPms());
        assertNotSame(slot.getPms().getFirst(), copy.getPms().getFirst());
        assertEquals("basic", copy.getActivePm().getId());
        copy.setId("engine"); copy.setSlot(14); copy.setPms(new ArrayList<>(slot.getPms()));
        copy.setActivePm(copy.getPms().getLast());
        assertEquals("engine", copy.getId()); assertEquals(14, copy.getSlot());
        assertEquals(2, copy.getPms().size()); assertEquals("advanced", copy.getActivePm().getId());
    }

    @Test
    void levelsLoadCopyAndAllowIndependentScalarUpdates() throws Exception {
        Level level = new Level("2", yaml("cost: 12.5\neffects: ['yield(3)']\n"));
        assertEquals(2, level.getLevel()); assertEquals(12.5, level.getCost());
        assertEquals(List.of("yield(3)"), level.getEffects());
        Level copy = new Level(level);
        assertEquals(2, copy.getLevel()); assertEquals(12.5, copy.getCost());
        assertEquals(level.getEffects(), copy.getEffects());
        copy.setLevel(3); copy.setCost(25.0); copy.setEffects(List.of("yield(4)"));
        assertEquals(3, copy.getLevel()); assertEquals(25.0, copy.getCost());
        assertEquals(List.of("yield(4)"), copy.getEffects());
        assertEquals(2, level.getLevel()); assertEquals(12.5, level.getCost());
    }

    @Test
    void typeLoadsAndExposesItsConfigurationAndOverrides() throws Exception {
        NodeType type = type(true);
        assertEquals("mine", type.getId()); assertEquals("Mine", type.getName());
        assertEquals(60, type.getTimer()); assertEquals("Iron", type.getResource());
        assertEquals(2, type.getYieldNaturalYield()); assertEquals(-3.0, type.getTimeNaturalYield());
        assertSame(menu, type.getMenuItem()); assertEquals(2, type.getMaxLevel());
        assertEquals(1, type.getSlots().size()); assertEquals(2, type.getLevels().size());
        assertEquals(List.of("v.IRON_INGOT(100)"), type.getDrops());
        assertEquals(List.of("PLAINS"), type.getBiomes());
        verify(logger).info("[PLAINS]");
        NodeType copy = new NodeType(type);
        assertEquals(type.getId(), copy.getId()); assertEquals(type.getName(), copy.getName());
        assertEquals(type.getTimer(), copy.getTimer()); assertEquals(type.getResource(), copy.getResource());
        assertEquals(type.getYieldNaturalYield(), copy.getYieldNaturalYield());
        assertEquals(type.getTimeNaturalYield(), copy.getTimeNaturalYield());
        assertSame(menu, copy.getMenuItem()); assertEquals(type.getMaxLevel(), copy.getMaxLevel());
        assertEquals(type.getDrops(), copy.getDrops()); assertEquals(type.getBiomes(), copy.getBiomes());
        assertNotSame(type.getSlots().getFirst(), copy.getSlots().getFirst());
        ItemStack replacement = mock(ItemStack.class);
        copy.setId("forge"); copy.setName("Forge"); copy.setTimer(20); copy.setResource("Gold");
        copy.setYieldNaturalYield(4); copy.setTimeNaturalYield(-1.0); copy.setMenuItem(replacement);
        copy.setMaxLevel(3); copy.setDrops(List.of()); copy.setBiomes(List.of());
        copy.setSlots(List.of()); copy.setLevels(List.of());
        assertEquals("forge", copy.getId()); assertEquals("Forge", copy.getName());
        assertEquals(20, copy.getTimer()); assertEquals("Gold", copy.getResource());
        assertEquals(4, copy.getYieldNaturalYield()); assertEquals(-1.0, copy.getTimeNaturalYield());
        assertSame(replacement, copy.getMenuItem()); assertEquals(3, copy.getMaxLevel());
        assertTrue(copy.getDrops().isEmpty()); assertTrue(copy.getBiomes().isEmpty());
        assertTrue(copy.getSlots().isEmpty()); assertTrue(copy.getLevels().isEmpty());
        NodeType defaults = type(false);
        assertTrue(defaults.getDrops().isEmpty()); assertTrue(defaults.getBiomes().isEmpty());
    }

    @Test
    void copiedTypeLevelEditsDoNotMutateTheTemplateOrOtherNodes() throws Exception {
        NodeType template = type(false);
        NodeType firstNode = new NodeType(template);
        NodeType secondNode = new NodeType(template);
        firstNode.getLevels().getFirst().setCost(999.0);
        assertEquals(10.0, template.getLevels().getFirst().getCost());
        assertEquals(10.0, secondNode.getLevels().getFirst().getCost());
        firstNode.getLevels().removeLast();
        assertEquals(2, template.getLevels().size());
        assertEquals(2, secondNode.getLevels().size());
    }

    @Test
    void blockLoadsDefaultsAndSpecialPropertiesAndCopiesNestedTypes() throws Exception {
        TypeLoader.getNodeTypes().add(type(false));
        NodeBlock block = new NodeBlock("iron", yaml("""
                block: ia.nodes:iron
                resource: Iron
                main_types: [mine]
                breakable: false
                transferable: false
                special: true
                tier: 3
                title: Ancient Mine
                """));
        assertEquals("iron", block.getId()); assertEquals("ia.nodes:iron", block.getBlock());
        assertEquals("Iron", block.getResource()); assertEquals("mine", block.getTypes().getFirst().getId());
        assertFalse(block.isBreakable()); assertFalse(block.isTransferable()); assertTrue(block.isSpecial());
        assertEquals(3, block.getTier()); assertTrue(block.hasTitle()); assertEquals("Ancient Mine", block.getTitle());
        NodeBlock copy = new NodeBlock(block);
        assertEquals(block.getId(), copy.getId()); assertEquals(block.getBlock(), copy.getBlock());
        assertEquals(block.getResource(), copy.getResource());
        assertNotSame(block.getTypes().getFirst(), copy.getTypes().getFirst());
        assertFalse(copy.isBreakable()); assertFalse(copy.isTransferable()); assertTrue(copy.isSpecial());
        assertEquals(3, copy.getTier()); assertEquals("Ancient Mine", copy.getTitle());
        copy.setId("gold"); copy.setBlock("v.GOLD_BLOCK"); copy.setResource("Gold"); copy.setTypes(List.of());
        assertEquals("gold", copy.getId()); assertEquals("v.GOLD_BLOCK", copy.getBlock());
        assertEquals("Gold", copy.getResource()); assertTrue(copy.getTypes().isEmpty());
        NodeBlock defaults = new NodeBlock("plain", yaml("block: v.STONE\n"));
        assertTrue(defaults.isBreakable()); assertTrue(defaults.isTransferable());
        assertFalse(defaults.isSpecial()); assertEquals(0, defaults.getTier());
        assertFalse(defaults.hasTitle()); assertNull(new NodeBlock(defaults).getTitle());
    }

    @Test
    void sorterReturnsTheSameListOrderedByWeight() throws Exception {
        ProductionMethod a = method("a", 8), b = method("b", 2), c = method("c", 8);
        List<ProductionMethod> values = new ArrayList<>(List.of(a, b, c));
        assertSame(values, new Sorter().sortPMsByWeight(values));
        assertEquals(List.of(b, a, c), values);
    }

    @Test
    void sorterIdentifiesWhicheverComparedMethodHasNoWeight() throws Exception {
        ProductionMethod missing = method("missing", 0), valid = method("valid", 1);
        missing.setWeight(null);
        new Sorter().sortPMsByWeight(new ArrayList<>(List.of(valid, missing)));
        new Sorter().sortPMsByWeight(new ArrayList<>(List.of(missing, valid)));
        verify(logger, times(2)).info("[Dowsing] missing has no weight");
        verify(logger, never()).info("[Dowsing] valid has no weight");
    }

    private ProductionMethod method(String id, int weight) throws Exception {
        return new ProductionMethod(id, yaml("weight: " + weight + "\neffects: ['yield(2)']\ncost: ['v.COAL(3)']\nprerequisite: basic\nitem: {}\n"));
    }

    private NodeSlot slot() throws Exception {
        return new NodeSlot("fuel", yaml("slot: 12\nproduction_methods: [advanced, basic]\n"));
    }

    private NodeType type(boolean optional) throws Exception {
        if (PMLoader.getPMs().isEmpty()) {
            PMLoader.getPMs().add(method("advanced", 10)); PMLoader.getPMs().add(method("basic", 1));
            SlotLoader.getNodeSlots().add(slot());
        }
        return new NodeType("mine", yaml("""
                name: Mine
                timer: 60
                resource: Iron
                time-reduction-per-natural-yield: -3
                natural-yield-per-yield: 2
                item: {}
                slots: [fuel]
                levels:
                  '1':
                    cost: 10
                    effects: ['yield(1)']
                  '2':
                    cost: 20
                    effects: ['yield(2)']
                """ + (optional ? "drops: ['v.IRON_INGOT(100)']\nbiomes: [PLAINS]\n" : "")));
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }
}
