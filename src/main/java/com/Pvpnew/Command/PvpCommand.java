package com.Pvpnew.Commands;

import com.pvpsystem.PvpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PvpCommand implements CommandExecutor {

    private final PvpPlugin plugin;

    public PvpCommand(PvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvpsystem.admin")) {
            sender.sendMessage(ChatColor.RED + "Немає дозволу!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§e/pvptag reload §7- перезавантажити конфіг");
            sender.sendMessage("§e/pvptag untag <гравець> §7- зняти бойовий тег");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Конфіг перезавантажено!");
            return true;
        }

        if (args[0].equalsIgnoreCase("untag") && args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Гравець не знайдений!");
                return true;
            }
            plugin.getPvpManager().removeFromCombat(target);
            sender.sendMessage(ChatColor.GREEN + "Бойовий тег знятий з " + target.getName());
            return true;
        }

        return true;
    }
}
