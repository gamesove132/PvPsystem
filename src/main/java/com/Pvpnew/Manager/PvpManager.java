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
    private final Map<UUID, Map<UUID, Integer>> combatMap = new HashMap<>();
    private final Map<UUID, BukkitTask> timerTasks = new HashMap<>();
    // Зберігаємо НАШ скорборд — один об'єкт на гравця, не створюємо новий кожного разу
    private final Map<UUID, Scoreboard> combatBoards = new HashMap<>();
    // Таск що кожні 2 тіки повертає наш скорборд якщо TAB перезаписав
    private BukkitTask refreshTask;

    public PvpManager(PvpPlugin plugin) {
        this.plugin = plugin;
        startRefreshTask();
    }

    /**
     * ГОЛОВНИЙ ФІКС: TAB оновлює скорборд кожну секунду.
     * Ми перевіряємо кожні 2 тіки (0.1 сек) і якщо TAB перезаписав — повертаємо наш.
     */
    private void startRefreshTask() {
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(combatMap.keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !isInCombat(p)) continue;
                    Scoreboard ours = combatBoards.get(uuid);
                    if (ours == null) continue;
                    // Якщо TAB замінив наш скорборд — повертаємо назад
                    if (!p.getScoreboard().equals(ours)) {
                        p.setScoreboard(ours);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
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

        BukkitTask old = timerTasks.remove(player.getUniqueId());
        if (old != null) old.cancel();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Map<UUID, Integer> opponents = combatMap.get(player.getUniqueId());
                if (opponents == null) {
                    removeFromCombat(player);
                    cancel();
                    return;
                }

                Iterator<Map.Entry<UUID, Integer>> it = opponents.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Integer> entry = it.next();
                    int left = entry.getValue() - 1;
                    if (left <= 0) it.remove();
                    else entry.setValue(left);
                }

                if (opponents.isEmpty()) {
                    removeFromCombat(player);
                    cancel();
                } else {
                    // Оновлюємо ВМІСТ існуючого скорборду — не створюємо новий!
                    refreshScoreboardContent(player);
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
        combatBoards.remove(player.getUniqueId());

        BukkitTask task = timerTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();

        String msg = plugin.getConfig().getString("messages.combat-end", "&a✔ Ви вийшли з бою!");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));

        // Скидаємо на порожній скорборд — TAB сам відновить свій
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    public void updateScoreboard(Player player) {
        Map<UUID, Integer> opponents = combatMap.get(player.getUniqueId());
        if (opponents == null || opponents.isEmpty()) {
            resetScoreboard(player);
            return;
        }

        // Створюємо скорборд ОДИН РАЗ при вході в бій
        if (!combatBoards.containsKey(player.getUniqueId())) {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            combatBoards.put(player.getUniqueId(), board);
            player.setScoreboard(board);
        }

        refreshScoreboardContent(player);
    }

    /**
     * Оновлює вміст існуючого скорборду без створення нового об'єкта.
     * Це вирішує конфлікт з TAB — ми не замінюємо сам об'єкт скорборду,
     * а лише змінюємо його вміст.
     */
    private void refreshScoreboardContent(Player player) {
        Scoreboard board = combatBoards.get(player.getUniqueId());
        if (board == null) {
            updateScoreboard(player);
            return;
        }

        Map<UUID, Integer> opponents = combatMap.get(player.getUniqueId());
        if (opponents == null || opponents.isEmpty()) return;

        // Перереєструємо objective з новим вмістом
        Objective old = board.getObjective("pvp");
        if (old != null) old.unregister();

        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("scoreboard.title", "&c&l⚔ ПВП БІЙ ⚔"));

        Objective obj = board.registerNewObjective("pvp", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        String separator = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("scoreboard.separator", "&8&m-----------"));

        int score = opponents.size() + 2;
        obj.getScore(separator).setScore(score--);

        for (Map.Entry<UUID, Integer> entry : opponents.entrySet()) {
            Player opponent = Bukkit.getPlayer(entry.getKey());
            String name = opponent != null ? opponent.getName() : "Офлайн";
            int timeLeft = entry.getValue();

            String line = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("scoreboard.player-line", "&f{player} &7- &c{time}с")
                            .replace("{player}", name)
                            .replace("{time}", String.valueOf(timeLeft)));

            obj.getScore(line).setScore(score--);
        }

        // Якщо TAB встиг замінити — повертаємо наш
        if (!player.getScoreboard().equals(board)) {
            player.setScoreboard(board);
        }
    }

    private void resetScoreboard(Player player) {
        combatBoards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    public void handlePlayerDeath(Player player) {
        removeFromCombat(player);
    }

    public void handlePlayerQuit(Player player) {
        if (isInCombat(player)) {
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
        if (refreshTask != null) refreshTask.cancel();
        timerTasks.values().forEach(BukkitTask::cancel);
        timerTasks.clear();
        combatMap.clear();
        combatBoards.clear();
    }
}
