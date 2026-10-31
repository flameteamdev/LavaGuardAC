package com.flameteam.lavaguard;

import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PunishmentLogger {

    private final LavaGuardAC plugin;
    private final File logFile;
    private final SimpleDateFormat dateFormat;

    public PunishmentLogger(LavaGuardAC plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "logs.txt");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        createFileIfNotExists();
    }

    private void createFileIfNotExists() {
        if (!logFile.exists()) {
            try {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie mozna utworzyc pliku logs.txt: " + e.getMessage());
            }
        }
    }

    public void logPunishment(String target, String action, String reason, String executor) {
        // Zapisywanie do pliku w wątku asynchronicznym (aby nie lagować serwera)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                String timestamp = dateFormat.format(new Date());
                String logLine = String.format("[%s] AKCJA: %s | GRACZ: %s | POWOD: %s | WYKONAWCA: %s", 
                        timestamp, action, target, reason, executor);
                writer.write(logLine);
                writer.newLine();
            } catch (IOException e) {
                plugin.getLogger().warning("Blad podczas zapisywania do logs.txt: " + e.getMessage());
            }
        });
    }
}