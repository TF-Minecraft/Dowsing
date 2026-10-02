package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.loaders.*;
import net.tfminecraft.dowsing.managers.NodeManager;
import net.tfminecraft.dowsing.objects.*;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import org.bukkit.*;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

@SuppressWarnings({"unchecked", "deprecation"})
class DatabaseTest {
    @TempDir Path root;
    DowsingMain previous;
    Database db;
    List<Node> previousNodes;
    Map<String,Integer> previousCapacity;
    @BeforeEach void setup() {
        previous = DowsingMain.plugin;
        DowsingMain.plugin = mock(DowsingMain.class);
        when(DowsingMain.plugin.getDataFolder()).thenReturn(root.toFile());
        when(DowsingMain.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        previousNodes = new ArrayList<>(NodeManager.nodes); NodeManager.nodes.clear();
        previousCapacity = new HashMap<>(NodeManager.extraCapacityByGuild); NodeManager.extraCapacityByGuild.clear();
        db = new Database();
    }
    @AfterEach void restore() {
        DowsingMain.plugin = previous;
        NodeManager.nodes.clear(); NodeManager.nodes.addAll(previousNodes);
        NodeManager.extraCapacityByGuild.clear(); NodeManager.extraCapacityByGuild.putAll(previousCapacity);
    }
    Chunk chunk() { Chunk c = mock(Chunk.class); when(c.toString()).thenReturn("Chunk{x=3,z=-2}"); return c; }
    @Test void missingDirectoriesAndEmptyResourceFilesAreSafe() throws Exception {
        Chunk c = chunk();
        assertNull(db.getResource(c)); assertFalse(db.hasResource(c)); assertFalse(db.removeResource("missing"));
        assertDoesNotThrow(db::loadNodes); assertDoesNotThrow(db::deleteDatabase);
        Files.createDirectories(root.resolve("Resources/subdir"));
        Files.writeString(root.resolve("Resources/empty.txt"), "");
        Files.writeString(root.resolve("Resources/header.txt"), c + "\n");
        assertNull(db.getResource(c)); assertFalse(db.hasResource(c));
    }
    @Test void resourceRoundTripUsesConfiguredPluginDirectoryAndReportsRemoval() throws Exception {
        Chunk c = chunk(); db.saveResource("iron", c.toString(), "iron.3");
        assertTrue(Files.exists(root.resolve("Resources/iron.txt")));
        assertTrue(db.hasResource(c)); assertEquals("iron.3", db.getResource(c));
        db.saveResource("iron", c.toString(), "iron.7"); assertEquals("iron.7", db.getResource(c));
        Chunk other = mock(Chunk.class); when(other.toString()).thenReturn("elsewhere");
        assertFalse(db.hasResource(other)); assertNull(db.getResource(other));
        assertTrue(db.removeResource("IRON")); assertFalse(db.removeResource("IRON"));
    }
    @Test void aResourceRemovedDuringReadIsSkipped() throws Exception {
        Path folder = Files.createDirectory(root.resolve("Resources"));
        Path file = Files.writeString(folder.resolve("gone.txt"), "resource");
        try (var files = mockStatic(Files.class)) {
            files.when(() -> Files.newBufferedReader(file)).thenThrow(new NoSuchFileException(file.toString()));
            assertNull(db.getResource(chunk()));
        }
    }
    @Test void capacityDirectoryCreationFailureIsContained() throws Exception {
        Path blocked = Files.writeString(root.resolve("blocked"), "file");
        when(DowsingMain.plugin.getDataFolder()).thenReturn(blocked.toFile());
        assertDoesNotThrow(db::saveGuildCapacity);
    }
    @Test void ownersPreferGuildAndMigrateLegacyFaction() {
        Guild guild = mock(Guild.class); Faction faction = mock(Faction.class);
        when(faction.getOrCreateMainGuild()).thenReturn(guild);
        try (MockedStatic<FactionManager> fm = mockStatic(FactionManager.class)) {
            fm.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
            fm.when(() -> FactionManager.getByString("legacy")).thenReturn(faction);
            JSONObject data = new JSONObject(); assertNull(db.resolveOwner(data));
            data.put("faction", "none"); assertNull(db.resolveOwner(data));
            data.put("faction", "missing"); assertNull(db.resolveOwner(data));
            data.put("faction", "legacy"); assertSame(guild, db.resolveOwner(data));
            data.put("guild", "none"); assertNull(db.resolveOwner(data));
            data.put("guild", "guild"); assertSame(guild, db.resolveOwner(data));
        }
    }
    @Test void capacityRoundTripAndLegacyNumbers() throws Exception {
        db.loadGuildCapacity(); assertTrue(NodeManager.extraCapacityByGuild.isEmpty());
        NodeManager.extraCapacityByGuild.put("guild", 3); db.saveGuildCapacity();
        NodeManager.extraCapacityByGuild.clear(); db.loadGuildCapacity();
        assertEquals(Map.of("guild",3), NodeManager.extraCapacityByGuild);
        Files.writeString(root.resolve("guild_capacity.json"), "{\"decimal\":4.0,\"string\":\"2\",\"nil\":null}");
        db.loadGuildCapacity(); assertEquals(4, NodeManager.extraCapacityByGuild.get("decimal"));
        assertEquals(2, NodeManager.extraCapacityByGuild.get("string")); assertEquals(0, NodeManager.extraCapacityByGuild.get("nil"));
        Files.writeString(root.resolve("guild_capacity.json"), "invalid"); assertDoesNotThrow(db::loadGuildCapacity);
        Files.delete(root.resolve("guild_capacity.json")); Files.createDirectory(root.resolve("guild_capacity.json"));
        assertDoesNotThrow(db::saveGuildCapacity);
    }
    Node node(World world, boolean owned) {
        Node n = mock(Node.class); NodeBlock block = mock(NodeBlock.class); NodeType type = mock(NodeType.class);
        NodeSlot slot = mock(NodeSlot.class); ProductionMethod pm = mock(ProductionMethod.class);
        when(n.getId()).thenReturn(UUID.randomUUID()); when(n.getLoc()).thenReturn(new Location(world,1,2,3));
        when(n.getBlock()).thenReturn(block); when(block.getId()).thenReturn("block");
        when(n.getCurrentType()).thenReturn(type); when(type.getId()).thenReturn("type");
        when(type.getSlots()).thenReturn(List.of(slot)); when(slot.getId()).thenReturn("slot"); when(slot.getActivePm()).thenReturn(pm); when(pm.getId()).thenReturn("pm");
        when(n.getIsActive()).thenReturn(true); when(n.getLevel()).thenReturn(2); when(n.getCycleTime()).thenReturn(3);
        when(n.getTimeLeft()).thenReturn(4); when(n.getInputCounter()).thenReturn(5); when(n.getEfficiency()).thenReturn(80.0);
        when(n.getLastResult()).thenReturn(Map.of("v.STONE",4)); when(n.hasGuild()).thenReturn(owned);
        Guild guild = mock(Guild.class); when(guild.getId()).thenReturn("guild"); when(n.getGuild()).thenReturn(guild);
        return n;
    }
    @Test void nodesRoundTripWorldNameNumericFormsAndLastResult() throws Exception {
        World world = mock(World.class); when(world.getName()).thenReturn("test_world");
        Files.createDirectories(root.resolve("Nodes/subdir")); Node saved = node(world,true); db.saveNode(saved);
        Path path = root.resolve("Nodes/" + saved.getId() + ".json");
        JSONObject json = (JSONObject) new JSONParser().parse(Files.readString(path));
        assertEquals("test_world", json.get("world")); assertEquals("guild", json.get("guild"));
        // JSON integers and decimals are both valid persisted values.
        for (String key : List.of("xPos","yPos","zPos","level","cycle time","time remaining","input counter","efficiency")) json.put(key, 2L);
        Files.writeString(path,json.toJSONString());
        NodeBlock savedBlock = saved.getBlock();
        org.bukkit.Server server = mock(org.bukkit.Server.class); when(server.getWorld("test_world")).thenReturn(world);
        NodeType loadedType = mock(NodeType.class); NodeSlot slot = mock(NodeSlot.class); ProductionMethod pm = mock(ProductionMethod.class);
        when(loadedType.getSlots()).thenReturn(List.of(slot)); when(slot.getId()).thenReturn("slot"); when(slot.getActivePm()).thenReturn(pm); when(pm.getId()).thenReturn("pm");
        try (var bukkit = mockStatic(Bukkit.class); var blocks = mockStatic(BlockLoader.class); var types = mockStatic(TypeLoader.class);
             var pms = mockStatic(PMLoader.class); var factions = mockStatic(FactionManager.class);
             var nodes = mockConstruction(Node.class, (mock,context) -> {
                 assertEquals(world, ((Location)context.arguments().get(2)).getWorld());
                 assertEquals(2,context.arguments().get(5));
             })) {
            bukkit.when(Bukkit::getServer).thenReturn(server); blocks.when(() -> BlockLoader.getByString("block")).thenReturn(savedBlock);
            types.when(() -> TypeLoader.getByString("type")).thenReturn(loadedType); pms.when(() -> PMLoader.getByString("pm")).thenReturn(pm);
            db.loadNodes(); assertEquals(1,NodeManager.nodes.size()); verify(slot).setActivePm(pm);
            verify(NodeManager.nodes.getFirst()).setLastResult(Map.of("v.STONE",4));
            NodeManager.nodes.clear(); json.remove("efficiency"); json.remove(LastCycleResult.KEY); json.put("active pms",new JSONArray());
            Files.writeString(path,json.toJSONString()); db.loadNodes(); assertEquals(1,NodeManager.nodes.size());
            types.when(() -> TypeLoader.getByString("type")).thenReturn(null); NodeManager.nodes.clear(); db.loadNodes(); assertTrue(NodeManager.nodes.isEmpty());
            blocks.when(() -> BlockLoader.getByString("block")).thenReturn(null); db.loadNodes(); assertTrue(NodeManager.nodes.isEmpty());
            Files.writeString(path,"invalid"); assertDoesNotThrow(db::loadNodes);
        }
        Node unowned=node(world,false); db.saveNode(unowned);
        JSONObject data=(JSONObject)new JSONParser().parse(Files.readString(root.resolve("Nodes/"+unowned.getId()+".json")));
        assertEquals("none",data.get("guild"));
        db.deleteDatabase(); assertTrue(Files.isDirectory(root.resolve("Nodes/subdir"))); assertFalse(Files.exists(path));
        Files.delete(root.resolve("Nodes/subdir")); Files.delete(root.resolve("Nodes")); Files.writeString(root.resolve("Nodes"),"blocked");
        assertDoesNotThrow(() -> db.saveNode(saved));
    }
    @Test void jsonAccessorsAndSavePreserveTypedDefaultsAndExistingValues() throws Exception {
        Field field=Database.class.getDeclaredField("json"); field.setAccessible(true);
        JSONObject existing=new JSONObject(); existing.put("existing","&aGreen"); existing.put("object",new JSONObject(Map.of("a","b"))); existing.put("array",new JSONArray()); field.set(db,existing);
        HashMap<String,Object> defaults=new HashMap<>(); defaults.put("string","&bBlue"); defaults.put("integer",4); defaults.put("double",1.5); defaults.put("bool","true"); defaults.put("obj",new JSONObject()); defaults.put("arr",new JSONArray()); defaults.put("unsupported",new Object()); defaults.put("existing","old");
        assertEquals("§aGreen",db.getString("existing",defaults)); assertEquals("§bBlue",db.getString("string",defaults));
        assertEquals("absent",db.getRawData("absent",defaults)); assertTrue(db.getBoolean("bool",defaults)); assertFalse(db.getBoolean("absent",defaults));
        assertEquals(4,db.getInteger("integer",defaults)); assertEquals(1.5,db.getDouble("double",defaults)); assertEquals(-1,db.getDouble("absent",defaults)); assertEquals(-1,db.getInteger("absent",defaults));
        assertSame(existing.get("object"),db.getObject("object",defaults)); assertSame(defaults.get("obj"),db.getObject("obj",defaults)); assertTrue(db.getObject("missing",defaults).isEmpty());
        assertSame(existing.get("array"),db.getArray("array",defaults)); assertSame(defaults.get("arr"),db.getArray("arr",defaults)); assertTrue(db.getArray("missing",defaults).isEmpty());
        Path path=root.resolve("defaults.json"); assertTrue(db.save(path.toFile(),defaults));
        JSONObject result=(JSONObject)new JSONParser().parse(Files.readString(path)); assertEquals(4.0,result.get("integer")); assertEquals("§aGreen",result.get("existing")); assertFalse(result.containsKey("unsupported"));
        assertFalse(db.save(root.toFile(),defaults));
    }
}
