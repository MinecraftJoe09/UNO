package me.minecraftjoe09.uno.game;

import me.minecraftjoe09.uno.Plugin;
import me.minecraftjoe09.uno.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class UNOPlayer {

    private final Player player;
    private Inventory inv;
    private final List<Card> cards = new LinkedList<>();
    private BukkitTask timer;
    private boolean uno = false;
    private boolean pickingColor = false;
    private int penaltyDraws = 0;

    private UNOPlayer(Player player) {
        this.player = player;
    }

    public static @NotNull UNOPlayer newPlayer(@NotNull Player player) {
        return new UNOPlayer(player);
    }

    public static @NotNull UNOPlayer newBot() {
        return new UNOPlayer(null);
    }

    public void setupInventory(@NotNull Inventory inv) {
        this.inv = inv;
    }

    public void transformInv(int slot, ItemStack@NotNull... items) {
        for (int i = 0; i < items.length; i++) {
            this.inv.setItem(slot + i, items[i]);
        }
    }

    public void playCard(@NotNull Card card) {
        this.cards.remove(card);
        if (isBot()) {
            return;
        }
        this.player.getInventory().removeItem(card.toItem());
    }

    public void drawCard(@NotNull Card card) {
        this.cards.add(card);
        if (isBot()) {
            return;
        }
        this.player.getInventory().addItem(card.toItem());
    }

    public void startTurn(int time, @NotNull Consumer<@NotNull UNOPlayer> ai) {
        setStatus(ItemBuilder.simple(
                Material.LIME_STAINED_GLASS_PANE,
                1,
                Component.text("It's your turn.")
                        .color(TextColor.color(0x55FF55))
                        .decoration(TextDecoration.ITALIC, false)
        ));
        this.timer = new BukkitRunnable() {

            int count = 9;

            @Override
            public void run() {
                UNOPlayer.this.inv.setItem(36 + --count, ItemBuilder.simple(
                        Material.RED_STAINED_GLASS_PANE,
                        1,
                        Component.text("It's your turn.")
                                .color(TextColor.color(0xFF5555))
                                .decoration(TextDecoration.ITALIC, false)
                ));
                if (count == 0) {
                    cancel();
                    ai.accept(UNOPlayer.this);
                }
            }
        }.runTaskTimer(Plugin.getInstance(), time * 20L / 9, time * 20L / 9);
        if (isBot()) {
            new BukkitRunnable() {

                @Override
                public void run() {
                    ai.accept(UNOPlayer.this);
                }
            }.runTaskLater(Plugin.getInstance(), ThreadLocalRandom.current().nextInt(10, 110));
        }
    }

    public void endTurn() {
        this.timer.cancel();
        setStatus(ItemBuilder.simple(
                Material.RED_STAINED_GLASS_PANE,
                1,
                Component.text("It's not your turn.")
                        .color(TextColor.color(0xFF5555))
                        .decoration(TextDecoration.ITALIC, false)
        ));
        if (this.cards.size() != 1) {
            this.uno = false;
        }
    }

    public void skipTurn() {
        setStatus(ItemBuilder.simple(
                Material.RED_STAINED_GLASS_PANE,
                1,
                Component.text("You have been skipped!")
                        .color(TextColor.color(0xFF5555))
                        .decoration(TextDecoration.ITALIC, false)
        ));
    }

    public void setTurn(@Range(from = -1, to = 8) int index) {
        this.inv.remove(Material.LIME_CANDLE);
        if (index == -1) {
            return;
        }
        this.inv.setItem(9 + index, ItemBuilder.simple(
                Material.LIME_CANDLE,
                1,
                Component.text("Current Player")
                        .color(TextColor.color(0x55FF55))
                        .decoration(TextDecoration.ITALIC, false)
        ));
    }

    public void updateTopCard(@NotNull Card top) {
        this.inv.setItem(22, top.toItem());
    }

    public void updateCardCount(@Range(from = -1, to = 8) int index, int cards) {
        if (index == -1) {
            return;
        }
        ItemStack item = this.inv.getItem(index);
        if (item == null) {
            item = new ItemStack(Material.PAPER);
        }
        item.setAmount(cards);
        this.inv.setItem(index, item);
    }

    public boolean callUNO() {
        if (this.uno || this.cards.size() != 2) {
            return false;
        }
        this.uno = true;
        return true;
    }

    public boolean isUNO() {
        return this.uno;
    }

    public void addPenaltyDraws(int draws) {
        this.penaltyDraws += draws;
    }

    public boolean hasPenaltyDraws() {
        return this.penaltyDraws > 0;
    }

    public void startPickingColor() {
        this.pickingColor = true;
    }

    public void stopPickingColor() {
        this.pickingColor = false;
    }

    public boolean isPickingColor() {
        return this.pickingColor;
    }

    public void setStatus(ItemStack item) {
        for (int i = 36; i < 45; i++) {
            this.inv.setItem(i, item);
        }
    }

    public void openGameInv() {
        if (isBot()) {
            return;
        }
        this.player.openInventory(this.inv);
    }

    public String getName() {
        return !isBot() ? this.player.getName() : "Bot";
    }

    public Player getPlayer() {
        return this.player;
    }

    public Inventory getGameInv() {
        return this.inv;
    }

    public @NotNull List<Card> getCards() {
        return this.cards;
    }

    public boolean isBot() {
        return this.player == null;
    }

    public boolean isDone() {
        return this.cards.isEmpty();
    }

    public void reset() {
        this.penaltyDraws = 0;

        this.inv.close();
        this.inv = null;

        this.cards.clear();

        if (isBot()) {
            return;
        }

        for (ItemStack item : this.player.getInventory().getContents()) {
            if (item == null || Card.ofItem(item) == null) {
                continue;
            }
            this.player.getInventory().removeItem(item);
        }
    }
}
