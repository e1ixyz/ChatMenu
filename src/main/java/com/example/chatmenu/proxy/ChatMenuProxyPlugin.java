package com.example.chatmenu.proxy;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "chatmenuproxy",
        name = "ChatMenuProxy",
        version = "1.0.0",
        authors = {"ChatMenu"}
)
public final class ChatMenuProxyPlugin {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("chatmenu:proxy");
    private static final long DUPLICATE_SUPPRESSION_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private final ProxyServer proxy;
    private final Logger logger;
    private final Map<UUID, CommandStamp> recentCommands = new ConcurrentHashMap<>();

    @Inject
    public ChatMenuProxyPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getPluginManager().fromInstance(this).ifPresentOrElse(
                container -> proxy.getEventManager().register(container, this),
                () -> logger.warning("Unable to register ChatMenu proxy listener: container lookup failed.")
        );
        logger.info("ChatMenu proxy bridge enabled (Velocity).");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        proxy.getChannelRegistrar().unregister(CHANNEL);
        recentCommands.clear();
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

        if (!markExecution(player, command)) {
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

    private boolean markExecution(Player player, String command) {
        UUID id = player.getUniqueId();
        long now = System.nanoTime();
        CommandStamp previous = recentCommands.get(id);
        if (previous != null) {
            if (now - previous.timestamp() < DUPLICATE_SUPPRESSION_WINDOW_NANOS
                    && previous.command().equalsIgnoreCase(command)) {
                return false;
            }
        }
        recentCommands.put(id, new CommandStamp(command, now));
        return true;
    }

    private record CommandStamp(String command, long timestamp) {}
}
