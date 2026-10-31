package com.flameteam.lavaguard;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LavaGuardAC extends JavaPlugin {

    private static LavaGuardAC instance;
    private ViolationManager violationManager;
    private DiscordWebhook discordWebhook;
    private PunishmentLogger punishmentLogger;
    
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<String> massBanList = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.punishmentLogger = new PunishmentLogger(this); // Inicjalizacja loggera
        this.discordWebhook = new DiscordWebhook(this);
        this.violationManager = new ViolationManager(this);

        UpdateChecker updateChecker = new UpdateChecker(this);
        updateChecker.checkForUpdates();

        getServer().getPluginManager().registerEvents(new CheckListeners(this), this);
        getServer().getPluginManager().registerEvents(new AdminGUI(this), this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        getCommand("lavaguard").setExecutor(new LavaGuardCommand(this));

        getLogger().info("Lava Guard AC by FlameTeam Studios zostal pomyslnie zaladowany!");
    }

    public static LavaGuardAC getInstance() {
        return instance;
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public PunishmentLogger getPunishmentLogger() {
        return punishmentLogger;
    }

    public Set<UUID> getFrozenPlayers() {
        return frozenPlayers;
    }

    public Set<String> getMassBanList() {
        return massBanList;
    }

    public String getMsg(String key) {
        String lang = getConfig().getString("language", "PL").toUpperCase();
        String prefix = getConfig().getString("messages." + lang + ".prefix", "&8[&6LavaGuard&cAC&8] &r");
        String msg = getConfig().getString("messages." + lang + "." + key, "Missing message: " + key);
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    public String getRawMsg(String key) {
        String lang = getConfig().getString("language", "PL").toUpperCase();
        String msg = getConfig().getString("messages." + lang + "." + key, "Missing message: " + key);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}