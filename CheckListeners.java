package com.flameteam.lavaguard;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class CheckListeners implements Listener {

    private final LavaGuardAC plugin;
    private final Map<UUID, Long> lastBlockBreak = new HashMap<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Double> lastDeltaY = new HashMap<>();

    private final Map<UUID, Double> pendingVelocityY = new HashMap<>();
    private final Map<UUID, Integer> ticksSinceVelocity = new HashMap<>();

    private final Map<UUID, Long> lastTotemPop = new HashMap<>();
    private final Map<UUID, Long> lastCrystalPlace = new HashMap<>();
    private final Map<UUID, Long> lastAnchorInteract = new HashMap<>();

    private final Map<UUID, Long> lastScaffoldPlace = new HashMap<>();
    private final Map<UUID, Float> lastPitch = new HashMap<>();
    private final Map<UUID, Float> lastYaw = new HashMap<>();
    private final Map<UUID, Integer> sprintBridgeStreak = new HashMap<>();

    public CheckListeners(LavaGuardAC plugin) {
        this.plugin = plugin;
    }

    // --- UKRYWANIE PLUGINÓW (WYKONANIE KOMENDY) ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("checks.hide-plugins.enabled", true)) {
            return;
        }

        Player p = event.getPlayer();
        if (p.hasPermission("lavaguard.admin") || p.hasPermission("lavaguard.bypass")) {
            return;
        }

        String message = event.getMessage().toLowerCase();
        String[] args = message.split(" ");
        String cmd = args[0].substring(1);

        if (cmd.equals("pl") || cmd.equals("plugins") || cmd.equals("about") || 
            cmd.equals("version") || cmd.equals("ver") || cmd.equals("?") || 
            cmd.equals("icanhasbukkit") || cmd.startsWith("bukkit:") || cmd.startsWith("minecraft:")) {
            
            event.setCancelled(true);
            p.sendMessage(plugin.getRawMsg("unknown-command"));
        }
    }

    // --- UKRYWANIE PLUGINÓW (USUNIĘCIE Z TAB-COMPLETE) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(PlayerCommandSendEvent event) {
        if (!plugin.getConfig().getBoolean("checks.hide-plugins.enabled", true)) {
            return;
        }

        Player p = event.getPlayer();
        if (p.hasPermission("lavaguard.admin") || p.hasPermission("lavaguard.bypass")) {
            return;
        }

        Iterator<String> iterator = event.getCommands().iterator();
        while (iterator.hasNext()) {
            String cmd = iterator.next().toLowerCase();
            if (cmd.equals("pl") || cmd.equals("plugins") || cmd.equals("about") || 
                cmd.equals("version") || cmd.equals("ver") || cmd.equals("?") || 
                cmd.equals("icanhasbukkit") || cmd.contains(":")) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        
        pendingVelocityY.remove(uuid);
        ticksSinceVelocity.remove(uuid);

        if (plugin.getFrozenPlayers().contains(uuid)) {
            if (plugin.getConfig().getBoolean("settings.freeze-quit-ban", true)) {
                plugin.getFrozenPlayers().remove(uuid);
                
                int days = plugin.getConfig().getInt("settings.ban-duration-days", 30);
                String reason = plugin.getRawMsg("quit-frozen-reason");
                String broadcast = plugin.getMsg("quit-frozen-broadcast").replace("%player%", p.getName());
                
                Date expiration = new Date(System.currentTimeMillis() + Duration.ofDays(days).toMillis());
                
                Bukkit.getBanList(BanList.Type.NAME).addBan(p.getName(), reason, expiration, "LavaGuardAC");
                Bukkit.broadcastMessage(broadcast);
                plugin.getDiscordWebhook().sendBanEmbed(p.getName(), "Logout Bypass (Freeze)", days);
                
                plugin.getPunishmentLogger().logPunishment(p.getName(), "BAN (Auto)", "Ucieczka (Freeze)", "LavaGuardAC");
            } else {
                plugin.getFrozenPlayers().remove(uuid);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Vector v = event.getVelocity();
        if (v.getY() > 0.2 && p.isOnGround()) {
            pendingVelocityY.put(p.getUniqueId(), v.getY());
            ticksSinceVelocity.put(p.getUniqueId(), 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        if (plugin.getFrozenPlayers().contains(uuid)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                event.setTo(from);
                return;
            }
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        lastPitch.put(uuid, from.getPitch());
        lastYaw.put(uuid, from.getYaw());

        double dY = to.getY() - from.getY();
        if (from.getX() != to.getX() || dY != 0 || from.getZ() != to.getZ()) {
            lastDeltaY.put(uuid, dY);
        }

        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isFlying() || p.isGliding()) {
            return;
        }

        if (plugin.getConfig().getBoolean("checks.nofall.enabled", true)) {
            if (dY < -0.1 && p.isOnGround() && !p.isSwimming() && !p.isInsideVehicle()) {
                boolean isNearGround = false;
                Location loc = to.clone();
                
                for (double x = -0.3; x <= 0.3; x += 0.3) {
                    for (double z = -0.3; z <= 0.3; z += 0.3) {
                        Material matBelow = loc.clone().add(x, -0.5, z).getBlock().getType();
                        if (matBelow.isSolid() || matBelow == Material.WATER || matBelow == Material.LAVA || matBelow == Material.COBWEB) {
                            isNearGround = true;
                            break;
                        }
                    }
                }
                
                if (!isNearGround) {
                    plugin.getViolationManager().addViolation(p, "NoFall (Packet Spoof)");
                }
            }
        }

        if (plugin.getConfig().getBoolean("checks.antikb.enabled", true)) {
            if (pendingVelocityY.containsKey(uuid)) {
                int ticks = ticksSinceVelocity.getOrDefault(uuid, 0) + 1;
                ticksSinceVelocity.put(uuid, ticks);

                if (dY > 0.0) {
                    pendingVelocityY.remove(uuid);
                    ticksSinceVelocity.remove(uuid);
                } else if (ticks > 5) {
                    Block blockAbove = to.clone().add(0, 2, 0).getBlock();
                    boolean isBlocked = blockAbove.getType().isSolid() || p.isSwimming() || to.getBlock().getType() == Material.COBWEB;

                    if (!isBlocked) {
                        plugin.getViolationManager().addViolation(p, "Anti-Knockback / Velocity");
                    }
                    
                    pendingVelocityY.remove(uuid);
                    ticksSinceVelocity.remove(uuid);
                }
            }
        }

        if (plugin.getConfig().getBoolean("checks.speed.enabled", true)) {
            double horizontalSpeed = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
            double maxSpeed = plugin.getConfig().getDouble("checks.speed.max-speed", 0.75);
            if (p.hasPotionEffect(PotionEffectType.SPEED)) maxSpeed += 0.35;

            if (horizontalSpeed > maxSpeed) {
                event.setTo(from);
                plugin.getViolationManager().addViolation(p, "Speed / Bhop");
            }
        }

        if (plugin.getConfig().getBoolean("checks.fly.enabled", true)) {
            Block below = p.getLocation().subtract(0, 0.2, 0).getBlock();
            if (!below.getType().isOccluding() && !p.isSwimming() && !p.isClimbing()) {
                int ticks = airTicks.getOrDefault(uuid, 0) + 1;
                airTicks.put(uuid, ticks);
                if (ticks > 25 && to.getY() >= from.getY()) {
                    event.setTo(from);
                    plugin.getViolationManager().addViolation(p, "Flight");
                }
            } else {
                airTicks.put(uuid, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && plugin.getFrozenPlayers().contains(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getDamager() instanceof Player p)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Entity victim = event.getEntity();
        UUID uuid = p.getUniqueId();

        if (plugin.getConfig().getBoolean("checks.wallhit.enabled", true)) {
            if (isHittingThroughWall(p, victim)) {
                event.setCancelled(true);
                plugin.getViolationManager().addViolation(p, "WallHit (Through Walls)");
                return;
            }
        }

        if (victim instanceof LivingEntity && plugin.getConfig().getBoolean("checks.criticals.enabled", true)) {
            float fallDist = p.getFallDistance();
            double deltaY = lastDeltaY.getOrDefault(uuid, 0.0);
            int ticksInAir = airTicks.getOrDefault(uuid, 0);
            double minFall = plugin.getConfig().getDouble("checks.criticals.min-fall-distance", 0.065);

            if (fallDist > 0.0f && !p.isSwimming() && !p.isClimbing() && !p.isInsideVehicle() && !p.isGliding()) {
                boolean invalidMotion = p.isOnGround() || deltaY >= 0.0;
                boolean microHop = (fallDist < minFall && ticksInAir <= 2 && Math.abs(deltaY) < minFall);

                if (invalidMotion || microHop) {
                    event.setCancelled(true);
                    plugin.getViolationManager().addViolation(p, "Criticals");
                    return;
                }
            }
        }

        if (victim instanceof Player target) {
            double distance = p.getEyeLocation().distance(target.getLocation());

            if (plugin.getConfig().getBoolean("checks.reach.enabled", true)) {
                if (distance > plugin.getConfig().getDouble("checks.reach.max-distance", 3.6)) {
                    event.setCancelled(true);
                    plugin.getViolationManager().addViolation(p, "Reach");
                    return;
                }
            }

            if (plugin.getConfig().getBoolean("checks.killaura.enabled", true)) {
                if (distance > 1.2) {
                    Vector playerLookDir = p.getLocation().getDirection().normalize();
                    Vector playerToTarget = target.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();

                    double dotProduct = playerLookDir.dot(playerToTarget);
                    double minDot = plugin.getConfig().getDouble("checks.killaura.min-dot-product", 0.55);

                    if (dotProduct < minDot) {
                        event.setCancelled(true);
                        plugin.getViolationManager().addViolation(p, "KillAura (Aiming Angle)");
                        return;
                    }
                }
            }

            if (plugin.getConfig().getBoolean("checks.hitbox.enabled", true)) {
                double expansion = plugin.getConfig().getDouble("checks.hitbox.max-expansion", 0.25);
                BoundingBox targetBox = target.getBoundingBox().expand(expansion);
                Vector eyePos = p.getEyeLocation().toVector();
                Vector lookDir = p.getEyeLocation().getDirection().normalize();

                if (!targetBox.contains(eyePos)) {
                    RayTraceResult rayTrace = targetBox.rayTrace(eyePos, lookDir, 6.0);
                    if (rayTrace == null) {
                        event.setCancelled(true);
                        plugin.getViolationManager().addViolation(p, "Hitbox");
                        return;
                    }
                }
            }
        }

        if (victim instanceof EnderCrystal && plugin.getConfig().getBoolean("checks.autocrystal.enabled", true)) {
            long lastPlace = lastCrystalPlace.getOrDefault(uuid, 0L);
            long minTime = plugin.getConfig().getLong("checks.autocrystal.min-time-ms", 50);
            if (System.currentTimeMillis() - lastPlace < minTime) {
                event.setCancelled(true);
                plugin.getViolationManager().addViolation(p, "Auto-Crystal");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && plugin.getFrozenPlayers().contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();

        if (plugin.getFrozenPlayers().contains(p.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (p.getGameMode() == GameMode.CREATIVE) return;

        if (plugin.getConfig().getBoolean("checks.fastbreak.enabled", true)) {
            long now = System.currentTimeMillis();
            long last = lastBlockBreak.getOrDefault(p.getUniqueId(), 0L);
            if (now - last < plugin.getConfig().getLong("checks.fastbreak.min-time-ms", 50) && event.getBlock().getType().getHardness() > 0.2f) {
                event.setCancelled(true);
                plugin.getViolationManager().addViolation(p, "FastBreak / Nuker");
            }
            lastBlockBreak.put(p.getUniqueId(), now);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        if (plugin.getFrozenPlayers().contains(uuid)) {
            event.setCancelled(true);
            return;
        }

        if (p.getGameMode() == GameMode.CREATIVE || p.isFlying()) return;

        if (plugin.getConfig().getBoolean("checks.scaffold.enabled", true)) {
            Block placed = event.getBlockPlaced();
            Block against = event.getBlockAgainst();
            Location pLoc = p.getLocation();

            double distX = Math.abs(pLoc.getX() - (placed.getX() + 0.5));
            double distZ = Math.abs(pLoc.getZ() - (placed.getZ() + 0.5));

            if (placed.getY() <= pLoc.getBlockY() - 1 && distX <= 1.6 && distZ <= 1.6) {
                long now = System.currentTimeMillis();
                long lastPlace = lastScaffoldPlace.getOrDefault(uuid, 0L);
                lastScaffoldPlace.put(uuid, now);

                double minPitch = plugin.getConfig().getDouble("checks.scaffold.min-pitch", 40.0);
                if (pLoc.getPitch() < minPitch) {
                    event.setCancelled(true);
                    plugin.getViolationManager().addViolation(p, "Scaffold (Invalid Pitch)");
                    return;
                }

                Vector eyeToAgainst = against.getLocation().add(0.5, 0.5, 0.5).toVector()
                        .subtract(p.getEyeLocation().toVector()).normalize();
                Vector lookDir = p.getEyeLocation().getDirection().normalize();
                if (lookDir.dot(eyeToAgainst) < 0.25) {
                    event.setCancelled(true);
                    plugin.getViolationManager().addViolation(p, "Scaffold (Impossible Line of Sight)");
                    return;
                }

                float prevPitch = lastPitch.getOrDefault(uuid, pLoc.getPitch());
                float prevYaw = lastYaw.getOrDefault(uuid, pLoc.getYaw());
                float pitchDelta = Math.abs(pLoc.getPitch() - prevPitch);
                float yawDelta = Math.abs(pLoc.getYaw() - prevYaw);
                if (yawDelta > 180.0f) yawDelta = 360.0f - yawDelta;

                double maxSnap = plugin.getConfig().getDouble("checks.scaffold.max-rotation-snap", 75.0);
                if (pitchDelta > maxSnap || yawDelta > maxSnap * 1.5) {
                    event.setCancelled(true);
                    plugin.getViolationManager().addViolation(p, "Scaffold (Snap Rotation)");
                    return;
                }

                long minDelay = plugin.getConfig().getLong("checks.scaffold.min-place-delay-ms", 65);
                if (now - lastPlace < minDelay && placed.getY() == against.getY()) {
                    event.setCancelled(true);
                    plugin.getViolationManager().addViolation(p, "Scaffold (FastPlace)");
                    return;
                }

                if (p.isSprinting() && !p.isSneaking() && placed.getY() == against.getY()) {
                    int streak = sprintBridgeStreak.getOrDefault(uuid, 0) + 1;
                    sprintBridgeStreak.put(uuid, streak);

                    int maxSprint = plugin.getConfig().getInt("checks.scaffold.max-sprint-bridges", 3);
                    if (streak > maxSprint) {
                        event.setCancelled(true);
                        plugin.getViolationManager().addViolation(p, "Scaffold (Sprint Bridge)");
                        sprintBridgeStreak.put(uuid, 0);
                        return;
                    }
                } else {
                    sprintBridgeStreak.put(uuid, 0);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (plugin.getFrozenPlayers().contains(p.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (!event.getAction().isRightClick()) return;

        if (event.getItem() != null && event.getItem().getType() == Material.END_CRYSTAL) {
            lastCrystalPlace.put(p.getUniqueId(), System.currentTimeMillis());
        }

        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.RESPAWN_ANCHOR) {
            if (plugin.getConfig().getBoolean("checks.autoanchor.enabled", true)) {
                long now = System.currentTimeMillis();
                long last = lastAnchorInteract.getOrDefault(p.getUniqueId(), 0L);
                long minTime = plugin.getConfig().getLong("checks.autoanchor.min-time-ms", 50);

                if (now - last < minTime) {
                    event.setCancelled(true);
                    plugin.getViolationManager().addViolation(p, "Auto-Anchor");
                }
                lastAnchorInteract.put(p.getUniqueId(), now);
            }
        }
    }

    @EventHandler
    public void onTotemPop(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player p) {
            lastTotemPop.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (!plugin.getConfig().getBoolean("checks.autototem.enabled", true)) return;

        long lastPop = lastTotemPop.getOrDefault(p.getUniqueId(), 0L);
        long now = System.currentTimeMillis();
        long minTime = plugin.getConfig().getLong("checks.autototem.min-time-ms", 100);

        if (now - lastPop < minTime) {
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();

            if ((cursor != null && cursor.getType() == Material.TOTEM_OF_UNDYING) ||
                (current != null && current.getType() == Material.TOTEM_OF_UNDYING) || 
                event.getClick().isKeyboardClick()) {

                event.setCancelled(true);
                plugin.getViolationManager().addViolation(p, "Auto-Totem");
            }
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (!plugin.getConfig().getBoolean("checks.autototem.enabled", true)) return;

        long lastPop = lastTotemPop.getOrDefault(p.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastPop < plugin.getConfig().getLong("checks.autototem.min-time-ms", 100)) {
            if (event.getMainHandItem() != null && event.getMainHandItem().getType() == Material.TOTEM_OF_UNDYING) {
                event.setCancelled(true);
                plugin.getViolationManager().addViolation(p, "Auto-Totem");
            }
        }
    }

    private boolean isHittingThroughWall(Player attacker, Entity victim) {
        if (victim instanceof LivingEntity living && !attacker.hasLineOfSight(living)) {
            return true;
        }

        Location eyeLoc = attacker.getEyeLocation();
        Vector eyePos = eyeLoc.toVector();
        Vector lookDir = eyeLoc.getDirection().normalize();

        BoundingBox victimBox = victim.getBoundingBox().expand(0.15);
        if (victimBox.contains(eyePos)) {
            return false;
        }

        RayTraceResult entityHit = victimBox.rayTrace(eyePos, lookDir, 6.0);
        double distToTarget = (entityHit != null)
                ? eyePos.distance(entityHit.getHitPosition())
                : eyePos.distance(victimBox.getCenter());

        double buffer = plugin.getConfig().getDouble("checks.wallhit.wall-buffer", 0.20);
        double checkDist = distToTarget - buffer;
        if (checkDist <= 0.1) return false;

        RayTraceResult blockHit = attacker.getWorld().rayTraceBlocks(
                eyeLoc,
                lookDir,
                checkDist,
                FluidCollisionMode.NEVER,
                true
        );

        return blockHit != null && blockHit.getHitBlock() != null;
    }
}