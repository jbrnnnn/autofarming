package com.example.autofarm;

public enum FarmMode {
    OFF, BREAK, RIGHT_CLICK;

    public FarmMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
