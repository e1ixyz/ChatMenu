package com.example.chatmenu;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DynamicMenuCommand extends Command {

    private final ChatMenu plugin;
    private final CommandConfig cfg;

    public DynamicMenuCommand(ChatMenu plugin, CommandConfig cfg) {
        super(cfg.name);
        this.plugin = plugin;
        this.cfg = cfg;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        return plugin.handleMenuCommand(sender, getName(), args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (cfg.type == CommandType.TARGET) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                List<String> result = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> {
                    String n = p.getName();
                    if (prefix.isEmpty() || n.toLowerCase().startsWith(prefix)) result.add(n);
                });
                Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
                return result;
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
