package com.example.chatmenu;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class ProxyCommandBridge {

    static final String CHANNEL = "chatmenu:proxy";

    private final ChatMenu plugin;

    ProxyCommandBridge(ChatMenu plugin) {
        this.plugin = plugin;
    }

    void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    boolean dispatch(Player source, ChatMenu.ProxyCommandMode mode, String command) {
        if (source == null || !source.isOnline()) return false;
        if (command == null || command.isBlank()) return false;
        if (mode == null) mode = ChatMenu.ProxyCommandMode.PLAYER;

        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(mode.name());
            out.writeUTF(source.getUniqueId().toString());
            out.writeUTF(source.getName());
            out.writeUTF(command);
            source.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to forward proxy command '" + command + "': " + ex.getMessage());
            return false;
        }
    }
}
