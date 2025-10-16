package com.example.chatmenu;

import com.example.chatmenu.ChatMenu.PendingBatch;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class CommandRunner implements CommandExecutor {

    /**
     * Internal:
     *   Tokenized format: /cmrun <uuid-token>
     *   Tokens are issued per menu interaction, bound to the viewer name, and expire quickly.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("§cOnly players may invoke ChatMenu actions.");
            return true;
        }

        if (args.length != 1) {
            viewer.sendMessage("§cInvalid ChatMenu invocation.");
            return true;
        }

        UUID token;
        try {
            token = UUID.fromString(args[0]);
        } catch (IllegalArgumentException ex) {
            viewer.sendMessage("§cThat menu action is no longer valid.");
            return true;
        }

        ChatMenu plugin = ChatMenu.getInstance();
        if (plugin == null) {
            viewer.sendMessage("§cChatMenu is not ready.");
            return true;
        }

        PendingBatch batch = plugin.claimPendingBatch(token, viewer.getName());
        if (batch == null) {
            viewer.sendMessage("§cThat menu action is no longer valid.");
            return true;
        }

        final String viewerName = batch.viewerName;
        final String targetName = batch.targetName != null ? batch.targetName : batch.viewerName;

        for (int i = 0; i < batch.commands.size(); i++) {
            String seg = batch.commands.get(i);
            ExecutionPlan plan = ExecutionPlan.parse(seg);
            if (plan == null) continue;

            String cmdFinal = plan.command()
                    .replace("%player%", viewerName)
                    .replace("%target%", targetName);

            if (cmdFinal.startsWith("/")) cmdFinal = cmdFinal.substring(1);

            final String toExec = cmdFinal;
            final long delay = i * 2L;

            if (plan.proxy()) {
                Player onlineViewer = Bukkit.getPlayerExact(viewerName);
                if (onlineViewer == null || !onlineViewer.isOnline()) {
                    viewer.sendMessage("§cCannot run proxy command: you are no longer online.");
                    continue;
                }
                final ChatMenu.ProxyCommandMode mode = plan.proxyMode();
                final Player viewerSnapshot = viewer;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    boolean ok = plugin.dispatchProxyCommand(onlineViewer, mode, toExec);
                    if (!ok && viewerSnapshot.isOnline()) {
                        viewerSnapshot.sendMessage("§cThat proxy action could not be delivered.");
                    }
                }, delay);
                continue;
            }

            if (plan.runAsPlayer()) {
                Player onlineViewer = Bukkit.getPlayerExact(viewerName);
                if (onlineViewer == null || !onlineViewer.isOnline()) {
                    viewer.sendMessage("§cCannot run as player: you are no longer online.");
                    continue;
                }
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> onlineViewer.performCommand(toExec), delay);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toExec), delay);
            }
        }

        List<CommandConfig.Notification> notifications = batch.notifications;
        if (!notifications.isEmpty()) {
            long notifyDelay = batch.commands.isEmpty() ? 0L : (batch.commands.size() * 2L);
            final Player viewerSnapshot = viewer;
            final String targetSnapshot = targetName;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player targetPlayer = targetSnapshot == null ? null : Bukkit.getPlayerExact(targetSnapshot);

                for (CommandConfig.Notification note : notifications) {
                    Component msg = plugin.parseText(note.message, viewerSnapshot, targetSnapshot, note.context);
                    switch (note.audience) {
                        case VIEWER -> {
                            if (viewerSnapshot != null && viewerSnapshot.isOnline()) {
                                viewerSnapshot.sendMessage(msg);
                            }
                        }
                        case TARGET -> {
                            if (targetPlayer != null && targetPlayer.isOnline()) {
                                targetPlayer.sendMessage(msg);
                            } else if (viewerSnapshot != null && viewerSnapshot.isOnline()
                                    && (targetSnapshot == null
                                    || !viewerSnapshot.getName().equalsIgnoreCase(targetSnapshot))) {
                                viewerSnapshot.sendMessage(msg);
                            }
                        }
                    }
                }
            }, notifyDelay);
        }

        return true;
    }

    private record ExecutionPlan(String command, boolean proxy, boolean runAsPlayer,
                                 ChatMenu.ProxyCommandMode proxyMode) {

        static ExecutionPlan parse(String raw) {
            if (raw == null) return null;
            String work = raw.trim();
            if (work.isEmpty()) return null;

            if (startsWithIgnoreCase(work, "proxy-console:") || startsWithIgnoreCase(work, "proxy_console:")
                    || startsWithIgnoreCase(work, "proxyconsole:")) {
                String cmd = stripPrefix(work, "proxy-console:", "proxy_console:", "proxyconsole:");
                cmd = trimLeadingSlash(cmd);
                return cmd.isEmpty() ? null : new ExecutionPlan(cmd, true, false, ChatMenu.ProxyCommandMode.CONSOLE);
            }

            if (startsWithIgnoreCase(work, "proxy-player:") || startsWithIgnoreCase(work, "proxy_player:")
                    || startsWithIgnoreCase(work, "proxyplayer:")) {
                String cmd = stripPrefix(work, "proxy-player:", "proxy_player:", "proxyplayer:");
                cmd = trimLeadingSlash(cmd);
                return cmd.isEmpty() ? null : new ExecutionPlan(cmd, true, false, ChatMenu.ProxyCommandMode.PLAYER);
            }

            if (startsWithIgnoreCase(work, "proxy:")) {
                work = stripPrefix(work, "proxy:");
                if (startsWithIgnoreCase(work, "console:")) {
                    String cmd = trimLeadingSlash(stripPrefix(work, "console:"));
                    return cmd.isEmpty() ? null : new ExecutionPlan(cmd, true, false, ChatMenu.ProxyCommandMode.CONSOLE);
                }
                if (startsWithIgnoreCase(work, "player:")) {
                    String cmd = trimLeadingSlash(stripPrefix(work, "player:"));
                    return cmd.isEmpty() ? null : new ExecutionPlan(cmd, true, false, ChatMenu.ProxyCommandMode.PLAYER);
                }
                String cmd = trimLeadingSlash(work);
                return cmd.isEmpty() ? null : new ExecutionPlan(cmd, true, false, ChatMenu.ProxyCommandMode.PLAYER);
            }

            boolean runAsPlayer = false;
            if (startsWithIgnoreCase(work, "player:")) {
                runAsPlayer = true;
                work = stripPrefix(work, "player:");
            } else if (startsWithIgnoreCase(work, "console:")) {
                work = stripPrefix(work, "console:");
            }

            String cmd = trimLeadingSlash(work);
            if (cmd.isEmpty()) return null;
            return new ExecutionPlan(cmd, false, runAsPlayer, null);
        }

        private static boolean startsWithIgnoreCase(String value, String prefix) {
            if (value == null || prefix == null) return false;
            if (value.length() < prefix.length()) return false;
            return value.regionMatches(true, 0, prefix, 0, prefix.length());
        }

        private static String stripPrefix(String value, String... prefixes) {
            if (value == null) return "";
            for (String prefix : prefixes) {
                if (startsWithIgnoreCase(value, prefix)) {
                    return value.substring(prefix.length()).trim();
                }
            }
            return value.trim();
        }

        private static String trimLeadingSlash(String value) {
            if (value == null) return "";
            String result = value.trim();
            if (result.startsWith("/")) {
                result = result.substring(1).trim();
            }
            return result;
        }
    }
}
