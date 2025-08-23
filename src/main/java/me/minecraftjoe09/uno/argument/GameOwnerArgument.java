package me.minecraftjoe09.uno.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import me.minecraftjoe09.uno.game.Game;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class GameOwnerArgument implements CustomArgumentType.Converted<Game, String> {

    @Override
    public @NotNull Game convert(@NotNull String name) {
        Game game = Game.ofOwner(Bukkit.getPlayer(name));
        if (game == null) {
            throw new IllegalArgumentException("That player doesn't own a game");
        }
        return game;
    }

    @Override
    public @NotNull ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public <S> @NotNull CompletableFuture<Suggestions> listSuggestions(@NotNull CommandContext<S> context, @NotNull SuggestionsBuilder builder) {
        Game.getOwnerSuggestions().forEach(builder::suggest);
        return builder.buildFuture();
    }
}
