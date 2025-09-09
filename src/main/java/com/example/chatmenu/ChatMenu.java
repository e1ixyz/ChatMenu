package com.example.chatmenu;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.*;

public class ChatMenu extends JavaPlugin {

    private static ChatMenu instance;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // key = command name (lowercase)
    private final Map<String, CommandConfig> commands = new HashMap<>();
    private final Set<String> dynamicCommands = new HashSet<>();

    // Sentinels for escaped bracket handling (\[ and \])
    private static final String LBR_SENTINEL = "\uE000";
    private static final String RBR_SENTINEL = "\uE001";

    public static ChatMenu getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadCommands();

        if (getCommand("cmrun") != null) getCommand("cmrun").setExecutor(new CommandRunner());
        if (getCommand("chatmenu") != null) getCommand("chatmenu").setExecutor((sender, cmd, label, args) -> {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                unregisterDynamicCommands();
                reloadConfig();
                loadCommands();
                registerDynamicCommands();
                sender.sendMessage("§aChatMenu config reloaded.");
                return true;
            }
            sender.sendMessage("§eUsage: /chatmenu reload");
            return true;
        });

        registerDynamicCommands();
        getLogger().info("ChatMenu enabled.");
    }

    @Override
    public void onDisable() {
        unregisterDynamicCommands();
        getLogger().info("ChatMenu disabled.");
    }

    /* --------------------------- config load --------------------------- */

    private void loadCommands() {
        commands.clear();

        var root = getConfig().getConfigurationSection("commands");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            var sec = root.getConfigurationSection(key);
            if (sec == null) continue;

            String permission = Optional.ofNullable(sec.getString("permission", "")).orElse("").trim();
            String typeStr = sec.getString("type", "self");
            CommandType type = CommandType.fromString(typeStr);

            List<String> message = sec.getStringList("message");
            if (message == null || message.isEmpty()) {
                String single = sec.getString("message", "");
                if (single != null && !single.isEmpty()) message = List.of(single);
                else message = List.of();
            }

            CommandConfig cfg = new CommandConfig(key.toLowerCase(), permission, type, message);
            commands.put(cfg.name, cfg);
        }
    }

    /* -------------------- dynamic register/unregister ------------------- */

    private CommandMap getCommandMap() {
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (Exception e) {
            getLogger().warning("Unable to access CommandMap: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(CommandMap map) {
        try {
            Field known = map.getClass().getDeclaredField("knownCommands");
            known.setAccessible(true);
            return (Map<String, Command>) known.get(map);
        } catch (Exception e) {
            getLogger().warning("Unable to access knownCommands map: " + e.getMessage());
            return null;
        }
    }

    private void registerDynamicCommands() {
        CommandMap cmdMap = getCommandMap();
        if (cmdMap == null) return;

        Map<String, Command> known = getKnownCommands(cmdMap);

        for (CommandConfig cfg : commands.values()) {
            final String name = cfg.name;

            if ((known != null && (known.containsKey(name) ||
                known.containsKey(getDescription().getName().toLowerCase() + ":" + name)))
                || getCommand(name) != null) {
                continue;
            }

            DynamicMenuCommand dyn = new DynamicMenuCommand(this, cfg);
            if (!cfg.permission.isEmpty()) dyn.setPermission(cfg.permission);
            dyn.setDescription("ChatMenu dynamic command");
            dyn.setUsage(cfg.type == CommandType.TARGET ? "/" + name + " <player>" : "/" + name);

            try {
                cmdMap.register(getDescription().getName().toLowerCase(), dyn);
                dynamicCommands.add(name);
                getLogger().info("Registered dynamic command: /" + name);
            } catch (Exception e) {
                getLogger().warning("Failed to register dynamic command /" + name + ": " + e.getMessage());
            }
        }
    }

    private void unregisterDynamicCommands() {
        CommandMap cmdMap = getCommandMap();
        if (cmdMap == null) return;
        Map<String, Command> known = getKnownCommands(cmdMap);
        if (known == null) return;

        for (String name : new HashSet<>(dynamicCommands)) {
            known.remove(name);
            known.remove(getDescription().getName().toLowerCase() + ":" + name);
            getLogger().info("Unregistered dynamic command: /" + name);
        }
        dynamicCommands.clear();
    }

    /* ------------------------- invocation entry ------------------------- */

    boolean handleMenuCommand(CommandSender sender, String cmdName, String[] args) {
        CommandConfig cfg = commands.get(cmdName.toLowerCase());
        if (cfg == null) return false;

        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("Only players can use this.");
            return true;
        }

        if (!cfg.permission.isEmpty() && !viewer.hasPermission(cfg.permission)) {
            viewer.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (cfg.type == CommandType.SELF) {
            sendChatMenu(viewer, null, cfg);
            return true;
        }

        // TARGET menu
        if (args.length < 1) {
            viewer.sendMessage("§eUsage: /" + cfg.name + " <player>");
            return true;
        }

        String targetName = args[0];
        sendChatMenu(viewer, targetName, cfg);
        return true;
    }

    /* ------------------------- rendering helpers ------------------------ */

    // Replace escaped brackets with sentinels so the parser can ignore them as markers.
    private String preprocessBrackets(String s) {
        if (s == null || s.isEmpty()) return s;
        // Only backslash escapes (no [[ ]] sugar)
        s = s.replace("\\[", LBR_SENTINEL).replace("\\]", RBR_SENTINEL);
        return s;
    }

    // Restore sentinels back to literal brackets before rendering.
    private String restoreBrackets(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.replace(LBR_SENTINEL, "[").replace(RBR_SENTINEL, "]");
    }

    private boolean flagsDefaultAsPlayer(String rawFlags) {
        if (rawFlags == null) return false;
        String f = rawFlags.toLowerCase(Locale.ROOT).replace(" ", "");
        return f.contains("as=player") || f.contains("runas=player") || f.equals("player");
    }

    private void sendChatMenu(Player viewer, String targetName, CommandConfig cfg) {
        for (String rawLine : cfg.message) {
            String working = preprocessBrackets(rawLine);
            Component full = Component.empty();

            int idx = 0;
            while ((idx = working.indexOf("[")) != -1) {
                int end = working.indexOf("]", idx);
                if (end == -1) break;

                String beforeRaw = working.substring(0, idx);
                if (!beforeRaw.isEmpty()) {
                    full = full.append(parseText(restoreBrackets(beforeRaw), viewer, targetName));
                }

                String segment = working.substring(idx + 1, end);
                // Allow optional 4th part for flags: [display | commands | hover | flags]
                String[] parts = segment.split("\\|", 4);

                if (parts.length >= 3) {
                    String displayRaw = restoreBrackets(parts[0].trim());
                    String commandsRaw = restoreBrackets(parts[1].trim());
                    String hoverRaw   = restoreBrackets(parts[2].trim());
                    String flagsRaw   = parts.length >= 4 ? restoreBrackets(parts[3].trim()) : "";

                    boolean defaultAsPlayer = flagsDefaultAsPlayer(flagsRaw);

                    Component displayComp = parseText(displayRaw, viewer, targetName);
                    Component hoverComp   = parseText(hoverRaw, viewer, targetName);

                    String[] cmds = commandsRaw.split("\\s*;\\s*");
                    List<String> encoded = new ArrayList<>(cmds.length);
                    for (String c : cmds) {
                        String cmd = c.trim();
                        if (cmd.isEmpty()) continue;

                        // Apply default executor if caller didn't specify one per command
                        boolean hasPrefix = cmd.regionMatches(true, 0, "player:", 0, 7)
                                || cmd.regionMatches(true, 0, "console:", 0, 8);
                        if (defaultAsPlayer && !hasPrefix) {
                            cmd = "player:" + cmd;
                        }
                        encoded.add(cmd);
                    }

                    String lastName = (cfg.type == CommandType.TARGET)
                            ? (targetName == null ? viewer.getName() : targetName)
                            : viewer.getName();

                    String cmrun = "/cmrun " + String.join(";", encoded) + " " + lastName;

                    Component clickable = displayComp
                            .hoverEvent(HoverEvent.showText(hoverComp))
                            .clickEvent(ClickEvent.runCommand(cmrun));

                    full = full.append(clickable).append(Component.text(" "));
                } else {
                    // No pipes -> treat as plain text "[...]"
                    full = full.append(parseText(restoreBrackets("[" + segment + "]"), viewer, targetName));
                }

                working = working.substring(end + 1);
                idx = 0;
            }

            if (!working.isEmpty()) {
                full = full.append(parseText(restoreBrackets(working), viewer, targetName));
            }

            viewer.sendMessage(full);
        }
    }

    private Component parseText(String raw, Player viewer, String targetName) {
        if (raw == null || raw.isEmpty()) return Component.empty();

        String s = raw.replace("%player%", viewer.getName());
        if (targetName != null) s = s.replace("%target%", targetName);

        try {
            s = PlaceholderAPI.setPlaceholders(viewer, s);
        } catch (Throwable ignored) { }

        if (s.contains("&")) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
        } else {
            try {
                return mm.deserialize(s);
            } catch (Exception e) {
                return Component.text(s);
            }
        }
    }
}
