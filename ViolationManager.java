package com.flameteam.lavaguard;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ViolationManager {

    private final LavaGuardAC plugin;
    private final Map<UUID, Integer> violations = new HashMap<>();

    public ViolationManager(LavaGuardAC plugin) {
        this.plugin = plugin;
    }

    public void addViolation(Player player, String cheatType) {
        if (player.hasPermission("lavaguard.bypass")) return;

        UUID uuid = player.getUniqueId();
        int currentVl = violations.getOrDefault(uuid, 0) + 1;
        violations.put(uuid, currentVl);

        int maxVl = plugin.getConfig().getInt("settings.max-violations", 5);
        if (currentVl >= maxVl) {
            punishPlayer(player, cheatType);
            violations.remove(uuid);
        }
    }

    public void punishPlayer(Player player, String cheatType) {
        int days = plugin.getConfig().getInt("settings.ban-duration-days", 30);
        String reason = plugin.getRawMsg("ban-reason").replace("%cheat%", cheatType);
        String broadcast = plugin.getMsg("ban-broadcast")
                .replace("%player%", player.getName())
                .replace("%cheat%", cheatType);

        Date expiration = new Date(System.currentTimeMillis() + Duration.ofDays(days).toMillis());
        
        // Poprawione nadawanie bana po nazwie gracza (String)
        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, expiration, "LavaGuardAC");
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.kickPlayer(reason);
            Bukkit.broadcastMessage(broadcast);
        });

        // Wysyłanie wiadomości Embed na Discorda
        plugin.getDiscordWebhook().sendBanEmbed(player.getName(), cheatType, days);
    }
}