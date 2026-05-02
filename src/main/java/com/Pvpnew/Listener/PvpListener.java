package com.pvpsystem.listeners;

import com.pvpsystem.PvpPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class PvpListener implements Listener {

    private final PvpPlugin plugin;

    public PvpListener(PvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (attacker.equals(victim)) return;

        plugin.getPvpManager().tagPlayers(attacker, victim);

        // Повідомлення про вхід в бій
        if (!plugin.getPvpManager().isInCombat(attacker)) return;
        String msg = plugin.getConfig().getString("messages.combat-start", "&c⚔ Ви в бою!");
        attacker.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        victim.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        if (!plugin.getPvpManager().isInCombat(player)) return;

        // Заборонені команди з конфігу
        List<String> blocked = plugin.getConfig().getStringList("blocked-commands");
        String cmd = e.getMessage().toLowerCase().split(" ")[0].substring(1); // без /

        // Блокуємо всі команди якщо block-all-commands: true
        boolean blockAll = plugin.getConfig().getBoolean("block-all-commands", true);

        if (blockAll || blocked.contains(cmd)) {
            e.setCancelled(true);
            String msg = plugin.getConfig().getString("messages.command-blocked",
                    "&cНе можна використовувати команди під час бою!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.getPvpManager().handlePlayerDeath(e.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getPvpManager().handlePlayerQuit(e.getPlayer());
    }
}
