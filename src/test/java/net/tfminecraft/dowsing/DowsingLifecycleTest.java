package net.tfminecraft.dowsing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.dowsing.loaders.*;
import net.tfminecraft.dowsing.managers.NodeManager;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.utils.Database;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class DowsingLifecycleTest {
    DowsingMain previous;
    Map<Field,Object> previousCache=new HashMap<>();
    List<Node> previousNodes;
    @BeforeEach void setup() throws Exception {
        previous=DowsingMain.plugin;
        for(Field field:Cache.class.getDeclaredFields()) if(Modifier.isStatic(field.getModifiers())) previousCache.put(field,field.get(null));
        previousNodes=new ArrayList<>(NodeManager.nodes);NodeManager.nodes.clear(); MockBukkit.mock();
    }
    @AfterEach void cleanup() throws Exception {
        // The persistence boundary stays mocked while exercising disable below.
        for(var plugin:MockBukkit.getMock().getPluginManager().getPlugins()) MockBukkit.getMock().getScheduler().cancelTasks(plugin);
        NodeManager.nodes.clear();MockBukkit.unmock();DowsingMain.plugin=previous;
        NodeManager.nodes.addAll(previousNodes);
        for(var e:previousCache.entrySet())e.getKey().set(null,e.getValue());
    }
    @Test void enableReloadAndDisableWireConfigurationCommandsAndPersistence() throws Exception {
        try(var configs=mockConstruction(ConfigLoader.class);var pms=mockConstruction(PMLoader.class);var slots=mockConstruction(SlotLoader.class);var types=mockConstruction(TypeLoader.class);var blocks=mockConstruction(BlockLoader.class);var databases=mockConstruction(Database.class);var ledger=mockStatic(Ledger.class);var managers=mockStatic(NodeManager.class)) {
            DowsingMain plugin=MockBukkit.load(DowsingMain.class);assertSame(plugin,DowsingMain.plugin);assertNotNull(DowsingMain.getNodeManager());
            assertNotNull(plugin.getCommand("dowsing").getExecutor());assertNotNull(plugin.getCommand("dowsing").getTabCompleter());
            for(String name:List.of("Resources","Nodes"))assertTrue(Files.isDirectory(plugin.getDataFolder().toPath().resolve(name)));
            for(String name:List.of("config.yml","production_methods.yml","slots.yml","types.yml","blocks.yml"))assertTrue(Files.exists(plugin.getDataFolder().toPath().resolve(name)));
            verify(plugin.db).loadGuildCapacity();verify(plugin.db).loadNodes();
            plugin.createFolders();plugin.createConfigs();
            plugin.reloadConfigCommand();
            Player player=mock(Player.class);plugin.reloadConfigPCommand(player);verify(player,times(2)).sendMessage(org.mockito.ArgumentMatchers.anyString());
            verify(plugin.configLoader,times(3)).loadConfig(new java.io.File(plugin.getDataFolder(),"config.yml"));
            Node node=mock(Node.class);NodeManager.nodes.add(node);
            plugin.onDisable();verify(plugin.db).deleteDatabase();verify(plugin.db).saveNode(node);verify(plugin.db).saveGuildCapacity();
            NodeManager.nodes.clear();
            try(var paths=Files.walk(plugin.getDataFolder().toPath())){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
            plugin.onEnable();assertTrue(Files.isDirectory(plugin.getDataFolder().toPath()));
            MockBukkit.getMock().getPluginManager().disablePlugin(plugin);
        }
    }
}
