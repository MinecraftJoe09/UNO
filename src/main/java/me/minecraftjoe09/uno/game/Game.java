package me.minecraftjoe09.uno.game;

import me.minecraftjoe09.uno.Plugin;
import me.minecraftjoe09.uno.except.*;
import me.minecraftjoe09.uno.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Game {

    private static final Map<Player, Game> games = new HashMap<>();

    private static final ItemStack DRAW_ITEM = ItemBuilder.simple(
            Material.WHITE_STAINED_GLASS_PANE,
            1,
            Component.text("Draw Card")
                    .color(TextColor.color(0xFFFFFF))
                    .decoration(TextDecoration.ITALIC, false)
    );
    private static final ItemStack UNO_ITEM = ItemBuilder.simple(
            Material.BELL,
            1,
            Component.text("UNO!")
                    .color(TextColor.color(0xFFFF00))
    );

    private final Stack<Card> deck = new Stack<>();
    private final Stack<Card> pile = new Stack<>();
    private final PlayerList players = new PlayerList();
    private final List<UNOPlayer> winners = new ArrayList<>();
    private final RuleSet rules;
    private int pendingDraws = 0;

    public Game(@NotNull Player owner, @NotNull RuleSet rules) {
        this.rules = rules;
        join(owner);
        games.put(owner, this);
    }

    public static @Nullable Game ofOwner(Player owner) {
        return games.get(owner);
    }

    public static boolean isPlaying(@NotNull Player player) {
        for (Game game : games.values()) {
            for (UNOPlayer unoPlayer : game.players) {
                if (player.equals(unoPlayer.getPlayer())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Set<String> getOwnerSuggestions() {
        return games.keySet().stream().map(Player::getName).collect(Collectors.toSet());
    }

    public void join(@NotNull Player player) throws GameAlreadyRunningException, GameFullException, PlayerAlreadyKnownException {
        if (isRunning()) {
            throw new GameAlreadyRunningException();
        }
        if (this.players.size() == 10) {
            throw new GameFullException();
        }
        if (this.players.stream().anyMatch(p -> player.equals(p.getPlayer()))) {
            throw new PlayerAlreadyKnownException();
        }
        this.players.add(UNOPlayer.newPlayer(player));
        player.updateCommands();
    }

    public void leave(@NotNull UNOPlayer unoPlayer) throws UnknownPlayerException {
        if (!this.players.remove(unoPlayer)) {
            throw new UnknownPlayerException();
        }
        Player player = unoPlayer.getPlayer();
        if (player == null) {
            throw new UnknownPlayerException();
        }
        Game game = games.remove(player);
        player.updateCommands();
        if (game == null) {
            return;
        }
        try {
            Player owner = this.players.getRealPlayers().getFirst().getPlayer();
            player.sendMessage(
                    Component.text("You have left the game")
                            .color(TextColor.color(0xFF5555))
            );
            games.put(owner, this);
            owner.updateCommands();
        } catch (NoSuchElementException e) {
            player.sendMessage(
                    Component.text("You have deleted the game")
                            .color(TextColor.color(0xFF5555))
            );
        }
    }

    public void addBot() throws GameAlreadyRunningException, GameFullException {
        if (isRunning()) {
            throw new GameAlreadyRunningException();
        }
        if (this.players.size() == 10) {
            throw new GameFullException();
        }
        this.players.add(UNOPlayer.newBot());
    }

    public void removeBot() throws GameAlreadyRunningException {
        if (isRunning()) {
            throw new GameAlreadyRunningException();
        }
        this.players.stream().filter(UNOPlayer::isBot).findFirst().ifPresent(this::leave);
    }

    public void start() throws GameAlreadyRunningException, NotEnoughPlayersException {
        if (isRunning()) {
            throw new GameAlreadyRunningException();
        }
        if (this.players.size() < 2) {
            throw new NotEnoughPlayersException();
        }
        this.deck.addAll(this.rules.initialDeck);
        Collections.shuffle(this.deck);
        do {
            this.pile.push(this.deck.pop());
        } while (this.pile.peek().getType().isSpecial());
        this.players.forEach(this::setupInventory);
        for (int i = 0; i < this.rules.initialCards; i++) {
            this.players.forEach(this::drawCard);
        }
        this.players.forEach(UNOPlayer::openGameInv);
        this.players.direction = this.rules.initialDirection;
        nextTurn();
    }

    public void setupInventory(@NotNull UNOPlayer unoPlayer) {
        Inventory inv = Bukkit.createInventory(
                null,
                9*5,
                Component.text("UNO (%d Players)".formatted(this.players.size()))
        );
        unoPlayer.setupInventory(inv);
        int index = this.players.indexOf(unoPlayer);
        for (int i = (index + 1) % this.players.size(); i != index; i = ++i % this.players.size()) {
            inv.addItem(ItemBuilder.simple(
                    Material.PAPER,
                    rules.initialCards,
                    Component.text(players.get(i).getName())
            ));
        }
        inv.setItem(20, DRAW_ITEM);
        unoPlayer.updateTopCard(this.pile.peek());
        inv.setItem(24, UNO_ITEM);
    }

    public void playCard(@NotNull UNOPlayer unoPlayer, @NotNull Card card) {
        if (!isRunning()) {
            throw new GameNotRunningException();
        }
        if (!canPlayCard(unoPlayer, card)) {
            return;
        }
        enforcePenaltyDraws(unoPlayer);
        unoPlayer.playCard(card);
        this.pile.push(card);
        updateTopCard();
        if (unoPlayer.isDone()) {
            this.winners.add(unoPlayer);
        }
        updateCardDisplay(unoPlayer);
        if (unoPlayer != this.players.getCurrentPlayer()) {
            return;
        }
        card.play(this, unoPlayer);
    }

    @Contract(pure = true)
    public boolean canPlayCard(@NotNull UNOPlayer player, @NotNull Card card) {
        Card top = this.pile.peek();

        // if a player is trying to end the game, check if the card is special and if the rules allow it
        if (player.getCards().size() == 1 && !this.rules.endOnAction && card.getType().isSpecial()) {
            return false;
        }

        // if a player is trying to jump in, check if the card matches and if the rules allow it
        if (player != this.players.getCurrentPlayer()) {
            if (!this.rules.jumpIn) {
                return false;
            }
            return card.equals(top);
        }

        // if a player is trying to play with pending draws, check if the card is stackable and if the rules allow it
        if (this.pendingDraws > 0) {
            return switch (card.getType()) {
                case DRAW2 -> this.rules.drawTwoStacking && top.getType() == Card.Type.DRAW2;
                case DRAW4 -> this.rules.drawFourStacking && top.getType() == Card.Type.DRAW4;
                default -> false;
            };
        }

        // wild cards can be played on any card
        if (card.getType().isWild()) {
            return true;
        }

        // the fallback rule: allow if either the color or the type match
        return card.getColor() == top.getColor() || card.getType() == top.getType();
    }

    public void drawCard(@NotNull UNOPlayer unoPlayer) {
        if (!canDrawCard(unoPlayer)) {
            return;
        }
        enforcePenaltyDraws(unoPlayer);
        this.deck.peek().resetColor();
        unoPlayer.drawCard(this.deck.pop());
        if (this.deck.isEmpty()) {
            recyclePile();
        }
        updateCardDisplay(unoPlayer);
        if (unoPlayer.hasPenaltyDraws()) {
            unoPlayer.addPenaltyDraws(-1);
        }
        if (unoPlayer != this.players.getCurrentPlayer()) {
            return;
        }
        if (this.pendingDraws > 0 && --this.pendingDraws > 0) {
            return;
        }
        nextTurn();
    }

    @Contract(pure = true)
    public boolean canDrawCard(@NotNull UNOPlayer unoPlayer) {
        // enforce cap
        if (unoPlayer.getCards().size() == this.rules.maxCards) {
            return false;
        }

        // initial draws
        if (!isRunning()) {
            return true;
        }

        // one who is not the current player may only draw if enforced by a penalty for not saying UNO
        if (unoPlayer != this.players.getCurrentPlayer()) {
            return unoPlayer.hasPenaltyDraws();
        }

        // the fallback rule: only allow drawing if one can't put down a card
        return !canPlayAny(unoPlayer);
    }

    @Contract(pure = true)
    public boolean canPlayAny(@NotNull UNOPlayer unoPlayer) {
        return unoPlayer.getCards().stream().anyMatch(card -> canPlayCard(unoPlayer, card));
    }

    public void catchNotSayingUNO(@NotNull UNOPlayer unoPlayer) {
        unoPlayer.addPenaltyDraws(this.rules.penaltyDraws);
        if (unoPlayer.isBot()) {
            return;
        }
        unoPlayer.getPlayer().sendMessage(
                Component.text("You got caught not saying UNO! (+%d)".formatted(this.rules.penaltyDraws))
                        .color(TextColor.color(0xFF5555))
        );
    }

    public void enforcePenaltyDraws(@NotNull UNOPlayer unoPlayer) {
        for (UNOPlayer other : this.players) {
            if (other == unoPlayer) {
                continue;
            }
            while (other.hasPenaltyDraws()) {
                drawCard(other);
            }
        }
    }

    public void nextTurn() {
        endTurn();
        if (this.winners.size() >= this.players.size() - 1) {
            this.players.stream().filter(Predicate.not(this.winners::contains)).findAny().ifPresent(this.winners::add);
            reset();
            return;
        }
        this.players.next().startTurn(this.rules.timeToPlay, this::playAI);
        setTurn();
    }

    public void endTurn() {
        UNOPlayer currentPlayer = this.players.getCurrentPlayer();
        if (currentPlayer == null) {
            return;
        }
        currentPlayer.endTurn();
        if (currentPlayer.isUNO()) {
            broadcast(
                    Component.text("UNO!")
                            .color(TextColor.color(0xFFFF55))
                            .decorate(TextDecoration.ITALIC)
                            .appendSpace()
                            .append(
                                    Component.text(currentPlayer.getName())
                                            .appendSpace()
                                            .append(
                                                    Component.text("has only one card left")
                                            )
                                            .color(TextColor.color(0xFFAA00))
                                            .decoration(TextDecoration.ITALIC, false)
                            ),
                    Predicate.not(currentPlayer::equals)
            );
        }
    }

    public void skipTurn() {
        endTurn();
        this.players.next().skipTurn();
        this.players.next().startTurn(this.rules.timeToPlay, this::playAI);
        setTurn();
    }

    public void setTurn() {
        for (UNOPlayer other : this.players) {
            int index = this.players.excludedOffset(this.players.getCurrentPlayer(), other);
            other.setTurn(index);
        }
    }

    public void recyclePile() {
        Card top = this.pile.pop();
        this.deck.addAll(this.pile);
        Collections.shuffle(this.deck);
        this.pile.clear();
        this.pile.push(top);
    }

    public void updateTopCard() {
        Card top = this.pile.peek();
        for (UNOPlayer other : this.players) {
            other.updateTopCard(top);
        }
    }

    public void updateCardDisplay(@NotNull UNOPlayer unoPlayer) {
        ItemStack item = switch (unoPlayer.getCards().size()) {
            case 0 -> switch (this.winners.indexOf(unoPlayer)) {
                case 0 -> ItemBuilder.simple(
                        Material.GOLD_INGOT,
                        1,
                        Component.text(unoPlayer.getName())
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("won the game")
                                .color(TextColor.color(0xDEB12D))
                                .decoration(TextDecoration.ITALIC, false)
                );
                case 1 -> ItemBuilder.simple(
                        Material.IRON_INGOT,
                        1,
                        Component.text(unoPlayer.getName())
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("got 2nd place")
                                .color(TextColor.color(0xCECACA))
                                .decoration(TextDecoration.ITALIC, false)
                );
                case 2 -> ItemBuilder.simple(
                        Material.COPPER_INGOT,
                        1,
                        Component.text(unoPlayer.getName())
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("got 3rd place")
                                .color(TextColor.color(0xB4684D))
                                .decoration(TextDecoration.ITALIC, false)
                );
                default -> ItemBuilder.simple(
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        1,
                        Component.text(unoPlayer.getName())
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("finished the game")
                                .color(TextColor.color(0x55FF55))
                                .decoration(TextDecoration.ITALIC, false)
                );
            };
            case 1 -> {
                if (unoPlayer.isUNO()) {
                    yield ItemBuilder.simple(
                            Material.BELL,
                            1,
                            Component.text(unoPlayer.getName())
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("UNO!")
                                    .color(TextColor.color(0xFFAA00))
                    );
                }
                yield ItemBuilder.simple(
                        Material.PAPER,
                        1,
                        Component.text(unoPlayer.getName())
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Click to call out")
                                .color(TextColor.color(0xAAAAAA))
                                .decoration(TextDecoration.ITALIC, false)
                );
            }
            default -> ItemBuilder.simple(
                    Material.PAPER,
                    unoPlayer.getCards().size(),
                    Component.text(unoPlayer.getName())
                            .decoration(TextDecoration.ITALIC, false)
            );
        };
        for (UNOPlayer other : this.players) {
            if (other == unoPlayer) {
                continue;
            }
            int index = this.players.excludedOffset(unoPlayer, other);
            other.getGameInv().setItem(index, item);
        }
    }

    public void addPendingDraws(int draws) {
        this.pendingDraws += draws;
    }

    public int getPendingDraws() {
        return this.pendingDraws;
    }

    public boolean isRunning() {
        return this.players.getCurrentPlayer() != null;
    }

    public @NotNull PlayerList getPlayers() {
        return this.players;
    }

    public @NotNull RuleSet getRules() {
        return this.rules;
    }

    public void broadcast(@NotNull Component component) {
        this.players.getRealPlayers().forEach(p -> p.getPlayer().sendMessage(component));
    }

    public void broadcast(@NotNull Component component, @NotNull Predicate<@NotNull UNOPlayer> filter) {
        this.players.getRealPlayers().stream().filter(filter).forEach(p -> p.getPlayer().sendMessage(component));
    }

    public void playAI(@NotNull UNOPlayer unoPlayer) {
        for (UNOPlayer other : this.players) {
            if (other == unoPlayer) {
                continue;
            }
            if (other.getCards().size() == 1 && !other.isUNO()) {
                catchNotSayingUNO(other);
                break;
            }
        }
        if (unoPlayer.getCards().size() == 2) {
            unoPlayer.callUNO();
        }
        if (unoPlayer.isPickingColor()) {
            this.pile.peek().assignColor(Card.Color.values()[ThreadLocalRandom.current().nextInt(4)]);
            unoPlayer.stopPickingColor();
            unoPlayer.transformInv(19, null, DRAW_ITEM, null, this.pile.peek().toItem(), null, UNO_ITEM, null);
            nextTurn();
            return;
        }
        Card card = calcCard(unoPlayer);
        if (card != null) {
            if (card.getType().isWild()) {
                card.assignColor(Card.Color.values()[ThreadLocalRandom.current().nextInt(4)]);
            }
            playCard(unoPlayer, card);
            return;
        }
        if (this.pendingDraws > 0) {
            do {
                drawCard(unoPlayer);
            } while (this.pendingDraws > 0);
            switch (this.pile.peek().getType()) {
                case DRAW2 -> {
                    if (!this.rules.playAfterDrawTwo) {
                        return;
                    }
                }
                case DRAW4 -> {
                    if (!this.rules.playAfterDrawFour) {
                        return;
                    }
                }
            }
            card = calcCard(unoPlayer);
            if (card != null) {
                if (card.getType().isWild()) {
                    card.assignColor(Card.Color.values()[ThreadLocalRandom.current().nextInt(4)]);
                }
                playCard(unoPlayer, card);
            }
            return;
        }
        drawCard(unoPlayer);
    }

    public @Nullable Card calcCard(@NotNull UNOPlayer unoPlayer) {
        AI ai = new AI(unoPlayer);
        ai.findMatchingTypeCard();
        ai.findMatchingColorCard();
        ai.findWildCard();
        ai.findDrawFourCard();
        return ai.get();
    }

    public void reset() {
        TextComponent.Builder builder = Component.text();
        builder.append(
                Component.text("UNO")
                        .color(TextColor.color(0xFFAA00))
                        .appendSpace()
                        .append(
                                Component.text("(%d Players)".formatted(this.players.size()))
                                        .color(TextColor.color(0x555555))
                        )
        );
        for (int i = 0; i < 3 && i < this.winners.size(); i++) {
            UNOPlayer unoPlayer = this.winners.get(i);
            builder.appendNewline();
            builder.append(
                    Component.text("->")
                            .color(TextColor.color(0x555555))
                            .appendSpace()
                            .append(
                                    Component.text("%d. %s".formatted(i + 1, unoPlayer.getName()))
                                            .color(TextColor.color(switch (i) {
                                                case 0 -> 0xDEB12D;
                                                case 1 -> 0xCECACA;
                                                default -> 0xB4684D;
                                            }))
                            )
            );
        }
        broadcast(builder.build());
        Bukkit.getScheduler().cancelTasks(Plugin.getInstance());
        this.players.endTurnCycle();
        this.players.forEach(UNOPlayer::reset);
        this.winners.clear();
        this.deck.clear();
        this.pile.clear();
    }

    public void handleGameInteract(@NotNull UNOPlayer unoPlayer, int slot) {
        if (unoPlayer.isPickingColor()) {
            Card top = this.pile.peek();
            switch (slot) {
                case 19 -> top.assignColor(Card.Color.RED);
                case 21 -> top.assignColor(Card.Color.YELLOW);
                case 23 -> top.assignColor(Card.Color.GREEN);
                case 25 -> top.assignColor(Card.Color.BLUE);
                default -> { return; }
            }
            unoPlayer.stopPickingColor();
            unoPlayer.transformInv(19, null, DRAW_ITEM, null, this.pile.peek().toItem(), null, UNO_ITEM, null);
            nextTurn();
            return;
        }
        if (slot < 9) {
            if (slot >= this.players.size()) {
                return;
            }
            UNOPlayer target = this.players.get(slot);
            if (target.getCards().size() == 1 && !target.isUNO()) {
                catchNotSayingUNO(target);
            }
        }
        switch (slot) {
            case 20 -> {
                drawCard(unoPlayer);
            }
            case 24 -> {
                if (unoPlayer.callUNO()) {
                    unoPlayer.getPlayer().sendMessage(
                            Component.text("UNO!")
                                    .color(TextColor.color(0xFFFF55))
                                    .decorate(TextDecoration.ITALIC)
                                    .appendSpace()
                                    .append(
                                            Component.text(unoPlayer.getName())
                                                    .appendSpace()
                                                    .append(
                                                            Component.text("has only one card left")
                                                    )
                                                    .color(TextColor.color(0xFFAA00))
                                                    .decoration(TextDecoration.ITALIC, false)
                                    )
                    );
                } else {
                    unoPlayer.getPlayer().sendMessage(
                            Component.text("You already called UNO!")
                                    .color(TextColor.color(0xFF5555))
                                    .decoration(TextDecoration.ITALIC, false)
                    );
                }
            }
        }
    }

    public void handlePlayCard(@NotNull UNOPlayer unoPlayer, @NotNull ItemStack item) {
        Card card = Card.ofItem(item);
        if (card == null) {
            return;
        }
        playCard(unoPlayer, card);
    }

    public void handleClose(@NotNull UNOPlayer unoPlayer) {
        if (!isRunning()) {
            return;
        }
        unoPlayer.getPlayer().sendMessage(
                Component.text("You left the game!")
                        .color(TextColor.color(0xFFAA00))
                        .appendSpace()
                        .append(
                                Component.text("Rejoin?")
                                        .color(TextColor.color(0xFFFF55))
                                        .hoverEvent(HoverEvent.showText(
                                                Component.text("Click here to rejoin the game")
                                        ))
                                        .clickEvent(ClickEvent.runCommand("uno open"))
                        )
        );
    }

    public void handleOpen(@NotNull UNOPlayer unoPlayer) {
        if (!isRunning()) {
            throw new GameNotRunningException();
        }
        unoPlayer.getPlayer().openInventory(unoPlayer.getGameInv());
    }

    public void handleLeave(@NotNull UNOPlayer unoPlayer) {
        leave(unoPlayer);
    }

    public static void fireInventoryClick(@NotNull InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getClickedInventory();
        for (Game game : games.values()) {
            for (UNOPlayer unoPlayer : game.players) {
                if (player.equals(unoPlayer.getPlayer())) {
                    if (unoPlayer.getGameInv().equals(inv)) {
                        event.setCancelled(true);
                        game.handleGameInteract(unoPlayer, event.getSlot());
                        return;
                    }
                    if (event.isShiftClick()) {
                        ItemStack item = event.getCurrentItem();
                        if (item == null) {
                            return;
                        }
                        event.setCancelled(true);
                        game.handlePlayCard(unoPlayer, item);
                        return;
                    }
                    return;
                }
            }
        }
    }

    public static void fireClose(@NotNull Player player) {
        for (Game game : games.values()) {
            for (UNOPlayer unoPlayer : game.players) {
                if (player.equals(unoPlayer.getPlayer())) {
                    game.handleClose(unoPlayer);
                    return;
                }
            }
        }
    }

    public static void fireOpen(@NotNull Player player) {
        for (Game game : games.values()) {
            for (UNOPlayer unoPlayer : game.players) {
                if (player.equals(unoPlayer.getPlayer())) {
                    game.handleOpen(unoPlayer);
                    return;
                }
            }
        }
    }

    public static void fireLeave(@NotNull Player player) {
        for (Game game : games.values()) {
            for (UNOPlayer unoPlayer : game.players) {
                if (player.equals(unoPlayer.getPlayer())) {
                    game.handleLeave(unoPlayer);
                    return;
                }
            }
        }
    }

    private class AI implements Supplier<@Nullable Card> {

        private Collection<Card> cards;

        public AI(@NotNull UNOPlayer unoPlayer) {
            this.cards = unoPlayer.getCards().stream().filter(card -> Game.this.canPlayCard(unoPlayer, card)).collect(Collectors.toCollection(ArrayList::new));
        }

        public void findMatchingTypeCard() {
            findCard(card -> card.getType() == Game.this.pile.peek().getType());
        }

        public void findMatchingColorCard() {
            findCard(card -> card.getColor() == Game.this.pile.peek().getColor());
        }

        public void findWildCard() {
            findCard(card -> card.getType() == Card.Type.WILD);
        }

        public void findDrawFourCard() {
            findCard(card -> card.getType() == Card.Type.DRAW4);
        }

        public void findCard(@NotNull Predicate<@NotNull Card> filter) {
            if (this.cards.size() <= 1) {
                return;
            }
            Collection<Card> cards = this.cards.stream().filter(filter).collect(Collectors.toCollection(ArrayList::new));
            if (cards.isEmpty()) {
                return;
            }
            this.cards = cards;
        }

        @Override
        public @Nullable Card get() {
            return this.cards.stream().findAny().orElse(null);
        }
    }
}
