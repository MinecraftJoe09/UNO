package me.minecraftjoe09.uno.game;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class RuleSet {

    public List<Card> initialDeck;
    public int initialCards;
    public int maxCards;
    public Direction initialDirection;
    public boolean drawTwoStacking;
    public boolean drawFourStacking;
    public boolean playAfterDrawTwo;
    public boolean playAfterDrawFour;
    public boolean jumpIn;
    public boolean endOnAction;
    public int timeToPlay;
    public int penaltyDraws;

    public RuleSet(@NotNull List<@NotNull Card> initialDeck, @Range(from = 2, to = 27) int initialCards, @Range(from = 2, to = 27) int maxCards, @NotNull Direction initialDirection, boolean drawTwoStacking, boolean drawFourStacking, boolean playAfterDrawTwo, boolean playAfterDrawFour, boolean jumpIn, boolean endOnAction, @Range(from = 10, to = 30) int timeToPlay, @Range(from = 0, to = 4) int penaltyDraws) {
        this.initialDeck = initialDeck;
        this.initialCards = initialCards;
        this.maxCards = maxCards;
        this.initialDirection = initialDirection;
        this.drawTwoStacking = drawTwoStacking;
        this.drawFourStacking = drawFourStacking;
        this.playAfterDrawTwo = playAfterDrawTwo;
        this.playAfterDrawFour = playAfterDrawFour;
        this.jumpIn = jumpIn;
        this.endOnAction = endOnAction;
        this.timeToPlay = timeToPlay;
        this.penaltyDraws = penaltyDraws;
    }

    public static RuleSet newClassicRuleSet() {
        List<Card> initialDeck = new ArrayList<>();
        for (Card.Color color : Card.Color.values()) {
            for (Card.Type type : EnumSet.range(Card.Type.ZERO, Card.Type.DRAW2)) {
                initialDeck.add(new Card(type, color));
            }
            for (Card.Type type : EnumSet.range(Card.Type.ONE, Card.Type.DRAW2)) {
                initialDeck.add(new Card(type, color));
            }
        }
        for (Card.Type type : EnumSet.range(Card.Type.DRAW4, Card.Type.WILD)) {
            for (int i = 0; i < 4; i++) {
                initialDeck.add(new Card(type, null));
            }
        }
        return new RuleSet(
                initialDeck,
                7,
                27,
                Direction.CW,
                false,
                false,
                false,
                false,
                false,
                false,
                10,
                2
        );
    }
}
