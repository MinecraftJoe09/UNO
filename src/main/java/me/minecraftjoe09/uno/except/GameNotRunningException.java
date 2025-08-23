package me.minecraftjoe09.uno.except;

public class GameNotRunningException extends IllegalStateException {

    public GameNotRunningException() {
        super("This game is not running");
    }
}
