package com.flameteam.lavaguard;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CheckListeners implements Listener {

    private final LavaGuardAC plugin;
    private final Map<UUID, Long> lastBlockBreak = new HashMap<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();

    public CheckListeners(LavaGuardAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isFlying() || p.isGliding()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // 1. Speed Check
        if (plugin.getConfig().getBoolean("checks.speed.enabled", true)) {
            double distX = to.getX() - from.getX();
            double distZ = to.getZ() - from.getZ();
            double horizontalSpeed = Math.hypot(distX, distZ);

            double maxSpeed = plugin.getConfig().getDouble("checks.speed.max-speed", 0.75);
            if (p.hasPotionEffect(PotionEffectType.SPEED)) {
                maxSpeed += 0.35;
            }

            if (horizontalSpeed > maxSpeed) {
                event.setTo(from);
                plugin.getViolationManager().addViolation(p, "Speed / Bhop");
            }
        }

        // 2. Fly Check
        if (plugin.getConfig().getBoolean("checks.fly.enabled", true)) {
            Block below = p.getLocation().subtract(0, 0.2, 0).getBlock();
            if (below.getType() == Material.AIR && !p.isSwimming() && !p.isClimbing()) {
                int ticks = airTicks.getOrDefault(p.getUniqueId(), 0) + 1;
                airTicks.put(p.getUniqueId(), ticks);

                // Jeśli gracz utrzymuje wysokość lub leci w górę zbyt długo bez opadania
                if (ticks > 25 && to.getY() >= from.getY()) {
                    event.setTo(from);
                    plugin.getViolationManager().addViolation(p, "Flight");
                }
            } else {
                airTicks.put(p.getUniqueId(), 0);
            }
        }
    }

    @EventHandler
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        // 3. Reach / KillAura Check
        if (plugin.getConfig().getBoolean("checks.reach.enabled", true)) {
            double distance = p.getEyeLocation().distance(event.getEntity().getLocation());
            double maxReach = plugin.getConfig().getDouble("checks.reach.max-distance", 3.6);

            if (distance > maxReach) {
                event.setCancelled(true);
                plugin.getViolationManager().addViolation(p, "Reach / KillAura");
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;

        // 4. FastBreak / Nuker Check
        if (plugin.getConfig().getBoolean("checks.fastbreak.enabled", true)) {
            long now = System.currentTimeMillis();
            long last = lastBlockBreak.getOrDefault(p.getUniqueId(), 0L);
            long minTime = plugin.getConfig().getLong("checks.fastbreak.min-time-ms", 50);

            if (now - last < minTime && event.getBlock().getType().getHardness() > 0.2f) {
                event.setCancelled(true);
                plugin.getViolationManager().addViolation(p, "FastBreak / Nuker");
            }
            lastBlockBreak.put(p.getUniqueId(), now);
        }
    }
}