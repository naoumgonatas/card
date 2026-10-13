package com.cardduel.engine;

/** Outcome of a player action. {@code message} is shown to the player when {@code ok} is false. */
public record Result(boolean ok, String message) {
    public static final Result OK = new Result(true, "");

    public static Result fail(String message) {
        return new Result(false, message);
    }
}
