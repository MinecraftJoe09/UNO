package me.minecraftjoe09.uno.game;

import me.minecraftjoe09.uno.Plugin;
import me.minecraftjoe09.uno.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class Card {

    public static final NamespacedKey TYPE_KEY = new NamespacedKey(Plugin.getInstance(), "type");
    public static final NamespacedKey COLOR_KEY = new NamespacedKey(Plugin.getInstance(), "color");

    private final Type type;
    private Color color;

    public Card(Type type, Color color) {
        this.type = type;
        this.color = color;
    }

    public Type getType() {
        return this.type;
    }

    public @Nullable Color getColor() {
        return this.color;
    }

    public void assignColor(@NotNull Color color) {
        if (this.color != null) {
            return;
        }
        this.color = color;
    }

    public void resetColor() {
        if (!this.type.isWild()) {
            return;
        }
        this.color = null;
    }

    public void play(Game game, UNOPlayer unoPlayer) {
        this.type.behavior.accept(game, unoPlayer, this);
    }

    public static @Nullable Card ofItem(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(TYPE_KEY)) {
            return null;
        }
        Type type = Type.fetchFromContainer(container);
        if (type == null || !type.isWild() && !container.has(COLOR_KEY)) {
            return null;
        }
        Color color = Color.fetchFromContainer(container);
        return new Card(type, color);
    }

    public ItemStack toItem() {
        ItemStack item = new ItemStack(this.color != null ? this.color.getMaterial() : Color.WILD);
        ItemMeta meta = Optional.ofNullable(item.getItemMeta()).orElse(Bukkit.getItemFactory().getItemMeta(item.getType()));
        meta.displayName(
                Component.text(this.type.getName())
                        .color(TextColor.color(this.color != null ? this.color.getChatColor() : 0xFFFFFF))
                        .decoration(TextDecoration.ITALIC, false)
        );
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(TYPE_KEY, PersistentDataType.BYTE, (byte) this.type.ordinal());
        if (this.color != null) {
            container.set(COLOR_KEY, PersistentDataType.BYTE, (byte) this.color.ordinal());
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Card card)) {
            return false;
        }
        return card.type == this.type && (card.color == null || this.color == null || card.color == this.color);
    }

    @Override
    public int hashCode() {
        return this.type.hashCode() ^ (this.color != null ? this.color.hashCode() : 0);
    }

    public enum Type {

        ZERO("0", Behavior.DEFAULT),
        ONE("1", Behavior.DEFAULT),
        TWO("2", Behavior.DEFAULT),
        THREE("3", Behavior.DEFAULT),
        FOUR("4", Behavior.DEFAULT),
        FIVE("5", Behavior.DEFAULT),
        SIX("6", Behavior.DEFAULT),
        SEVEN("7", Behavior.DEFAULT),
        EIGHT("8", Behavior.DEFAULT),
        NINE("9", Behavior.DEFAULT),
        SKIP("\uD83D\uDEAB", (game, unoPlayer, card) -> {
            game.skipTurn();
        }),
        REVERSE("\uD83D\uDD03", (game, unoPlayer, card) -> {
            game.getPlayers().reverseDirection();
            if (game.getPlayers().getActivePlayers().size() == 2) {
                game.skipTurn();
                return;
            }
            game.nextTurn();
        }),
        DRAW2("+2", (game, unoPlayer, card) -> {
            game.addPendingDraws(2);
            game.nextTurn();
        }),
        DRAW4("+4", (game, unoPlayer, card) -> {
            game.addPendingDraws(4);
            if (card.getColor() == null) {
                unoPlayer.transformInv(19, Color.RED.toItem(), null, Color.YELLOW.toItem(), null, Color.GREEN.toItem(), null, Color.BLUE.toItem());
                unoPlayer.startPickingColor();
            } else {
                game.nextTurn();
            }
        }),
        WILD("■", (game, unoPlayer, card) -> {
            if (card.getColor() == null) {
                unoPlayer.transformInv(19, Color.RED.toItem(), null, Color.YELLOW.toItem(), null, Color.GREEN.toItem(), null, Color.BLUE.toItem());
                unoPlayer.startPickingColor();
            } else {
                game.nextTurn();
            }
        });

        private final String name;
        private final Behavior behavior;

        Type(String name, Behavior behavior) {
            this.name = name;
            this.behavior = behavior;
        }

        public String getName() {
            return this.name;
        }

        public boolean isSpecial() {
            return ordinal() > NINE.ordinal();
        }

        public boolean isWild() {
            return ordinal() > DRAW2.ordinal();
        }

        @Override
        public String toString() {
            return this.name;
        }

        public static @Nullable Type fetchFromContainer(@NotNull PersistentDataContainer container) {
            Byte ordinal = container.get(Card.TYPE_KEY, PersistentDataType.BYTE);
            if (ordinal == null || ordinal < 0 || ordinal >= Type.values().length) {
                return null;
            }
            return Type.values()[ordinal];
        }

        @FunctionalInterface
        private interface Behavior {

            Behavior DEFAULT = (game, unoPlayer, card) -> {
                game.nextTurn();
            };

            void accept(@NotNull Game game, @NotNull UNOPlayer player, @NotNull Card card);
        }
    }

    public enum Color {

        RED(0xFF5555, Material.RED_STAINED_GLASS_PANE),
        YELLOW(0xFFFF55, Material.YELLOW_STAINED_GLASS_PANE),
        GREEN(0x55FF55, Material.GREEN_STAINED_GLASS_PANE),
        BLUE(0x5555FF, Material.BLUE_STAINED_GLASS_PANE);

        public static final Material WILD = Material.BLACK_STAINED_GLASS_PANE;

        private final int color;
        private final Material material;

        Color(int color, Material material) {
            this.color = color;
            this.material = material;
        }

        public int getChatColor() {
            return this.color;
        }

        public Material getMaterial() {
            return this.material;
        }

        public ItemStack toItem() {
            return ItemBuilder.simple(
                    this.material,
                    1,
                    Component.text(name())
                            .color(TextColor.color(this.color))
                            .decoration(TextDecoration.ITALIC, false)
            );
        }

        public static @Nullable Color fetchFromContainer(@NotNull PersistentDataContainer container) {
            Byte ordinal = container.get(Card.COLOR_KEY, PersistentDataType.BYTE);
            if (ordinal == null || ordinal < 0 || ordinal >= Color.values().length) {
                return null;
            }
            return Color.values()[ordinal];
        }
    }
}
