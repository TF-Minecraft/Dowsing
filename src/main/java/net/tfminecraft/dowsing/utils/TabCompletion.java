package net.tfminecraft.dowsing.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.loaders.TypeLoader;
import net.tfminecraft.dowsing.objects.NodeType;

public class TabCompletion implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!Permissions.isAdmin(sender) || !cmd.getName().equalsIgnoreCase("dowsing")) return List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1];
        if (args.length < 2) {
            return matching(sender instanceof Player
                    ? List.of("createresource", "deleteresource", "reload") : List.of("reload"), prefix);
        }
        if (!(sender instanceof Player)) return List.of();
        if (args[0].equalsIgnoreCase("createresource")) {
            if (args.length == 3) {
                return matching(TypeLoader.getNodeTypes().stream().map(NodeType::getResource)
                        .filter(resource -> resource != null && !resource.isBlank()).distinct().toList(), prefix);
            }
            if (args.length == 4) return matching(List.of("1", "2", "3", "4", "5", "6", "7", "8"), prefix);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("deleteresource")) {
            List<String> ids = new ArrayList<>();
            File[] files = new File(DowsingMain.plugin.getDataFolder(), "Resources").listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (file.isFile() && name.endsWith(".txt")) ids.add(name.substring(0, name.length() - 4));
                }
            }
            return matching(ids, prefix);
        }
        return List.of();
    }

    private static List<String> matching(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
