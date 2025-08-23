package me.minecraftjoe09.uno.command;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import me.minecraftjoe09.uno.argument.GameOwnerArgument;
import me.minecraftjoe09.uno.except.*;
import me.minecraftjoe09.uno.game.Game;
import me.minecraftjoe09.uno.game.RuleSet;
import me.minecraftjoe09.uno.game.UNOPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public class UNOCommand implements LifecycleEventHandler<@NotNull ReloadableRegistrarEvent<@NotNull Commands>> {

    private static final TextComponent ERR_NO_OWNER =
            Component.text("You don't own a game")
                    .color(TextColor.color(0xFF5555));
    private static final TextComponent ERR_NOT_IN_GAME =
            Component.text("You are not in a game")
                    .color(TextColor.color(0xFF5555));

    @Override
    public void run(@NotNull ReloadableRegistrarEvent<@NotNull Commands> event) {
        event.registrar().register(literal("uno")
                .requires(cmd -> cmd.getSender() instanceof Player)
                .then(literal("create")
                        .requires(cmd -> cmd.getSender() instanceof Player player && Game.ofOwner(player) == null)
                        .executes(cmd -> {
                            Player player = (Player) cmd.getSource().getSender();
                            if (Game.ofOwner(player) != null) {
                                player.sendMessage(
                                        Component.text("You already own a game")
                                                .color(TextColor.color(0xFF5555))
                                );
                                return 0;
                            }
                            new Game(player, RuleSet.newClassicRuleSet());
                            player.sendMessage(
                                    Component.text("Successfully created a game")
                                            .color(TextColor.color(0x55FF55))
                            );
                            return 1;
                        })
                )
                .then(literal("invite")
                        .requires(cmd -> cmd.getSender() instanceof Player player && Game.ofOwner(player) != null)
                        .then(argument("target", ArgumentTypes.player())
                                .executes(cmd -> {
                                    Player player = (Player) cmd.getSource().getSender();
                                    if (Game.ofOwner(player) == null) {
                                        player.sendMessage(ERR_NO_OWNER);
                                        return 0;
                                    }
                                    Player target = cmd.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(cmd.getSource()).getFirst();
                                    target.sendMessage(
                                            player.displayName()
                                                    .append(
                                                            Component.text(" invited you to ")
                                                    )
                                                    .color(TextColor.color(0xFFAA00))
                                                    .append(
                                                            Component.text("join their UNO game")
                                                                    .color(TextColor.color(0xFFFF55))
                                                                    .hoverEvent(HoverEvent.showText(
                                                                            Component.text("Click here to join the game")
                                                                    ))
                                                                    .clickEvent(ClickEvent.runCommand("uno join " + player.getName()))
                                                    )
                                    );
                                    player.sendMessage(
                                            Component.text("Successfully sent the invite")
                                                    .color(TextColor.color(0x55FF55))
                                    );
                                    return 1;
                                })
                        )
                )
                .then(literal("join")
                        .then(argument("owner", new GameOwnerArgument())
                                .executes(cmd -> {
                                    Player player = (Player) cmd.getSource().getSender();
                                    Game game = cmd.getArgument("owner", Game.class);
                                    try {
                                        game.join(player);
                                    } catch (GameAlreadyRunningException | GameFullException | PlayerAlreadyKnownException e) {
                                        player.sendMessage(
                                                Component.text(e.getMessage())
                                                        .color(TextColor.color(0xFF5555))
                                        );
                                        return 0;
                                    }
                                    player.sendMessage(
                                            Component.text("Successfully joined the game")
                                                    .color(TextColor.color(0x55FF55))
                                    );
                                    return 1;
                                })
                        )
                )
                .then(literal("leave")
                        .requires(cmd -> cmd.getSender() instanceof Player player && Game.ofOwner(player) != null)
                        .executes(cmd -> {
                            Player player = (Player) cmd.getSource().getSender();
                            if (!Game.isPlaying(player)) {
                                player.sendMessage(ERR_NOT_IN_GAME);
                                return 0;
                            }
                            Game.fireLeave(player);
                            player.sendMessage(
                                    Component.text("Successfully left the game")
                                            .color(TextColor.color(0x55FF55))
                            );
                            return 1;
                        })
                )
                .then(literal("kick")
                        .requires(cmd -> cmd.getSender() instanceof Player player && Game.ofOwner(player) != null)
                        .then(argument("targets", ArgumentTypes.players())
                                .executes(cmd -> {
                                    Player player = (Player) cmd.getSource().getSender();
                                    Game game = Game.ofOwner(player);
                                    if (game == null) {
                                        player.sendMessage(ERR_NO_OWNER);
                                        return 0;
                                    }
                                    List<Player> targets = cmd.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(cmd.getSource());
                                    List<UNOPlayer> list = game.getPlayers().stream().filter(p -> targets.contains(p.getPlayer())).collect(Collectors.toList());
                                    list.removeIf(p -> player.equals(p.getPlayer())); // don't kick owner
                                    list.forEach(game::leave);
                                    return list.size();
                                })
                        )
                )
                .then(literal("bot")
                        .requires(cmd -> cmd.getSender() instanceof Player player && Game.ofOwner(player) != null)
                        .then(literal("add")
                                .executes(cmd -> {
                                    Player player = (Player) cmd.getSource().getSender();
                                    Game game = Game.ofOwner(player);
                                    if (game == null) {
                                        player.sendMessage(ERR_NO_OWNER);
                                        return 0;
                                    }
                                    try {
                                        game.addBot();
                                    } catch (GameAlreadyRunningException | GameFullException e) {
                                        player.sendMessage(
                                                Component.text(e.getMessage())
                                                        .color(TextColor.color(0xFF5555))
                                        );
                                        return 0;
                                    }
                                    player.sendMessage(
                                            Component.text("Successfully added bot")
                                                    .color(TextColor.color(0x55FF55))
                                    );
                                    return 1;
                                })
                        )
                        .then(literal("remove")
                                .executes(cmd -> {
                                    Player player = (Player) cmd.getSource().getSender();
                                    Game game = Game.ofOwner(player);
                                    if (game == null) {
                                        player.sendMessage(ERR_NO_OWNER);
                                        return 0;
                                    }
                                    try {
                                        game.removeBot();
                                    } catch (GameAlreadyRunningException e) {
                                        player.sendMessage(
                                                Component.text(e.getMessage())
                                                        .color(TextColor.color(0xFF5555))
                                        );
                                        return 0;
                                    }
                                    return 1;
                                })
                        )
                )
                .then(literal("start")
                        .requires(cmd -> cmd.getSender() instanceof Player player && Game.ofOwner(player) != null)
                        .executes(cmd -> {
                            Player player = (Player) cmd.getSource().getSender();
                            Game game = Game.ofOwner(player);
                            if (game == null) {
                                player.sendMessage(ERR_NO_OWNER);
                                return 0;
                            }
                            try {
                                game.start();
                            } catch (GameAlreadyRunningException | NotEnoughPlayersException e) {
                                player.sendMessage(
                                        Component.text(e.getMessage())
                                                .color(TextColor.color(0xFF5555))
                                );
                                return 0;
                            }
                            return 1;
                        })
                )
                .then(literal("open")
                        .requires(cmd -> cmd.getSender() instanceof Player player && Game.isPlaying(player))
                        .executes(cmd -> {
                            Player player = (Player) cmd.getSource().getSender();
                            if (!Game.isPlaying(player)) {
                                player.sendMessage(ERR_NOT_IN_GAME);
                                return 0;
                            }
                            try {
                                Game.fireOpen(player);
                            } catch (GameNotRunningException e) {
                                player.sendMessage(
                                        Component.text(e.getMessage())
                                                .color(TextColor.color(0xFF5555))
                                );
                            }
                            return 1;
                        })
                )
                .build()
        );
    }
}
