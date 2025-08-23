package me.minecraftjoe09.uno.except;

public class NotEnoughPlayersException extends IllegalStateException {

    public NotEnoughPlayersException() {
        super("Two or more players are required to start");
    }
}
