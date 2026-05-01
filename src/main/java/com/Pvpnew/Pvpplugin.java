package com.pvpsystem;

import com.pvpsystem.commands.PvpCommand;
import com.pvpsystem.listeners.PvpListener;
import com.pvpsystem.managers.PvpManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PvpPlugin extends JavaPlugin {

    private static PvpPlugin instance;
    private PvpManager pvpManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        pvpManager = new PvpManager(this);

        getServer().getPluginManager().registerEvents(new PvpListener(this), this);

        PvpCommand cmd = new PvpCommand(this);
        getCommand("pvptag").setExecutor(cmd);

        getLogger().info("PvpSystem enabled!");
    }

    @Override
    public void onDisable() {
        if (pvpManager != null) pvpManager.cancelAll();
        getLogger().info("PvpSystem disabled!");
    }

    public static PvpPlugin getInstance() { return instance; }
    public PvpManager getPvpManager() { return pvpManager; }
}
