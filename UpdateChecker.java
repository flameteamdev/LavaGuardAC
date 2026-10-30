package com.flameteam.lavaguard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker implements Listener {

    private final LavaGuardAC plugin;
    private final HttpClient httpClient;
    private boolean updateAvailable = false;
    private String latestVersion = "";

    public UpdateChecker(LavaGuardAC plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }

        String project = plugin.getConfig().getString("update-checker.modrinth-project", "");
        if (project.isEmpty()) {
            return;
        }

        String apiUrl = "https://api.modrinth.com/v2/project/" + project + "/version";
        String currentVersion = plugin.getDescription().getVersion();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("User-Agent", "FlameTeamStudios/LavaGuardAC/" + currentVersion)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Pattern pattern = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcher = pattern.matcher(response.body());

                    if (matcher.find()) {
                        latestVersion = matcher.group(1);

                        if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                            updateAvailable = true;
                            String msg = formatUpdateMessage(currentVersion, latestVersion, project);

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.getLogger().warning(ChatColor.stripColor(msg));
                                for (Player admin : Bukkit.getOnlinePlayers()) {
                                    if (admin.isOp() || admin.hasPermission("lavaguard.admin")) {
                                        admin.sendMessage(msg);
                                    }
                                }
                            });
                        } else {
                            plugin.getLogger().info("Korzystasz z najnowszej wersji pluginu (" + currentVersion + ").");
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Nie udalo sie sprawdzic aktualizacji na Modrinth: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        if (!updateAvailable) return;

        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("lavaguard.admin")) {
            String currentVersion = plugin.getDescription().getVersion();
            String project = plugin.getConfig().getString("update-checker.modrinth-project", "lavaguard-ac");
            String msg = formatUpdateMessage(currentVersion, latestVersion, project);

            Bukkit.getScheduler().runTaskLater(plugin, () -> player.sendMessage(msg), 40L);
        }
    }

    private String formatUpdateMessage(String current, String latest, String project) {
        return plugin.getMsg("update-available")
                .replace("%current%", current)
                .replace("%latest%", latest)
                .replace("%project%", project);
    }
}