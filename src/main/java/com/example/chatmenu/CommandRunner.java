package com.example.chatmenu;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandRunner implements CommandExecutor {

    /**
     * Internal:
     *   NEW format: /cmrun <cmd1;cmd2;...> <viewerName> <targetName>
     *   Legacy     : /cmrun <cmd1;cmd2;...> <name>           (both map to <name>)
     *
     * Per-command executor prefixes:
     *   player:/coords      -> run as the clicking player (viewer)
     *   console:/lp user ... -> run as console (default if no prefix)
     *
     * Placeholder replacement in each command before dispatch:
     *   %player% -> viewerName
     *   %target% -> targetName
     *
     * Leading "/" is optional for commands.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) return false;

        final String viewerName;
        final String targetName;

        int tailCount;
        if (args.length >= 3) {
            // NEW format: last two are viewer + target
            viewerName = args[args.length - 2];
            targetName = args[args.length - 1];
            tailCount = 2;
        } else {
            // Legacy format: last one used for both
            viewerName = args[args.length - 1];
            targetName = args[args.length - 1];
            tailCount = 1;
        }

        // Rebuild command string from the remaining args
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length - tailCount; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i]);
        }

        String[] commands = sb.toString().split("\\s*;\\s*");
        for (int i = 0; i < commands.length; i++) {
            String seg = commands[i].trim();
            if (seg.isEmpty()) continue;

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
                Player p = Bukkit.getPlayerExact(viewerName);
                if (p == null || !p.isOnline()) {
                    sender.sendMessage("§cCannot run as player: " + viewerName + " is not online.");
                    continue;
                }
                Bukkit.getScheduler().runTaskLater(ChatMenu.getInstance(),
                        () -> p.performCommand(toExec), delay);
            } else {
                Bukkit.getScheduler().runTaskLater(ChatMenu.getInstance(),
                        () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toExec), delay);
            }
        }

        return true;
        }
}
