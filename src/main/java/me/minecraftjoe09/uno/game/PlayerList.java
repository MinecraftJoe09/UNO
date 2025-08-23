package me.minecraftjoe09.uno.game;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PlayerList extends ArrayList<@NotNull UNOPlayer> {

    public int index = -1;
    public Direction direction = Direction.CW;

    public UNOPlayer getCurrentPlayer() {
        return index != -1 ? super.get(this.index) : null;
    }

    public UNOPlayer next() {
        do {
            this.index += this.direction == Direction.CW ? 1 : -1;
            this.index += super.size();
            this.index %= super.size();
        } while (super.get(this.index).isDone()); // skip player if done
        return getCurrentPlayer();
    }

    public void endTurnCycle() {
        this.index = -1;
    }

    public void reverseDirection() {
        this.direction = this.direction == Direction.CCW ? Direction.CW : Direction.CCW;
    }

    public List<UNOPlayer> getActivePlayers() {
        return super.stream().filter(Predicate.not(UNOPlayer::isDone)).collect(Collectors.toList());
    }

    public List<UNOPlayer> getRealPlayers() {
        return super.stream().filter(Predicate.not(UNOPlayer::isBot)).collect(Collectors.toList());
    }

    public @Range(from = -1, to = 8) int excludedOffset(@NotNull UNOPlayer unoPlayer, @NotNull UNOPlayer origin) {
        int index = super.indexOf(unoPlayer) - super.indexOf(origin);
        index += super.size();
        index %= super.size();
        return --index; // subtract one because the origin is excluded from its own player list
    }
}
