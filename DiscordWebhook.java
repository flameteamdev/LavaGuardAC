package com.flameteam.lavaguard;

import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

public class DiscordWebhook {

    private final LavaGuardAC plugin;
    private final HttpClient httpClient;

    public DiscordWebhook(LavaGuardAC plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void sendBanEmbed(String playerName, String cheatType, int days) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) {
            return;
        }

        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty() || webhookUrl.contains("TWOJE_ID")) {
            return;
        }

        String lang = plugin.getConfig().getString("language", "PL").toUpperCase();
        String title = plugin.getConfig().getString("messages." + lang + ".discord-embed-title", "Lava Guard AC - Ban");
        String fieldPlayer = plugin.getConfig().getString("messages." + lang + ".discord-field-player", "Player");
        String fieldReason = plugin.getConfig().getString("messages." + lang + ".discord-field-reason", "Reason");
        String fieldDuration = plugin.getConfig().getString("messages." + lang + ".discord-field-duration", "Duration");
        String durationVal = plugin.getConfig().getString("messages." + lang + ".discord-duration-value", "%days% days")
                .replace("%days%", String.valueOf(days));

        // Budowanie bezpiecznego ładunku JSON z wiadomością Embed i głową gracza z Mc-Heads
        String jsonPayload = """
                {
                  "username": "Lava Guard AC",
                  "embeds": [
                    {
                      "title": "%s",
                      "color": 16724530,
                      "thumbnail": {
                        "url": "https://mc-heads.net/avatar/%s/100"
                      },
                      "fields": [
                        {
                          "name": "%s",
                          "value": "`%s`",
                          "inline": true
                        },
                        {
                          "name": "%s",
                          "value": "`%s`",
                          "inline": true
                        },
                        {
                          "name": "%s",
                          "value": "%s",
                          "inline": false
                        }
                      ],
                      "footer": {
                        "text": "FlameTeam Studios • Lava Guard AC"
                      },
                      "timestamp": "%s"
                    }
                  ]
                }
                """.formatted(
                escapeJson(title),
                escapeJson(playerName),
                escapeJson(fieldPlayer), escapeJson(playerName),
                escapeJson(fieldReason), escapeJson(cheatType),
                escapeJson(fieldDuration), escapeJson(durationVal),
                Instant.now().toString()
        );

        // Asynchroniczne wysyłanie zapytania HTTP poza głównym wątkiem serwera
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                plugin.getLogger().warning("Nie udało się wysłać powiadomienia na Discord: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "");
    }
}