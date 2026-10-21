package com.flameteam.lavaguard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collections;

public class AdminGUI implements Listener {

    private final LavaGuardAC plugin;

    public AdminGUI(LavaGuardAC plugin) {
        this.plugin = plugin;
    }

    public static void openPlayerList(Player admin, LavaGuardAC plugin) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.getRawMsg("gui-title"));

        for (Player target : Bukkit.getOnlinePlayers()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(ChatColor.YELLOW + target.getName());
                head.setItemMeta(meta);
            }
            inv.addItem(head);
        }
        admin.openInventory(inv);
    }

    private void openPunishMenu(Player admin, Player target) {
        String title = plugin.getRawMsg("gui-punish-title").replace("%player%", target.getName());
        Inventory inv = Bukkit.createInventory(null, 27, title);

        ItemStack banItem = new ItemStack(Material.ANVIL);
        ItemMeta banMeta = banItem.getItemMeta();
        if (banMeta != null) {
            banMeta.setDisplayName(plugin.getRawMsg("gui-ban-item"));
            banMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Target: " + target.getName()));
            banItem.setItemMeta(banMeta);
        }

        ItemStack kickItem = new ItemStack(Material.IRON_BOOTS);
        ItemMeta kickMeta = kickItem.getItemMeta();
        if (kickMeta != null) {
            kickMeta.setDisplayName(plugin.getRawMsg("gui-kick-item"));
            kickMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Target: " + target.getName()));
            kickItem.setItemMeta(kickMeta);
        }

        inv.setItem(11, banItem);
        inv.setItem(15, kickItem);
        admin.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (event.getCurrentItem() == null) return;

        String viewTitle = event.getView().getTitle();
        String mainTitle = plugin.getRawMsg("gui-title");

        if (viewTitle.equals(mainTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                String targetName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    openPunishMenu(admin, target);
                }
            }
        } else if (viewTitle.startsWith(plugin.getRawMsg("gui-punish-title").split("%player%")[0])) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked.getItemMeta() == null || clicked.getItemMeta().getLore() == null) return;

            String targetName = ChatColor.stripColor(clicked.getItemMeta().getLore().get(0)).replace("Target: ", "");
            Player target = Bukkit.getPlayerExact(targetName);

            if (target != null) {
                if (clicked.getType() == Material.ANVIL) {
                    plugin.getViolationManager().punishPlayer(target, "Manual Admin Ban");
                    admin.closeInventory();
                } else if (clicked.getType() == Material.IRON_BOOTS) {
                    target.kickPlayer(ChatColor.RED + "Lava Guard AC - Kicked by Admin");
                    admin.closeInventory();
                }
            }
        }
    }
}