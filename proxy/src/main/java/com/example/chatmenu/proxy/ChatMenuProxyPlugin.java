package com.example.chatmenu.proxy;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.api.event.PluginMessageEvent;

import java.util.UUID;
import java.util.logging.Level;

public final class ChatMenuProxyPlugin extends Plugin implements Listener {

    private static final String CHANNEL = "chatmenu:proxy";

    @Override
    public void onEnable() {
        ProxyServer proxy = getProxy();
        proxy.registerChannel(CHANNEL);
        PluginManager manager = proxy.getPluginManager();
        manager.registerListener(this, this);
        getLogger().info("ChatMenu proxy bridge enabled.");
    }

    @Override
    public void onDisable() {
        PluginManager manager = getProxy().getPluginManager();
        manager.unregisterListener(this);
        getProxy().unregisterChannel(CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equalsIgnoreCase(event.getTag())) {
            return;
        }

        if (!(event.getSender() instanceof Server)) {
            getLogger().warning("Blocked proxy command payload from non-server sender: " + event.getSender().getClass().getName());
            return;
        }

        if (!(event.getReceiver() instanceof ProxiedPlayer receiver)) {
            getLogger().warning("Blocked proxy command payload with non-player receiver.");
            return;
        }

        // Prevent forwarding to other servers.
        event.setCancelled(true);

        byte[] data = event.getData();
        if (data == null || data.length == 0) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        ProxyExecutor executor = ProxyExecutor.fromString(readUtfSafe(in));
        UUID viewerId = parseUuid(readUtfSafe(in));
        String viewerName = readUtfSafe(in);
        String command = trimLeadingSlash(readUtfSafe(in));

        if (viewerId != null && !viewerId.equals(receiver.getUniqueId())) {
            getLogger().warning("Ignoring proxy command payload due to UUID mismatch for player " + receiver.getName());
            return;
        }
        if (viewerName != null && !viewerName.isEmpty() && !viewerName.equalsIgnoreCase(receiver.getName())) {
            getLogger().warning("Ignoring proxy command payload due to name mismatch for player " + receiver.getName());
            return;
        }

        if (command.isEmpty()) {
            return;
        }

        try {
            dispatch(executor, receiver, command);
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Unable to run proxy command '" + command + "': " + ex.getMessage(), ex);
        }
    }

    private void dispatch(ProxyExecutor executor, ProxiedPlayer viewer, String command) {
        ProxyServer proxy = getProxy();
        switch (executor) {
            case PLAYER -> {
                if (viewer == null || !viewer.isConnected()) {
                    getLogger().warning("Proxy command skipped because player connection is unavailable.");
                    return;
                }
                proxy.getPluginManager().dispatchCommand(viewer, command);
            }
            case CONSOLE -> proxy.getPluginManager().dispatchCommand(proxy.getConsole(), command);
        }
    }

    private static String readUtfSafe(ByteArrayDataInput in) {
        try {
            return in.readUTF();
        } catch (Exception ex) {
            return "";
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String trimLeadingSlash(String value) {
        if (value == null) return "";
        String result = value.trim();
        if (result.startsWith("/")) {
            result = result.substring(1).trim();
        }
        return result;
    }

    private enum ProxyExecutor {
        PLAYER,
        CONSOLE;

        static ProxyExecutor fromString(String raw) {
            if (raw == null) return PLAYER;
            String normalized = raw.trim().toUpperCase();
            if (normalized.equals("CONSOLE")) {
                return CONSOLE;
            }
            return PLAYER;
        }
    }
}
