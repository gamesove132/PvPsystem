package com.pvpsystem.managers;

import com.pvpsystem.PvpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public class PvpManager {

    private final PvpPlugin plugin;
    // UUID гравця -> мапа (UUID опонента -> секунди залишилось)
    private final Map<UUID, Map<UUID, Integer>> combatMap = new HashMap<>();
    // UUID -> таска таймера
    private final Map<UUID, BukkitTask> timerTasks = new HashMap<>();

    public PvpManager(PvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void tagPlayers(Player attacker, Player victim) {
        int duration = plugin.getConfig().getInt("combat-duration", 30);

        tagOne(attacker, victim, duration);
        tagOne(victim, attacker, duration);

        updateScoreboard(attacker);
        updateScoreboard(victim);
    }

    private void tagOne(Player player, Player opponent, int duration) {
        combatMap.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(opponent.getUniqueId(), duration);

        // Скасуй старий таймер якщо є
        BukkitTask old = timerTasks.remove(player.getUniqueId());
        if (old != null) old.cancel();

        // Запусти новий таймер
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Map<UUID, Integer> opponents = combatMap.get(player.getUniqueId());
                if (opponents == null) {
                    removeFromCombat(player);
                    cancel();
                    return;
                }

                // Зменш таймер для кожного опонента
                Iterator<Map.Entry<UUID, Integer>> it = opponents.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Integer> entry = it.next();
                    int left = entry.getValue() - 1;
                    if (left <= 0) {
                        it.remove();
                    } else {
                        entry.setValue(left);
                    }
                }

                if (opponents.isEmpty()) {
                    removeFromCombat(player);
                    cancel();
                } else {
                    updateScoreboard(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        timerTasks.put(player.getUniqueId(), task);
    }

    public boolean isInCombat(Player player) {
        Map<UUID, Integer> opponents = combatMap.get(player.getUniqueId());
        return opponents != null && !opponents.isEmpty();
    }

    public void removeFromCombat(Player player) {
        combatMap.remove(player.getUniqueId());
        BukkitTask task = timerTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();

        // Повідомлення
        String msg = plugin.getConfig().getString("messages.combat-end",
                "&a✔ Ви вийшли з бою!");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));

        // Відновити звичайний скорборд або прибрати
        resetScoreboard(player);
    }

    public void updateScoreboard(Player player) {
        Map<UUID, Integer> opponents = combatMap.get(player.getUniqueId());
        if (opponents == null || opponents.isEmpty()) {
            resetScoreboard(player);
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        String title = plugin.getConfig().getString("scoreboard.title", "&c&lПВП БІЙ");
        title = ChatColor.translateAlternateColorCodes('&', title);

        Objective obj = board.registerNewObjective("pvp", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Роздільник
        String separator = plugin.getConfig().getString("scoreboard.separator", "&8-----------");
        separator = ChatColor.translateAlternateColorCodes('&', separator);

        int score = opponents.size() + 2;
        obj.getScore(separator).setScore(score--);

        for (Map.Entry<UUID, Integer> entry : opponents.entrySet()) {
            Player opponent = Bukkit.getPlayer(entry.getKey());
            String name = opponent != null ? opponent.getName() : "Офлайн";
            int timeLeft = entry.getValue();

            String line = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("scoreboard.player-line", "&f{player} &7- &c{time}s")
                            .replace("{player}", name)
                            .replace("{time}", String.valueOf(timeLeft)));

            obj.getScore(line).setScore(score--);
        }

        obj.getScore(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("scoreboard.separator", "&8-----------"))).setScore(score);

        player.setScoreboard(board);
    }

    private void resetScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    public void handlePlayerDeath(Player player) {
        removeFromCombat(player);
    }

    public void handlePlayerQuit(Player player) {
        if (isInCombat(player)) {
            // Вбити гравця якщо вийшов під час бою
            if (plugin.getConfig().getBoolean("kill-on-logout", true)) {
                player.setHealth(0);
                String msg = plugin.getConfig().getString("messages.logout-kill",
                        "&c{player} &7вийшов під час бою і був вбитий!")
                        .replace("{player}", player.getName());
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }
            }
            removeFromCombat(player);
        }
    }

    public void cancelAll() {
        timerTasks.values().forEach(BukkitTask::cancel);
        timerTasks.clear();
        combatMap.clear();
    }
}
