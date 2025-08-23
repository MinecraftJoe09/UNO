package me.minecraftjoe09.uno.except;

public class GameFullException extends IllegalStateException {

    public GameFullException() {
        super("This game is full");
    }
}
