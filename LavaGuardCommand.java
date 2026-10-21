package com.flameteam.lavaguard;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LavaGuardCommand implements CommandExecutor {

    private final LavaGuardAC plugin;

    public LavaGuardCommand(LavaGuardAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lavaguard.admin")) {
            sender.sendMessage(plugin.getMsg("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) {
                AdminGUI.openPlayerList(player, plugin);
            } else {
                sender.sendMessage("Komenda tylko dla graczy!");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("lang")) {
            if (args.length < 2 || (!args[1].equalsIgnoreCase("PL") && !args[1].equalsIgnoreCase("EN"))) {
                sender.sendMessage(ChatColor.RED + "Użycie / Usage: /lg lang <PL|EN>");
                return true;
            }
            plugin.getConfig().set("language", args[1].toUpperCase());
            plugin.saveConfig();
            sender.sendMessage(plugin.getMsg("lang-changed"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(plugin.getMsg("reloaded"));
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "Lava Guard AC (FlameTeam Studios): " + ChatColor.YELLOW + "/lg gui | /lg lang <PL/EN> | /lg reload");
        return true;
    }
}