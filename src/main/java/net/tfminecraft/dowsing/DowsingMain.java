package net.tfminecraft.dowsing;

import java.io.File;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.dowsing.loaders.BlockLoader;
import net.tfminecraft.dowsing.loaders.ConfigLoader;
import net.tfminecraft.dowsing.loaders.PMLoader;
import net.tfminecraft.dowsing.loaders.SlotLoader;
import net.tfminecraft.dowsing.loaders.TypeLoader;
import net.tfminecraft.dowsing.managers.CommandManager;
import net.tfminecraft.dowsing.managers.NodeManager;
import net.tfminecraft.dowsing.managers.ResourceManager;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.utils.Database;
import net.tfminecraft.dowsing.utils.ItemCreator;
import net.tfminecraft.dowsing.utils.TabCompletion;
import net.tfminecraft.dowsing.utils.NodePayments;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;
import net.tfminecraft.simplefactions.events.SimpleFactionsReadyEvent;
import net.tfminecraft.simplefactions.guild.income.Ledger;

@SuppressWarnings("deprecation") // Preserve legacy section-color messages used by the plugin configuration.
public class DowsingMain extends JavaPlugin implements Listener {
	FileConfiguration config = getConfig();
	
	public static DowsingMain plugin;
	
	
	ConfigLoader configLoader = new ConfigLoader();
	PMLoader pmLoader = new PMLoader();
	SlotLoader slotLoader = new SlotLoader();
	TypeLoader typeLoader = new TypeLoader();
	BlockLoader blockLoader = new BlockLoader();
	
	ResourceManager resourceManager = new ResourceManager();
	
	CommandManager commands = new CommandManager();
	private static final NodeManager nodeManager = new NodeManager();
	
	Database db = new Database();
	private NodePayments payments;
	private boolean nodesLoaded;
	private boolean managersStarted;

	public NodePayments payments() { return payments; }
	public boolean nodesReady() {
		return managersStarted && SimpleFactionsApi.ready() && !payments.blocked();
	}
	
	public static NodeManager getNodeManager() {
		return nodeManager;
	}
	
	public static Boolean isReloading = false;
	@Override
	public void onEnable(){
		plugin = this;
		createFolders();
		createConfigs();
		loadConfigs();
		nodesLoaded = false;
		managersStarted = false;
		payments = new NodePayments(getDataFolder().toPath(), db, getLogger());
		getServer().getPluginManager().registerEvents(this, plugin);
		getServer().getPluginManager().registerEvents(resourceManager, plugin);
		getServer().getPluginManager().registerEvents(nodeManager, plugin);
		getCommand(commands.cmd1).setExecutor(commands);
		getCommand(commands.cmd1).setTabCompleter(new TabCompletion());
		tryStartNodes();
		getServer().getScheduler().runTaskTimer(this, this::tryStartNodes, 100L, 100L);
	}
	@EventHandler
	public void onFactionsReady(SimpleFactionsReadyEvent event) { tryStartNodes(); }

	void tryStartNodes() {
		if (!SimpleFactionsApi.ready()) return;
		if (!nodesLoaded) {
			NodeManager.nodes.clear();
			NodeManager.extraCapacityByGuild.clear();
			db.loadGuildCapacity();
			db.loadNodes();
			nodesLoaded = true;
		}
		if (managersStarted && !payments.blocked()) return;
		if (!payments.recover()) return;
		Ledger.setNodeUpkeepLookup(NodeManager::getTotalUpkeep);
		NodeManager.requestNodeBenefitSync();
		if (managersStarted) return;
		managersStarted = true;
		startManagers();
	}
	@Override
	public void onDisable() {
		Ledger.setNodeUpkeepLookup(null);
		if (nodesReady()) {
			db.saveNodes(NodeManager.nodes);
			db.saveGuildCapacity();
		}
		managersStarted = false;
	}
	public void startManagers() {
		nodeManager.start();
	}
	public void loadConfigs() {
		configLoader.loadConfig(new File(getDataFolder(), "config.yml"));
		pmLoader.loadConfig(new File(getDataFolder(), "production_methods.yml"));
		slotLoader.loadConfig(new File(getDataFolder(), "slots.yml"));
		typeLoader.loadConfig(new File(getDataFolder(), "types.yml"));
		blockLoader.loadConfig(new File(getDataFolder(), "blocks.yml"));
	}
	public void createFolders() {
		if (!getDataFolder().exists()) getDataFolder().mkdir();
		File subFolderR = new File(getDataFolder(), "Resources");
		if(!subFolderR.exists()) subFolderR.mkdir();
		File subFolderD = new File(getDataFolder(), "Nodes");
		if(!subFolderD.exists()) subFolderD.mkdir();
	}
	public void createConfigs() {
		String[] files = {
				"production_methods.yml",
				"slots.yml",
				"types.yml",
				"config.yml",
				"blocks.yml",
				};
		for(String s : files) {
			File newConfigFile = new File(getDataFolder(), s);
	        if (!newConfigFile.exists()) {
	        	newConfigFile.getParentFile().mkdirs();
	            saveResource(s, false);
	        }
		}
	}
	public void reloadConfigCommand() {
		if (!nodesReady()) return;
		nodeManager.cacheNodes();
		ItemCreator.clearInvalidPathWarnings();
		BlockLoader.clear();
		TypeLoader.clear();
		PMLoader.clear();
		SlotLoader.clear();
		loadConfigs();
		nodeManager.loadCache();
		NodeManager.requestNodeBenefitSync();
	}
	public void reloadConfigPCommand(Player p) {
		p.sendMessage(ChatColor.GREEN + "[Dowsing]" + ChatColor.YELLOW + " Reloading plugin...");
		reloadConfigCommand();
		p.sendMessage(ChatColor.GREEN + "[Dowsing]" + ChatColor.YELLOW + " Reloading complete!");
	}
}
