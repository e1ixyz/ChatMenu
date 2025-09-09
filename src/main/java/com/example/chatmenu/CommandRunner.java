package com.example.chatmenu;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandRunner implements CommandExecutor {

    /**
     * Usage (internal): /cmrun <cmd1;cmd2;...> <targetOrSelfName>
     * We replace both %player% and %target% in the dispatched commands
     * with the provided last argument (so SELF menus keep working with %player%,
     * and TARGET menus should use %target%).
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) return false;

        // Last arg is the target (or self for SELF menus)
        String target = args[args.length - 1];

        // Rebuild the joined command string from all but the last arg
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length - 1; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i]);
        }

        // Now split by semicolons into separate console commands
        String[] commands = sb.toString().split("\\s*;\\s*");
        for (int i = 0; i < commands.length; i++) {
            final String toRun = commands[i]
                    .replace("%player%", target)
                    .replace("%target%", target);

            final long delay = i * 2L; // small spacing between commands
            Bukkit.getScheduler().runTaskLater(ChatMenu.getInstance(),
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toRun),
                    delay);
        }
        return true;
    }
}
