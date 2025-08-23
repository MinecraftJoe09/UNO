package me.minecraftjoe09.uno.listener;

import me.minecraftjoe09.uno.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class GameListener implements Listener {

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        Game.fireInventoryClick(event);
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        Game.fireClose((Player) event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Game.fireLeave(event.getPlayer());
    }
}
