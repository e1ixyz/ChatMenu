package com.example.chatmenu;

import com.example.chatmenu.ChatMenu.PendingBatch;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
            if (seg == null || seg.isEmpty()) continue;

            boolean runAsPlayer = false;

            if (seg.regionMatches(true, 0, "player:", 0, 7)) {
                runAsPlayer = true;
                seg = seg.substring(7).trim();
            } else if (seg.regionMatches(true, 0, "console:", 0, 8)) {
                runAsPlayer = false;
                seg = seg.substring(8).trim();
            }

            String cmdFinal = seg
                    .replace("%player%", viewerName)
                    .replace("%target%", targetName);

            if (cmdFinal.startsWith("/")) cmdFinal = cmdFinal.substring(1);

            final String toExec = cmdFinal;
            final long delay = i * 2L;

            if (runAsPlayer) {
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

        return true;
    }
}
