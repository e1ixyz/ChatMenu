package com.example.chatmenu;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

    // PlaceholderAPI context selector
    private enum PapiContext { VIEWER, TARGET }

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

    private PapiContext flagsPapiContext(String rawFlags, PapiContext fallback) {
        if (rawFlags == null) return fallback;
        String f = rawFlags.toLowerCase(Locale.ROOT);
        if (f.contains("ctx=target")) return PapiContext.TARGET;
        if (f.contains("ctx=viewer")) return PapiContext.VIEWER;
        return fallback;
    }

    // Detect and strip an optional per-line context directive: {{ctx=viewer}} or {{ctx=target}}
    private static class LineCtx {
        final String text;
        final PapiContext ctx;
        LineCtx(String text, PapiContext ctx) { this.text = text; this.ctx = ctx; }
    }
    private LineCtx extractLineContext(String raw) {
        if (raw == null) return new LineCtx("", PapiContext.VIEWER);
        String s = raw;
        PapiContext ctx = PapiContext.VIEWER;
        if (s.startsWith("{{") && s.toLowerCase(Locale.ROOT).startsWith("{{ctx=")) {
            int end = s.indexOf("}}");
            if (end > 0) {
                String inside = s.substring(2, end).trim(); // ctx=target
                String[] kv = inside.split("=", 2);
                if (kv.length == 2) {
                    if (kv[1].equalsIgnoreCase("target")) ctx = PapiContext.TARGET;
                    if (kv[1].equalsIgnoreCase("viewer")) ctx = PapiContext.VIEWER;
                }
                s = s.substring(end + 2).trim();
            }
        }
        return new LineCtx(s, ctx);
    }

    private void sendChatMenu(Player viewer, String targetName, CommandConfig cfg) {
        for (String rawLine : cfg.message) {
            LineCtx lc = extractLineContext(rawLine);
            String working = preprocessBrackets(lc.text);
            PapiContext lineCtx = lc.ctx;

            Component full = Component.empty();

            int idx = 0;
            while ((idx = working.indexOf("[")) != -1) {
                int end = working.indexOf("]", idx);
                if (end == -1) break;

                String beforeRaw = working.substring(0, idx);
                if (!beforeRaw.isEmpty()) {
                    full = full.append(parseText(restoreBrackets(beforeRaw), viewer, targetName, lineCtx));
                }

                String segment = working.substring(idx + 1, end);
                // Optional 4th part for flags: [display | commands | hover | flags]
                String[] parts = segment.split("\\|", 4);

                if (parts.length >= 3) {
                    String displayRaw = restoreBrackets(parts[0].trim());
                    String commandsRaw = restoreBrackets(parts[1].trim());
                    String hoverRaw   = restoreBrackets(parts[2].trim());
                    String flagsRaw   = parts.length >= 4 ? restoreBrackets(parts[3].trim()) : "";

                    boolean defaultAsPlayer = flagsDefaultAsPlayer(flagsRaw);
                    PapiContext btnCtx = flagsPapiContext(flagsRaw, lineCtx);

                    Component displayComp = parseText(displayRaw, viewer, targetName, btnCtx);
                    Component hoverComp   = parseText(hoverRaw, viewer, targetName, btnCtx);

                    String[] cmds = commandsRaw.split("\\s*;\\s*");
                    List<String> encoded = new ArrayList<>(cmds.length);
                    for (String c : cmds) {
                        String cmd = c.trim();
                        if (cmd.isEmpty()) continue;

                        boolean hasPrefix = cmd.regionMatches(true, 0, "player:", 0, 7)
                                || cmd.regionMatches(true, 0, "console:", 0, 8);
                        if (defaultAsPlayer && !hasPrefix) {
                            cmd = "player:" + cmd;
                        }
                        encoded.add(cmd);
                    }

                    String viewerName = viewer.getName();
                    String targetResolved = (cfg.type == CommandType.TARGET)
                            ? (targetName == null ? viewerName : targetName)
                            : viewerName;

                    // Pass BOTH viewer and target to cmrun (new protocol)
                    String cmrun = "/cmrun " + String.join(";", encoded) + " " + viewerName + " " + targetResolved;

                    Component clickable = displayComp
                            .hoverEvent(HoverEvent.showText(hoverComp))
                            .clickEvent(ClickEvent.runCommand(cmrun));

                    full = full.append(clickable).append(Component.text(" "));
                } else {
                    // No pipes -> plain "[...]" text
                    full = full.append(parseText(restoreBrackets("[" + segment + "]"), viewer, targetName, lineCtx));
                }

                working = working.substring(end + 1);
                idx = 0;
            }

            if (!working.isEmpty()) {
                full = full.append(parseText(restoreBrackets(working), viewer, targetName, lineCtx));
            }

            viewer.sendMessage(full);
        }
    }

    /* ---------- PlaceholderAPI context handling with Offline fallback ---------- */

    private OfflinePlayer resolvePapiPlayer(String name) {
        if (name == null || name.isEmpty()) return null;

        // Try exact online first
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;

        // Try case-insensitive online
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }

        // Try cached offline (if available on current server API)
        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null) return cached;
        } catch (NoSuchMethodError ignored) { }

        // Fallback to full offline lookup (may create an OfflinePlayer shell)
        return Bukkit.getOfflinePlayer(name);
    }

    private Component parseText(String raw, Player viewer, String targetName, PapiContext ctx) {
        if (raw == null || raw.isEmpty()) return Component.empty();

        String s = raw.replace("%player%", viewer.getName());
        if (targetName != null) s = s.replace("%target%", targetName);

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                if (ctx == PapiContext.TARGET && targetName != null) {
                    OfflinePlayer ctxPlayer = resolvePapiPlayer(targetName);
                    if (ctxPlayer != null) {
                        s = PlaceholderAPI.setPlaceholders(ctxPlayer, s);
                    } else {
                        // Fallback to viewer to avoid nulls
                        s = PlaceholderAPI.setPlaceholders(viewer, s);
                    }
                } else {
                    s = PlaceholderAPI.setPlaceholders(viewer, s);
                }
            }
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
