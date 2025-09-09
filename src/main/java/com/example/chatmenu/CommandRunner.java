package com.example.chatmenu;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandRunner implements CommandExecutor {

    /**
     * Usage (internal): /cmrun <cmd1;cmd2;...> <targetOrSelfName>
     * - Per-command executor:
     *     "player:/coords" runs as that player
     *     "console:lp user %player% ..." runs as console (default)
     * - Placeholders %player% and %target% are replaced with the provided last argument.
     * - Leading "/" is optional and will be stripped when dispatching.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) return false;

        String target = args[args.length - 1];

        // Rebuild the joined command string from all but the last arg
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length - 1; i++) {
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

            // Replace placeholders with the provided name (for both SELF and TARGET flows)
            String cmdFinal = seg.replace("%player%", target).replace("%target%", target);

            // Trim optional leading slash
            if (cmdFinal.startsWith("/")) cmdFinal = cmdFinal.substring(1);

            final String toExec = cmdFinal;
            final long delay = i * 2L; // small spacing between commands

            if (runAsPlayer) {
                Player p = Bukkit.getPlayerExact(target);
                if (p == null || !p.isOnline()) {
                    sender.sendMessage("§cCannot run as player: " + target + " is not online.");
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
