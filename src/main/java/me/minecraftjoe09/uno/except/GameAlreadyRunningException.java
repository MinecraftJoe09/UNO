package me.minecraftjoe09.uno.except;

public class GameAlreadyRunningException extends IllegalStateException {

    public GameAlreadyRunningException() {
        super("The game is already running");
    }
}
