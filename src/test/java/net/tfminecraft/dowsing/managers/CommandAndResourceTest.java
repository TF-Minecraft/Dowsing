package net.tfminecraft.dowsing.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.dowsing.*;
import net.tfminecraft.dowsing.utils.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import io.lumine.mythic.lib.api.item.NBTItem;
import dev.lone.itemsadder.api.CustomFurniture;

class CommandAndResourceTest {
    @TempDir Path root;
    Player player; Command command; Database db; DowsingMain previous; World world;
    @BeforeEach void setup() {
        previous=DowsingMain.plugin; DowsingMain.plugin=mock(DowsingMain.class);
        when(DowsingMain.plugin.getDataFolder()).thenReturn(root.toFile());
        player=mock(Player.class); when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
        world=mock(World.class); Chunk chunk=mock(Chunk.class); when(chunk.toString()).thenReturn("Chunk");
        when(world.getChunkAt(anyInt(),anyInt())).thenReturn(chunk); when(world.getChunkAt(any(Location.class))).thenReturn(chunk); when(player.getLocation()).thenReturn(new Location(world,0,64,0));
        command=mock(Command.class); when(command.getName()).thenReturn("dowsing"); db=mock(Database.class);
    }
    @AfterEach void restore(){DowsingMain.plugin=previous;}
    boolean run(CommandManager manager,CommandSender sender,String...args){return manager.onCommand(sender,command,"dowsing",args);}
    @Test void incompleteCommandsReturnUsageWithoutThrowing(){
        assertNotNull(new Permissions());
        CommandManager manager=new CommandManager(); manager.db=db;
        for(String[] args:List.of(new String[0],new String[]{"createresource"},new String[]{"createresource","id"},new String[]{"createresource","id","iron"},new String[]{"deleteresource"})) {
            assertFalse(run(manager,player,args));
        }
        verifyNoInteractions(db);
    }
    @Test void commandPermissionsReloadAndResourceWrites() throws Exception {
        CommandManager manager=new CommandManager(); manager.db=db;
        when(command.getName()).thenReturn("other"); assertFalse(run(manager,player));
        when(command.getName()).thenReturn("dowsing"); when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(false);
        run(manager,player); verify(player).sendMessage(contains("do not have access"));
        when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
        CommandSender console=mock(CommandSender.class); when(console.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
        try(var javaPlugin=mockStatic(JavaPlugin.class)){
            javaPlugin.when(()->JavaPlugin.getPlugin(DowsingMain.class)).thenReturn(DowsingMain.plugin);
            run(manager,player,"reload"); verify(DowsingMain.plugin).reloadConfigPCommand(player);
            run(manager,console,"reload"); verify(DowsingMain.plugin).reloadConfigCommand();
        }
        run(manager,console,"createresource"); run(manager,player,"unknown");
        run(manager,player,"createresource","iron","iron_ore","3"); verify(db).saveResource("iron","Chunk","iron_ore.3");
        doThrow(new IOException("write failed")).when(db).saveResource("fail","Chunk","iron.1");
        assertDoesNotThrow(()->run(manager,player,"createresource","fail","iron","1"));
        run(manager,player,"deleteresource","iron"); verify(db).removeResource("iron");
    }
    @Test void completionHandlesZeroArgumentsAndMissingResourceDirectory() throws Exception {
        TabCompletion tabs=new TabCompletion();
        assertEquals(List.of("createresource","deleteresource","reload"),tabs.onTabComplete(player,command,"dowsing",new String[0]));
        assertEquals(List.of(),tabs.onTabComplete(player,command,"dowsing",new String[]{"deleteresource",""}));
        Files.createDirectories(root.resolve("Resources/nested")); Files.writeString(root.resolve("Resources/iron.txt"),"");
        assertEquals(List.of("iron"),tabs.onTabComplete(player,command,"dowsing",new String[]{"deleteresource",""}));
        assertEquals(List.of(),tabs.onTabComplete(player,command,"",new String[]{"createresource",""}));
        assertEquals(List.of("1","2","3","4","5","6","7","8"),tabs.onTabComplete(player,command,"",new String[]{"createresource","x","y",""}));
        assertEquals(List.of("reload"),tabs.onTabComplete(player,command,"",new String[]{"reload"}));
        CommandSender console=mock(CommandSender.class); when(console.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
        assertEquals(List.of("reload"),tabs.onTabComplete(console,command,"",new String[]{""}));
        for(String[] args:List.of(new String[]{"createresource",""},new String[]{"createresource","x",""},new String[]{"createresource","x","y",""},new String[]{"deleteresource",""})) assertEquals(List.of(),tabs.onTabComplete(console,command,"",args));
        when(command.getName()).thenReturn("other"); assertEquals(List.of(),tabs.onTabComplete(player,command,"",new String[]{""}));
        when(player.hasPermission(Permissions.Permission_Admin)).thenReturn(false); assertEquals(List.of(),tabs.onTabComplete(player,command,"",new String[]{""}));
    }
    @Test void completionFiltersEveryCurrentTokenAndUsesLoadedResourceLabels() throws Exception {
        TabCompletion tabs = new TabCompletion();
        Files.createDirectories(root.resolve("Resources"));
        Files.writeString(root.resolve("Resources/iron.txt"), "");
        Files.writeString(root.resolve("Resources/ignored.json"), "");
        var mineral = mock(net.tfminecraft.dowsing.objects.NodeType.class);
        var duplicate = mock(net.tfminecraft.dowsing.objects.NodeType.class);
        var magic = mock(net.tfminecraft.dowsing.objects.NodeType.class);
        var missing = mock(net.tfminecraft.dowsing.objects.NodeType.class);
        var blank = mock(net.tfminecraft.dowsing.objects.NodeType.class);
        when(mineral.getResource()).thenReturn("mineral");
        when(duplicate.getResource()).thenReturn("mineral");
        when(magic.getResource()).thenReturn("Magic");
        when(blank.getResource()).thenReturn(" ");
        try (var types = mockStatic(net.tfminecraft.dowsing.loaders.TypeLoader.class)) {
            types.when(net.tfminecraft.dowsing.loaders.TypeLoader::getNodeTypes)
                    .thenReturn(List.of(mineral, duplicate, magic, missing, blank));
            assertEquals(List.of("reload"), tabs.onTabComplete(player, command, "", new String[]{"RE"}));
            assertEquals(List.of("reload"), tabs.onTabComplete(player, command, "", new String[]{"reload"}));
            assertEquals(List.of("iron"), tabs.onTabComplete(player, command, "", new String[]{"DELETERESOURCE", "IR"}));
            assertEquals(List.of("mineral", "Magic"), tabs.onTabComplete(player, command, "", new String[]{"createresource", "id", ""}));
            assertEquals(List.of("mineral"), tabs.onTabComplete(player, command, "", new String[]{"CREATERESOURCE", "id", "mI"}));
            assertEquals(List.of("2"), tabs.onTabComplete(player, command, "", new String[]{"createresource", "id", "mineral", "2"}));
            for (String[] args : List.of(new String[]{"absent"}, new String[]{"reload", ""},
                    new String[]{"deleteresource", "missing"}, new String[]{"createresource", "id", "missing"},
                    new String[]{"createresource", "id", "mineral", "9"}, new String[]{"createresource", "id", "mineral", "2", ""})) {
                assertTrue(tabs.onTabComplete(player, command, "", args).isEmpty());
            }
            types.when(net.tfminecraft.dowsing.loaders.TypeLoader::getNodeTypes).thenReturn(List.of(magic));
            assertTrue(tabs.onTabComplete(player, command, "", new String[]{"createresource", "id", "mi"}).isEmpty());
            assertEquals(List.of("Magic"), tabs.onTabComplete(player, command, "", new String[]{"createresource", "id", ""}));
            CommandSender console = mock(CommandSender.class);
            when(console.hasPermission(Permissions.Permission_Admin)).thenReturn(true);
            assertEquals(List.of("reload"), tabs.onTabComplete(console, command, "", new String[]{"rE"}));
            assertTrue(tabs.onTabComplete(console, command, "", new String[]{"create"}).isEmpty());
            assertTrue(tabs.onTabComplete(console, command, "", new String[]{"reload", ""}).isEmpty());
            when(console.hasPermission(Permissions.Permission_Admin)).thenReturn(false);
            assertTrue(tabs.onTabComplete(console, command, "", new String[]{""}).isEmpty());
        }
    }
    @Test void dowsingStickHandlesActionsCooldownTypeAndYieldColors() throws Exception {
        String previousStick=Cache.dowsingStick; Boolean previousEnabled=Cache.naturalYieldEnabled;
        Cache.dowsingStick="TOOL.DOWSER"; Cache.naturalYieldEnabled=true;
        ResourceManager manager=new ResourceManager(); manager.db=db;
        ItemStack item=mock(ItemStack.class); PlayerInventory inv=mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inv); when(inv.getItemInMainHand()).thenReturn(item);
        NBTItem nbt=mock(NBTItem.class);
        try(var nbtItems=mockStatic(NBTItem.class)) {
            nbtItems.when(()->NBTItem.get(item)).thenReturn(nbt);
            manager.dowsingEvent(event(Action.LEFT_CLICK_AIR)); verifyNoInteractions(nbt);
            manager.cooldown.put(player,Long.MAX_VALUE); manager.dowsingEvent(event(Action.RIGHT_CLICK_AIR)); verifyNoInteractions(nbt);
            manager.cooldown.put(player,0L); manager.dowsingEvent(event(Action.RIGHT_CLICK_BLOCK)); verify(nbt).hasType();
            when(nbt.hasType()).thenReturn(true); when(nbt.getType()).thenReturn("OTHER"); manager.cooldown.clear(); manager.dowsingEvent(event(Action.RIGHT_CLICK_AIR));
            when(nbt.getType()).thenReturn("TOOL"); when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("wrong"); manager.cooldown.clear(); manager.dowsingEvent(event(Action.RIGHT_CLICK_AIR));
            when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("DOWSER"); Cache.naturalYieldEnabled=false; manager.cooldown.clear(); manager.dowsingEvent(event(Action.RIGHT_CLICK_AIR)); verify(player).sendMessage(contains("does not stir"));
            Cache.naturalYieldEnabled=true; manager.cooldown.clear(); manager.dowsingEvent(event(Action.RIGHT_CLICK_AIR)); verify(player).sendMessage("§7Nothing found here");
            when(db.hasResource(any())).thenReturn(true);
            int[] yields={1,2,4,6,8}; String[] colors={"§4","§c","§e","§a","§2"};
            for(int i=0;i<yields.length;i++){when(db.getResource(any())).thenReturn("iron_ore."+yields[i]);manager.cooldown.clear();manager.dowsingEvent(event(Action.RIGHT_CLICK_AIR));verify(player).sendMessage("§fThis land holds §eiron ore§f, with a yield of "+colors[i]+yields[i]);}
        } finally {Cache.dowsingStick=previousStick;Cache.naturalYieldEnabled=previousEnabled;}
    }
    PlayerInteractEvent event(Action action){PlayerInteractEvent e=mock(PlayerInteractEvent.class);when(e.getPlayer()).thenReturn(player);when(e.getAction()).thenReturn(action);return e;}
    @Test void furnitureDetectionAcceptsAnyBukkitCollection(){
        ResourceManager manager=new ResourceManager(); Block block=mock(Block.class); World world=mock(World.class); Location location=new Location(world,0,0,0);
        when(block.getWorld()).thenReturn(world);when(block.getLocation()).thenReturn(location);
        Entity distant=mock(Entity.class),near=mock(Entity.class),other=mock(Entity.class);
        when(world.getNearbyEntities(location,0.2,0.2,0.2)).thenReturn(Set.of(near,other));when(world.getEntities()).thenReturn(List.of(distant,other,near));
        try(var furniture=mockStatic(CustomFurniture.class)){
            assertFalse(manager.clickedIsFurniture(block,"realm:mine"));
            CustomFurniture f=mock(CustomFurniture.class);when(f.getNamespace()).thenReturn("realm");when(f.getId()).thenReturn("other");furniture.when(()->CustomFurniture.byAlreadySpawned(near)).thenReturn(f);
            assertFalse(manager.clickedIsFurniture(block,"realm:mine"));when(f.getId()).thenReturn("mine");assertTrue(manager.clickedIsFurniture(block,"realm:mine"));
        }
    }
}
