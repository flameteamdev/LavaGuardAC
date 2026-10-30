package com.flameteam.lavaguard;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

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

        if (args[0].equalsIgnoreCase("lang") && args.length == 2) {
            if (!args[1].equalsIgnoreCase("PL") && !args[1].equalsIgnoreCase("EN")) {
                sender.sendMessage(ChatColor.RED + "Użycie: /lg lang <PL|EN>");
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

        if (args[0].equalsIgnoreCase("unban") && args.length == 2) {
            String targetName = args[1];
            BanList<org.bukkit.BanEntry<org.bukkit.profile.PlayerProfile>> banList = Bukkit.getBanList(BanList.Type.NAME);

            if (banList.isBanned(targetName)) {
                banList.pardon(targetName);
                sender.sendMessage(plugin.getMsg("unban-success").replace("%player%", targetName));
            } else {
                sender.sendMessage(plugin.getMsg("unban-not-banned").replace("%player%", targetName));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("kb") && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getMsg("player-not-found").replace("%player%", args[1]));
                return true;
            }
            target.setVelocity(target.getLocation().getDirection().multiply(-0.8).setY(0.4));
            sender.sendMessage(plugin.getMsg("kb-applied").replace("%player%", target.getName()));
            return true;
        }

        if (args[0].equalsIgnoreCase("shuffle") && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getMsg("player-not-found").replace("%player%", args[1]));
                return true;
            }
            
            List<ItemStack> hotbarItems = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                hotbarItems.add(target.getInventory().getItem(i));
            }
            Collections.shuffle(hotbarItems);
            for (int i = 0; i < 9; i++) {
                target.getInventory().setItem(i, hotbarItems.get(i));
            }
            
            sender.sendMessage(plugin.getMsg("hotbar-shuffled").replace("%player%", target.getName()));
            return true;
        }

        if (args[0].equalsIgnoreCase("freeze") && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getMsg("player-not-found").replace("%player%", args[1]));
                return true;
            }
            
            if (plugin.getFrozenPlayers().contains(target.getUniqueId())) {
                plugin.getFrozenPlayers().remove(target.getUniqueId());
                target.sendMessage(plugin.getMsg("player-unfrozen"));
                sender.sendMessage(plugin.getMsg("admin-unfroze").replace("%player%", target.getName()));
            } else {
                plugin.getFrozenPlayers().add(target.getUniqueId());
                target.sendMessage(plugin.getMsg("player-frozen"));
                sender.sendMessage(plugin.getMsg("admin-froze").replace("%player%", target.getName()));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("massban")) {
            Set<String> list = plugin.getMassBanList();

            if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
                String targetName = args[2];
                list.add(targetName);
                sender.sendMessage(plugin.getMsg("massban-added").replace("%player%", targetName));
                return true;
            }

            if (args.length == 1) {
                if (list.isEmpty()) {
                    sender.sendMessage(plugin.getMsg("massban-empty"));
                    return true;
                }

                int count = list.size();
                for (String name : list) {
                    Player target = Bukkit.getPlayerExact(name);
                    if (target != null) {
                        plugin.getViolationManager().punishPlayer(target, "MassBan");
                    } else {
                        int days = plugin.getConfig().getInt("settings.ban-duration-days", 30);
                        String reason = plugin.getRawMsg("ban-reason").replace("%cheat%", "MassBan");
                        Date expiration = new Date(System.currentTimeMillis() + Duration.ofDays(days).toMillis());
                        Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, expiration, "LavaGuardAC");
                        plugin.getDiscordWebhook().sendBanEmbed(name, "MassBan (Offline)", days);
                    }
                }
                sender.sendMessage(plugin.getMsg("massban-executed").replace("%count%", String.valueOf(count)));
                list.clear();
                return true;
            }
        }

        sender.sendMessage(ChatColor.GOLD + "Lava Guard AC: " + ChatColor.YELLOW + "/lg <gui|lang|reload|unban|kb|shuffle|freeze|massban>");
        return true;
    }
}