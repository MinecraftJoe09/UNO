package me.minecraftjoe09.uno.except;

public class PlayerAlreadyKnownException extends IllegalArgumentException {

    public PlayerAlreadyKnownException() {
        super("That player is already in the game");
    }
}
