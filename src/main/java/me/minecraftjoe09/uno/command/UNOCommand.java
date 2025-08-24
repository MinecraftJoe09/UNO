package me.minecraftjoe09.uno.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import me.minecraftjoe09.uno.argument.GameOwnerArgument;
import me.minecraftjoe09.uno.except.*;
import me.minecraftjoe09.uno.game.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
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
                .then(literal("rule")
                        .requires(cmd -> cmd.getSender() instanceof Player player && Game.ofOwner(player) != null)
                        .then(buildEnumListRuleNode(
                                "initialDeck",
                                Card.Type.class,
                                rules -> rules.initialDeck,
                                (list, type) -> {
                                    for (Card.Color color : Card.Color.values()) {
                                        list.add(new Card(type, color));
                                    }
                                },
                                (list, type) -> {
                                    for (Card.Color color : Card.Color.values()) {
                                        list.remove(new Card(type, color));
                                    }
                                },
                                card -> Component.space().append(card.toItem().displayName())
                        ))
                        .then(buildIntRuleNode("initialCards", IntegerArgumentType.integer(2, 27), rules -> rules.initialCards, (rules, value) -> rules.initialCards = value))
                        .then(buildIntRuleNode("maxCards", IntegerArgumentType.integer(2, 27), rules -> rules.maxCards, (rules, value) -> rules.maxCards = value))
                        .then(buildEnumRuleNode("initialDirection", Direction.class, rules -> rules.initialDirection, (rules, value) -> rules.initialDirection = value))
                        .then(buildBoolRuleNode("drawTwoStacking", BoolArgumentType.bool(), rules -> rules.drawTwoStacking, (rules, value) -> rules.drawTwoStacking = value))
                        .then(buildBoolRuleNode("drawFourStacking", BoolArgumentType.bool(), rules -> rules.drawFourStacking, (rules, value) -> rules.drawFourStacking = value))
                        .then(buildBoolRuleNode("playAfterDrawTwo", BoolArgumentType.bool(), rules -> rules.playAfterDrawTwo, (rules, value) -> rules.playAfterDrawTwo = value))
                        .then(buildBoolRuleNode("playAfterDrawFour", BoolArgumentType.bool(), rules -> rules.playAfterDrawFour, (rules, value) -> rules.playAfterDrawFour = value))
                        .then(buildBoolRuleNode("jumpIn", BoolArgumentType.bool(), rules -> rules.jumpIn, (rules, value) -> rules.jumpIn = value))
                        .then(buildBoolRuleNode("endOnAction", BoolArgumentType.bool(), rules -> rules.endOnAction, (rules, value) -> rules.endOnAction = value))
                        .then(buildIntRuleNode("timeToPlay", IntegerArgumentType.integer(10, 30), rules -> rules.timeToPlay, (rules, value) -> rules.timeToPlay = value))
                        .then(buildIntRuleNode("penaltyDraws", IntegerArgumentType.integer(0, 4), rules -> rules.penaltyDraws, (rules, value) -> rules.penaltyDraws = value))
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

    public static ArgumentBuilder<CommandSourceStack, ?> buildBoolRuleNode(String name, BoolArgumentType type, Function<RuleSet, Boolean> getter, BiFunction<RuleSet, Boolean, Boolean> setter) {
        return literal(name)
                .executes(cmd -> {
                    Player player = (Player) cmd.getSource().getSender();
                    Game game = Game.ofOwner(player);
                    if (game == null) {
                        player.sendMessage(ERR_NO_OWNER);
                        return 0;
                    }
                    boolean value = getter.apply(game.getRules());
                    player.sendMessage(
                            Component.text(name + " is currently set to " + value)
                    );
                    return value ? 1 : 0;
                })
                .then(argument("value", type)
                        .executes(cmd -> {
                            Player player = (Player) cmd.getSource().getSender();
                            Game game = Game.ofOwner(player);
                            if (game == null) {
                                player.sendMessage(ERR_NO_OWNER);
                                return 0;
                            }
                            return setter.apply(game.getRules(), cmd.getArgument("value", boolean.class)) ? 1 : 0;
                        })
                );
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildIntRuleNode(String name, IntegerArgumentType type, Function<RuleSet, Integer> getter, BiFunction<RuleSet, Integer, Integer> setter) {
        return literal(name)
                .executes(cmd -> {
                    Player player = (Player) cmd.getSource().getSender();
                    Game game = Game.ofOwner(player);
                    if (game == null) {
                        player.sendMessage(ERR_NO_OWNER);
                        return 0;
                    }
                    int value = getter.apply(game.getRules());
                    player.sendMessage(
                            Component.text(name + " is currently set to " + value)
                    );
                    return value;
                })
                .then(argument("value", type)
                        .executes(cmd -> {
                            Player player = (Player) cmd.getSource().getSender();
                            Game game = Game.ofOwner(player);
                            if (game == null) {
                                player.sendMessage(ERR_NO_OWNER);
                                return 0;
                            }
                            return setter.apply(game.getRules(), cmd.getArgument("value", int.class));
                        })
                );
    }

    public static <T extends Enum<T>> ArgumentBuilder<CommandSourceStack, ?> buildEnumRuleNode(String name, Class<T> type, Function<RuleSet, T> getter, BiFunction<RuleSet, T, T> setter) {
        ArgumentBuilder<CommandSourceStack, ?> builder = literal(name)
                .executes(cmd -> {
                    Player player = (Player) cmd.getSource().getSender();
                    Game game = Game.ofOwner(player);
                    if (game == null) {
                        player.sendMessage(ERR_NO_OWNER);
                        return 0;
                    }
                    T value = getter.apply(game.getRules());
                    player.sendMessage(
                            Component.text(name + " is currently set to " + value)
                    );
                    return 1;
                });
        for (T constant : type.getEnumConstants()) {
            builder.then(literal(constant.name())
                    .executes(cmd -> {
                        Player player = (Player) cmd.getSource().getSender();
                        Game game = Game.ofOwner(player);
                        if (game == null) {
                            player.sendMessage(ERR_NO_OWNER);
                            return 0;
                        }
                        setter.apply(game.getRules(), constant);
                        return 1;
                    })
            );
        }
        return builder;
    }

    public static <T extends Enum<T>, V> ArgumentBuilder<CommandSourceStack, ?> buildEnumListRuleNode(String name, Class<T> type, Function<RuleSet, List<V>> getter, BiConsumer<List<V>, T> adder, BiConsumer<List<V>, T> remover, Function<V, TextComponent> formatter) {
        ArgumentBuilder<CommandSourceStack, ?> builder = literal(name)
                .executes(cmd -> {
                    Player player = (Player) cmd.getSource().getSender();
                    Game game = Game.ofOwner(player);
                    if (game == null) {
                        player.sendMessage(ERR_NO_OWNER);
                        return 0;
                    }
                    List<V> list = getter.apply(game.getRules());
                    TextComponent.Builder msg = Component.text();
                    msg.append(
                            Component.text("%s currently contains the following %d values:".formatted(name, list.size()))
                    );
                    msg.appendNewline();
                    for (V value : list) {
                        msg.append(formatter.apply(value));
                    }
                    player.sendMessage(msg);
                    return list.size();
                });
        ArgumentBuilder<CommandSourceStack, ?> addBuilder = literal("add");
        for (T constant : type.getEnumConstants()) {
            addBuilder.then(literal(constant.toString())
                    .executes(cmd -> {
                        Player player = (Player) cmd.getSource().getSender();
                        Game game = Game.ofOwner(player);
                        if (game == null) {
                            player.sendMessage(ERR_NO_OWNER);
                            return 0;
                        }
                        List<V> list = getter.apply(game.getRules());
                        int oldSize = list.size();
                        adder.accept(list, constant);
                        return list.size() - oldSize;
                    })
            );
        }
        builder.then(addBuilder);
        ArgumentBuilder<CommandSourceStack, ?> removeBuilder = literal("remove");
        for (T constant : type.getEnumConstants()) {
            removeBuilder.then(literal(constant.toString())
                    .executes(cmd -> {
                        Player player = (Player) cmd.getSource().getSender();
                        Game game = Game.ofOwner(player);
                        if (game == null) {
                            player.sendMessage(ERR_NO_OWNER);
                            return 0;
                        }
                        List<V> list = getter.apply(game.getRules());
                        int oldSize = list.size();
                        remover.accept(list, constant);
                        return oldSize - list.size();
                    })
            );
        }
        builder.then(removeBuilder);
        return builder;
    }
}
