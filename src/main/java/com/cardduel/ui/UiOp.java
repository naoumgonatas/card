package com.cardduel.ui;

/**
 * One "set this property" instruction for the table screen, e.g. {@code #M0 #Name.Text = "Cave Bear"}.
 * {@code value} is a String or a Boolean. The Hytale page turns each op into a {@code UICommandBuilder.set(...)}.
 */
public record UiOp(String selector, Object value) {

    public static UiOp text(String selector, String text) {
        return new UiOp(selector, text == null ? "" : text);
    }

    public static UiOp flag(String selector, boolean value) {
        return new UiOp(selector, value);
    }
}
