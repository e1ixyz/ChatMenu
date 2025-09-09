package com.example.chatmenu;

public enum CommandType {
    SELF, TARGET;

    public static CommandType fromString(String s) {
        if (s == null) return SELF;
        return s.equalsIgnoreCase("target") ? TARGET : SELF;
    }
}
