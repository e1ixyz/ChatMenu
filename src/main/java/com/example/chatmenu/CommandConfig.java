package com.example.chatmenu;

import java.util.List;

public class CommandConfig {
    public final String name;        // command name (lowercase)
    public final String permission;  // permission required to open the menu
    public final CommandType type;   // SELF or TARGET
    public final List<String> message;

    public CommandConfig(String name, String permission, CommandType type, List<String> message) {
        this.name = name;
        this.permission = permission == null ? "" : permission;
        this.type = type == null ? CommandType.SELF : type;
        this.message = message;
    }
}
