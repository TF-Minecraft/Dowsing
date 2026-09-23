package net.tfminecraft.dowsing.loaders;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.dowsing.Cache;


public class ConfigLoader {
	
	public void loadConfig(File configFile) {
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
		Cache.dowsingStick = config.getString("dowsing_item");
		Cache.cycleLength = config.getInt("input_cycle_length");
		Cache.naturalYieldEnabled = config.getBoolean("enable-natural-yields");
		if(config.getInt("members_per_node_capacity") != -1) {
			if(config.getInt("members_per_node_capacity") < 1) {
				Cache.membersPerCapacity = 1;
			} else {
				Cache.membersPerCapacity = config.getInt("members_per_node_capacity");
			}
			Cache.extraCapacity = true;
		} else {
			Cache.extraCapacity = false;
		}
		if(config.contains("extra-capacity-cost")) {
			Cache.extraCapacityCost = config.getDouble("extra-capacity-cost");
		} else {
			Cache.extraCapacityCost = 1000.0;
		}
		Cache.maxMemberCapacity = config.getInt("max-extra-capacity-from-members");
		if(config.contains("refund-amount")) {
			Cache.refundPercentage = config.getDouble("refund-amount");
		} else {
			Cache.refundPercentage = 0.8;
		}
		Cache.minMembersForNode = config.getInt("min-members-for-node", 1);

		Cache.efficiencyGrowthPerMember = config.getDouble("efficiency-growth-per-member", 0.1);
		Cache.maxEfficiencyPerMember = config.getDouble("max-efficiency-per-member", 25.0);
		Cache.efficiencyLossType = config.getDouble("efficiency-loss-type", 40.0);
		Cache.efficiencyLossPM = config.getDouble("efficiency-loss-pm", 5.0);
	}
}
