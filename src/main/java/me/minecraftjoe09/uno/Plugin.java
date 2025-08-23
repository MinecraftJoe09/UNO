package me.minecraftjoe09.uno;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.minecraftjoe09.uno.command.UNOCommand;
import me.minecraftjoe09.uno.listener.GameListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Plugin extends JavaPlugin {

    private static Plugin instance;

    {
        instance = this;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        super.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, new UNOCommand());
        Bukkit.getPluginManager().registerEvents(new GameListener(), getInstance());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Plugin getInstance() {
        return instance;
    }
}
