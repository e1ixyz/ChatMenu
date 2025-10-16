package com.example.chatmenu.proxy;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Plugin(
        id = "chatmenuproxy",
        name = "ChatMenuProxy",
        version = "1.0.0",
        authors = {"ChatMenu"}
)
public final class ChatMenuProxyPlugin {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("chatmenu:proxy");

    private final ProxyServer proxy;
    private final Logger logger;
    private final PluginContainer container;

    @Inject
    public ChatMenuProxyPlugin(ProxyServer proxy, Logger logger, PluginContainer container) {
        this.proxy = proxy;
        this.logger = logger;
        this.container = container;
        this.proxy.getEventManager().register(container, this);
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        logger.info("ChatMenu proxy bridge enabled (Velocity).");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        proxy.getChannelRegistrar().unregister(CHANNEL);
    }

    @Subscribe
    public void handlePluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }

        if (!(event.getSource() instanceof ServerConnection)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            logger.warning("Blocked proxy command payload from non-server sender: " + event.getSource().getClass().getName());
            return;
        }

        if (!(event.getTarget() instanceof Player player)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            logger.warning("Blocked proxy command payload with non-player target.");
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        byte[] data = event.getData();
        if (data == null || data.length == 0) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        ProxyExecutor executor = ProxyExecutor.fromString(readUtfSafe(in));
        UUID viewerId = parseUuid(readUtfSafe(in));
        String viewerName = readUtfSafe(in);
        String command = trimLeadingSlash(readUtfSafe(in));

        if (viewerId != null && !viewerId.equals(player.getUniqueId())) {
            logger.warning("Ignoring proxy command payload due to UUID mismatch for player " + player.getUsername());
            return;
        }
        if (viewerName != null && !viewerName.isEmpty() && !viewerName.equalsIgnoreCase(player.getUsername())) {
            logger.warning("Ignoring proxy command payload due to name mismatch for player " + player.getUsername());
            return;
        }
        if (command.isEmpty()) {
            return;
        }

        try {
            dispatch(executor, player, command);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Unable to run proxy command '" + command + "': " + ex.getMessage(), ex);
        }
    }

    private void dispatch(ProxyExecutor executor, Player player, String command) {
        switch (executor) {
            case PLAYER -> proxy.getCommandManager().executeAsync(player, command);
            case CONSOLE -> proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
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
