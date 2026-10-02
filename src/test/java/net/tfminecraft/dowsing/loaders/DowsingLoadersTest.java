package net.tfminecraft.dowsing.loaders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.objects.NodeBlock;
import net.tfminecraft.dowsing.objects.NodeSlot;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.dowsing.objects.ProductionMethod;
import net.tfminecraft.dowsing.utils.ItemCreator;

@SuppressWarnings("deprecation")
class DowsingLoadersTest {
    @TempDir Path temp;
    private final Map<Field, Object> globals = new HashMap<>();
    private List<ProductionMethod> methods;
    private List<NodeSlot> slots;
    private List<NodeType> types;
    private List<NodeBlock> blocks;
    private MockedConstruction<ItemCreator> creators;
    private MockedStatic<Bukkit> bukkit;
    private Logger logger;
    private DowsingMain previousPlugin;
    private Locale previousLocale;

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
        previousLocale = Locale.getDefault();
        logger = mock(Logger.class);
        DowsingMain.plugin = mock(DowsingMain.class);
        when(DowsingMain.plugin.getLogger()).thenReturn(logger);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getLogger).thenReturn(logger);
        creators = mockConstruction(ItemCreator.class, (creator, context) -> {
            when(creator.createMenuItem(any(), anyList(), anyList(), anyString())).thenAnswer(inv -> menu("Fuel"));
            when(creator.createTypeItemConfig(any())).thenAnswer(inv -> menu("Mine"));
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        creators.close(); bukkit.close();
        DowsingMain.plugin = previousPlugin;
        Locale.setDefault(previousLocale);
        PMLoader.clear(); PMLoader.getPMs().addAll(methods);
        SlotLoader.clear(); SlotLoader.getNodeSlots().addAll(slots);
        TypeLoader.clear(); TypeLoader.getNodeTypes().addAll(types);
        BlockLoader.clear(); BlockLoader.getNodeBlocks().addAll(blocks);
        for (var entry : globals.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @Test
    void configurationLoadsAllRuntimeSettings() throws Exception {
        assertNotNull(new Cache());
        new ConfigLoader().loadConfig(file("config.yml", """
                dowsing_item: tools.dowsing_stick
                input_cycle_length: 720
                enable-natural-yields: true
                members_per_node_capacity: 3
                extra-capacity-cost: 1250
                max-extra-capacity-from-members: 5
                refund-amount: 0.6
                min-members-for-node: 4
                efficiency-growth-per-member: 0.2
                max-efficiency-per-member: 30
                efficiency-loss-type: 25
                efficiency-loss-pm: 3
                """));
        assertEquals("tools.dowsing_stick", Cache.dowsingStick);
        assertEquals(720, Cache.cycleLength); assertTrue(Cache.naturalYieldEnabled);
        assertTrue(Cache.extraCapacity); assertEquals(3, Cache.membersPerCapacity);
        assertEquals(1250, Cache.extraCapacityCost); assertEquals(5, Cache.maxMemberCapacity);
        assertEquals(0.6, Cache.refundPercentage); assertEquals(4, Cache.minMembersForNode);
        assertEquals(0.2, Cache.efficiencyGrowthPerMember); assertEquals(30, Cache.maxEfficiencyPerMember);
        assertEquals(25, Cache.efficiencyLossType); assertEquals(3, Cache.efficiencyLossPM);
    }

    @Test
    void configurationClampsMemberDivisorUsesDefaultsAndCanDisableCapacity() throws Exception {
        ConfigLoader loader = new ConfigLoader();
        loader.loadConfig(file("zero.yml", "members_per_node_capacity: 0\n"));
        assertEquals(1, Cache.membersPerCapacity); assertTrue(Cache.extraCapacity);
        assertEquals(1000, Cache.extraCapacityCost); assertEquals(0.8, Cache.refundPercentage);
        assertEquals(1, Cache.minMembersForNode); assertEquals(0.1, Cache.efficiencyGrowthPerMember);
        assertEquals(25, Cache.maxEfficiencyPerMember); assertEquals(40, Cache.efficiencyLossType);
        assertEquals(5, Cache.efficiencyLossPM);
        loader.loadConfig(file("off.yml", "members_per_node_capacity: -1\n"));
        assertFalse(Cache.extraCapacity);
    }

    @Test
    void loadersResolveCopiesByIdsNamesAndBlockProviders() throws Exception {
        loadDefinitions();
        assertEquals(1, PMLoader.getPMs().size()); assertEquals(1, SlotLoader.getNodeSlots().size());
        assertEquals(1, TypeLoader.getNodeTypes().size()); assertEquals(3, BlockLoader.getNodeBlocks().size());
        ProductionMethod method = PMLoader.getByString("BASIC");
        assertNotNull(method); assertNotSame(PMLoader.getPMs().getFirst(), method);
        assertEquals("basic", PMLoader.getByItemName("FUEL").getId());
        assertNull(PMLoader.getByString("absent")); assertNull(PMLoader.getByItemName("absent"));
        NodeSlot slot = SlotLoader.getByString("FUEL");
        assertEquals("basic", slot.getActivePm().getId()); assertNotSame(SlotLoader.getNodeSlots().getFirst(), slot);
        assertNull(SlotLoader.getByString("absent"));
        NodeType type = TypeLoader.getByString("MINE");
        assertEquals("mine", type.getId()); assertNotSame(TypeLoader.getNodeTypes().getFirst(), type);
        assertEquals("mine", TypeLoader.getByItemName("MINE").getId());
        assertNull(TypeLoader.getByString("absent")); assertNull(TypeLoader.getByItemName("absent"));
        NodeBlock block = BlockLoader.getByString("IRON");
        assertEquals("iron", block.getId()); assertNotSame(BlockLoader.getNodeBlocks().get(2), block);
        assertEquals("iron", BlockLoader.getByBlock(Material.IRON_BLOCK).getId());
        assertEquals("custom", BlockLoader.getByPath("NODES:IRON").getId());
        assertNull(BlockLoader.getByString("absent")); assertNull(BlockLoader.getByBlock(Material.DIRT));
        assertNull(BlockLoader.getByPath("nodes:missing"));
        PMLoader.clear(); SlotLoader.clear(); TypeLoader.clear(); BlockLoader.clear();
        assertTrue(PMLoader.getPMs().isEmpty()); assertTrue(SlotLoader.getNodeSlots().isEmpty());
        assertTrue(TypeLoader.getNodeTypes().isEmpty()); assertTrue(BlockLoader.getNodeBlocks().isEmpty());
    }

    @Test
    void productionMethodLookupSkipsUnavailableMenuItems() throws Exception {
        loadDefinitions();
        ProductionMethod missing = new ProductionMethod("unavailable", yaml("item: {}\n"));
        missing.setMenuItem(null);
        PMLoader.getPMs().addFirst(missing);
        assertEquals("basic", PMLoader.getByItemName("Fuel").getId());
        verify(logger).info("[Dowsing] unavailable has no menu item");
    }

    @Test
    void methodAndTypeLoadersSkipInvalidEntriesWhileKeepingValidEntries() throws Exception {
        new PMLoader().loadConfig(file("bad-methods.yml", "invalid: scalar\nvalid:\n  item: {}\n"));
        assertEquals(List.of("valid"), PMLoader.getPMs().stream().map(ProductionMethod::getId).toList());
        verify(logger).warning(startsWith("[Dowsing] Failed to load production method 'invalid':"));
        new TypeLoader().loadConfig(file("bad-types.yml", "invalid: scalar\nvalid:\n  item: {}\n  levels: {}\n"));
        assertEquals(List.of("valid"), TypeLoader.getNodeTypes().stream().map(NodeType::getId).toList());
        verify(logger).warning(startsWith("[Dowsing] Failed to load node type 'invalid':"));
    }

    @Test
    void missingAndMalformedFilesDoNotThrowFromFileLoading() throws Exception {
        File missing = temp.resolve("missing.yml").toFile();
        File malformed = file("broken.yml", "broken: [\n");
        for (File source : List.of(missing, malformed)) {
            assertDoesNotThrow(() -> new ConfigLoader().loadConfig(source));
            assertDoesNotThrow(() -> new PMLoader().loadConfig(source));
            assertDoesNotThrow(() -> new SlotLoader().loadConfig(source));
            assertDoesNotThrow(() -> new TypeLoader().loadConfig(source));
            assertDoesNotThrow(() -> new BlockLoader().loadConfig(source));
        }
        assertTrue(PMLoader.getPMs().isEmpty()); assertTrue(SlotLoader.getNodeSlots().isEmpty());
        assertTrue(TypeLoader.getNodeTypes().isEmpty()); assertTrue(BlockLoader.getNodeBlocks().isEmpty());
    }

    @Test
    void vanillaBlockPathsResolveIndependentlyOfTheServersLocale() throws Exception {
        loadDefinitions();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertEquals("iron", BlockLoader.getByBlock(Material.IRON_BLOCK).getId());
    }

    private void loadDefinitions() throws Exception {
        new PMLoader().loadConfig(file("methods.yml", "basic:\n  weight: 1\n  item: {}\n"));
        new SlotLoader().loadConfig(file("slots.yml", "fuel:\n  slot: 12\n  production_methods: [basic]\n"));
        new TypeLoader().loadConfig(file("types.yml", """
                mine:
                  name: Mine
                  timer: 20
                  resource: Iron
                  natural-yield-per-yield: 2
                  slots: [fuel]
                  item: {}
                  levels:
                    '1':
                      cost: 10
                      effects: ['yield(1)']
                """));
        new BlockLoader().loadConfig(file("blocks.yml", """
                custom:
                  block: ia.nodes:iron
                  resource: Iron
                  main_types: [mine]
                stone:
                  block: v.STONE
                  resource: Stone
                  main_types: [mine]
                iron:
                  block: v.iron_block
                  resource: Iron
                  main_types: [mine]
                """));
    }

    private File file(String name, String text) throws Exception {
        return Files.writeString(temp.resolve(name), text).toFile();
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration config = new YamlConfiguration(); config.loadFromString(text); return config;
    }

    private static ItemStack menu(String name) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getDisplayName()).thenReturn(name);
        return item;
    }
}
