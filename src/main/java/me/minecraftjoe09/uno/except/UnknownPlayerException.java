package me.minecraftjoe09.uno.except;

public class UnknownPlayerException extends IllegalArgumentException {

    public UnknownPlayerException() {
        super("That player isn't currently playing");
    }
}
