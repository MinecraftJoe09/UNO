package me.minecraftjoe09.uno.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.Optional;

public class ItemBuilder {

    public static @NotNull ItemStack simple(@NotNull Material type, @Range(from = 1, to = 64) int amount, @NotNull Component name, @NotNull Component@NotNull... lore) {
        ItemStack item = new ItemStack(type, amount);
        ItemMeta meta = Optional.ofNullable(item.getItemMeta()).orElse(Bukkit.getItemFactory().getItemMeta(type));
        meta.displayName(name);
        meta.lore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }
}
