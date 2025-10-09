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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatMenu extends JavaPlugin {

    private static ChatMenu instance;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // key = command name (lowercase)
    private final Map<String, CommandConfig> commands = new HashMap<>();
    private final Set<String> dynamicCommands = new HashSet<>();

    // Sentinels for escaped bracket handling (\[ and \])
    private static final String LBR_SENTINEL = "\uE000";
    private static final String RBR_SENTINEL = "\uE001";

    // Pending command executions keyed by opaque tokens
    private final Map<UUID, PendingBatch> pendingBatches = new ConcurrentHashMap<>();
    private static final long PENDING_TTL_MS = 5 * 60_000L;

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
        pendingBatches.clear();
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

            List<CommandConfig.Line> lines = parseMessageDefinition(sec.get("message"), type);
            CommandConfig cfg = new CommandConfig(key.toLowerCase(), permission, type, lines);
            commands.put(cfg.name, cfg);
        }
    }

    private List<CommandConfig.Line> parseMessageDefinition(Object rawMessage, CommandType type) {
        List<CommandConfig.Line> lines = new ArrayList<>();
        if (rawMessage instanceof List<?> list) {
            for (Object entry : list) {
                CommandConfig.Line line = parseMessageEntry(entry, type);
                if (line != null) lines.add(line);
            }
        } else if (rawMessage instanceof ConfigurationSection section) {
            lines.add(parseStructuredLine(section.get("line"), CommandConfig.PapiContext.fromString(section.get("context"), CommandConfig.PapiContext.VIEWER), type));
        } else if (rawMessage instanceof String str) {
            lines.add(parseLegacyLine(str));
        } else if (rawMessage != null) {
            lines.add(parseLegacyLine(String.valueOf(rawMessage)));
        }
        return lines;
    }

    private CommandConfig.Line parseMessageEntry(Object entry, CommandType type) {
        if (entry == null) {
            return new CommandConfig.Line(List.of());
        }
        if (entry instanceof String str) {
            return parseLegacyLine(str);
        }
        if (entry instanceof ConfigurationSection section) {
            return parseMessageEntry(section.getValues(false), type);
        }
        if (entry instanceof Map<?, ?> rawMap) {
            return parseMapLine(rawMap, type);
        }
        if (entry instanceof List<?> list) {
            return parseStructuredLine(list, CommandConfig.PapiContext.VIEWER, type);
        }
        return parseLegacyLine(entry.toString());
    }

    private CommandConfig.Line parseMapLine(Map<?, ?> rawMap, CommandType type) {
        Map<String, Object> map = normalizeMap(rawMap);
        CommandConfig.PapiContext defaultCtx = CommandConfig.PapiContext.fromString(map.get("context"), CommandConfig.PapiContext.VIEWER);

        if (map.containsKey("line")) {
            return parseStructuredLine(map.get("line"), defaultCtx, type);
        }
        if (map.containsKey("segments")) {
            return parseStructuredLine(map.get("segments"), defaultCtx, type);
        }
        if (map.containsKey("raw")) {
            return parseLegacyLine(String.valueOf(map.get("raw")));
        }
        if (map.containsKey("text") && map.size() == 1) {
            return new CommandConfig.Line(List.of(new CommandConfig.TextSegment(String.valueOf(map.get("text")), defaultCtx)));
        }
        if (map.containsKey("button")) {
            CommandConfig.ButtonSegment button = parseButtonSegment(map, "button", defaultCtx, false);
            return new CommandConfig.Line(List.of(button));
        }
        if (looksLikeButton(map)) {
            CommandConfig.ButtonSegment button = parseButtonSegment(map, null, defaultCtx, false);
            return new CommandConfig.Line(List.of(button));
        }
        if (map.containsKey("text")) {
            return new CommandConfig.Line(List.of(new CommandConfig.TextSegment(String.valueOf(map.get("text")), defaultCtx)));
        }
        return new CommandConfig.Line(List.of(new CommandConfig.TextSegment(map.toString(), defaultCtx)));
    }

    private CommandConfig.Line parseStructuredLine(Object rawSegments, CommandConfig.PapiContext defaultCtx, CommandType type) {
        if (rawSegments == null) {
            return new CommandConfig.Line(List.of());
        }

        List<?> segmentList;
        if (rawSegments instanceof List<?> list) {
            segmentList = list;
        } else if (rawSegments instanceof ConfigurationSection section) {
            segmentList = List.of(section.getValues(false));
        } else if (rawSegments instanceof Map<?, ?> map) {
            segmentList = List.of(map);
        } else if (rawSegments instanceof String str) {
            segmentList = List.of(str);
        } else {
            segmentList = List.of(rawSegments.toString());
        }

        List<CommandConfig.Segment> segments = new ArrayList<>();
        for (Object segObj : segmentList) {
            CommandConfig.Segment segment = parseSegment(segObj, defaultCtx, type);
            if (segment != null) segments.add(segment);
        }
        return new CommandConfig.Line(segments);
    }

    private CommandConfig.Segment parseSegment(Object raw, CommandConfig.PapiContext defaultCtx, CommandType type) {
        if (raw == null) {
            return new CommandConfig.TextSegment("", defaultCtx);
        }
        if (raw instanceof String str) {
            return new CommandConfig.TextSegment(str, defaultCtx);
        }
        if (raw instanceof ConfigurationSection section) {
            return parseSegment(section.getValues(false), defaultCtx, type);
        }
        if (raw instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = normalizeMap(rawMap);
            CommandConfig.PapiContext ctx = CommandConfig.PapiContext.fromString(map.get("context"), defaultCtx);

            if (map.containsKey("text") && !looksLikeButton(map)) {
                return new CommandConfig.TextSegment(String.valueOf(map.get("text")), ctx);
            }
            if (map.containsKey("button")) {
                return parseButtonSegment(map, "button", ctx, false);
            }
            if (looksLikeButton(map)) {
                return parseButtonSegment(map, null, ctx, false);
            }
            if (map.containsKey("text")) {
                return new CommandConfig.TextSegment(String.valueOf(map.get("text")), ctx);
            }
            if (map.containsKey("raw")) {
                return new CommandConfig.TextSegment(String.valueOf(map.get("raw")), ctx);
            }
            return new CommandConfig.TextSegment(map.toString(), ctx);
        }
        if (raw instanceof List<?> list) {
            return new CommandConfig.TextSegment(list.toString(), defaultCtx);
        }
        return new CommandConfig.TextSegment(raw.toString(), defaultCtx);
    }

    private CommandConfig.ButtonSegment parseButtonSegment(Map<String, Object> container, String nestedKey,
                                                           CommandConfig.PapiContext inheritedCtx, boolean defaultAppendSpace) {
        Map<String, Object> buttonSource = container;
        if (nestedKey != null) {
            buttonSource = normalizeMap(container.get(nestedKey));
        }
        if (buttonSource == null) buttonSource = Map.of();

        String display = firstNonNull(buttonSource.get("text"), buttonSource.get("display"),
                buttonSource.get("label"), "");
        String hover = parseHover(buttonSource.containsKey("hover") ? buttonSource.get("hover") : buttonSource.get("tooltip"));

        Object commandsObj = buttonSource.containsKey("commands") ? buttonSource.get("commands") : buttonSource.get("command");
        List<String> commands = parseCommandList(commandsObj);

        boolean defaultAsPlayer = parseDefaultRunAs(firstNonNull(buttonSource.get("run-as"), buttonSource.get("default-runner"),
                container.get("run-as"), container.get("default-runner")));
        boolean appendSpace = parseAppendSpace(firstNonNull(buttonSource.get("append-space"), buttonSource.get("spacing"),
                container.get("append-space"), container.get("spacing")));
        if (!appendSpace) appendSpace = defaultAppendSpace;

        CommandConfig.PapiContext ctx = CommandConfig.PapiContext.fromString(firstNonNull(buttonSource.get("context"), container.get("context")), inheritedCtx);

        return new CommandConfig.ButtonSegment(display, hover, commands, defaultAsPlayer, appendSpace, ctx);
    }

    private boolean looksLikeButton(Map<String, Object> map) {
        return map.containsKey("commands") || map.containsKey("command") || map.containsKey("hover")
                || map.containsKey("run-as") || map.containsKey("default-runner") || map.containsKey("display")
                || map.containsKey("label");
    }

    private Map<String, Object> normalizeMap(Object source) {
        if (source == null) return new LinkedHashMap<>();
        if (source instanceof ConfigurationSection section) {
            return new LinkedHashMap<>(section.getValues(false));
        }
        if (source instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    map.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return map;
        }
        return new LinkedHashMap<>();
    }

    private String firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return String.valueOf(value);
        }
        return "";
    }

    private String parseHover(Object hoverObj) {
        if (hoverObj == null) return "";
        if (hoverObj instanceof String str) return str;
        if (hoverObj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object part : list) {
                if (part == null) continue;
                if (sb.length() > 0) sb.append("\n");
                sb.append(part);
            }
            return sb.toString();
        }
        return hoverObj.toString();
    }

    private boolean parseDefaultRunAs(Object raw) {
        if (raw == null) return false;
        String s = raw.toString().trim().toLowerCase(Locale.ROOT);
        return s.equals("player") || s.equals("viewer") || s.equals("as_player") || s.equals("player_default");
    }

    private boolean parseAppendSpace(Object raw) {
        if (raw == null) return false;
        if (raw instanceof Boolean b) return b;
        String s = raw.toString().trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("yes") || s.equals("space") || s.equals("1");
    }

    private List<String> parseCommandList(Object raw) {
        List<String> commands = new ArrayList<>();
        if (raw == null) return commands;

        if (raw instanceof String str) {
            addCommandsFromString(commands, str);
        } else if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String str) {
                    addCommandsFromString(commands, str);
                } else if (entry instanceof Map<?, ?> map) {
                    String cmd = parseCommandFromMap(normalizeMap(map));
                    if (cmd != null && !cmd.isBlank()) commands.add(cmd);
                } else if (entry instanceof ConfigurationSection section) {
                    String cmd = parseCommandFromMap(new LinkedHashMap<>(section.getValues(false)));
                    if (cmd != null && !cmd.isBlank()) commands.add(cmd);
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            String cmd = parseCommandFromMap(normalizeMap(map));
            if (cmd != null && !cmd.isBlank()) commands.add(cmd);
        } else if (raw instanceof ConfigurationSection section) {
            String cmd = parseCommandFromMap(new LinkedHashMap<>(section.getValues(false)));
            if (cmd != null && !cmd.isBlank()) commands.add(cmd);
        }

        return commands;
    }

    private void addCommandsFromString(List<String> commands, String raw) {
        if (raw == null) return;
        String[] parts = raw.split("\\s*;\\s*");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) commands.add(trimmed);
        }
    }

    private String parseCommandFromMap(Map<String, Object> map) {
        if (map.isEmpty()) return null;
        if (map.containsKey("player")) {
            return "player:" + String.valueOf(map.get("player")).trim();
        }
        if (map.containsKey("console")) {
            return "console:" + String.valueOf(map.get("console")).trim();
        }
        String command = firstNonNull(map.get("command"), map.get("value"));
        if (command == null || command.isBlank()) return null;
        boolean asPlayer = parseDefaultRunAs(firstNonNull(map.get("run-as"), map.get("executor"), map.get("default-runner")));
        command = command.trim();
        if (asPlayer) return command.startsWith("player:") || command.startsWith("console:") ? command : "player:" + command;
        return command;
    }

    private CommandConfig.Line parseLegacyLine(String raw) {
        LineCtx lc = extractLineContext(raw);
        String working = preprocessBrackets(lc.text);
        CommandConfig.PapiContext lineCtx = lc.ctx;

        List<CommandConfig.Segment> segments = new ArrayList<>();

        int idx = 0;
        while ((idx = working.indexOf("[")) != -1) {
            int end = working.indexOf("]", idx);
            if (end == -1) break;

            String beforeRaw = working.substring(0, idx);
            if (!beforeRaw.isEmpty()) {
                segments.add(new CommandConfig.TextSegment(restoreBrackets(beforeRaw), lineCtx));
            }

            String segment = working.substring(idx + 1, end);
            String[] parts = segment.split("\\|", 4);

            if (parts.length >= 3) {
                String displayRaw = restoreBrackets(parts[0].trim());
                String commandsRaw = restoreBrackets(parts[1].trim());
                String hoverRaw = restoreBrackets(parts[2].trim());
                String flagsRaw = parts.length >= 4 ? restoreBrackets(parts[3].trim()) : "";

                boolean defaultAsPlayer = flagsDefaultAsPlayer(flagsRaw);
                CommandConfig.PapiContext btnCtx = flagsPapiContext(flagsRaw, lineCtx);

                List<String> commands = new ArrayList<>();
                for (String c : commandsRaw.split("\\s*;\\s*")) {
                    String cmd = c.trim();
                    if (cmd.isEmpty()) continue;
                    commands.add(cmd);
                }

                segments.add(new CommandConfig.ButtonSegment(displayRaw, hoverRaw, commands, defaultAsPlayer, true, btnCtx));
            } else {
                segments.add(new CommandConfig.TextSegment(restoreBrackets("[" + segment + "]"), lineCtx));
            }

            working = working.substring(end + 1);
            idx = 0;
        }

        if (!working.isEmpty()) {
            segments.add(new CommandConfig.TextSegment(restoreBrackets(working), lineCtx));
        } else if (segments.isEmpty()) {
            segments.add(new CommandConfig.TextSegment("", lineCtx));
        }

        return new CommandConfig.Line(segments);
    }

    private List<String> encodeCommands(CommandConfig.ButtonSegment button) {
        if (button.commands.isEmpty()) return List.of();
        List<String> encoded = new ArrayList<>(button.commands.size());
        for (String raw : button.commands) {
            if (raw == null) continue;
            String cmd = raw.trim();
            if (cmd.isEmpty()) continue;
            if (!hasExecutorPrefix(cmd) && button.defaultAsPlayer) {
                cmd = "player:" + cmd;
            }
            encoded.add(cmd);
        }
        return encoded;
    }

    private boolean hasExecutorPrefix(String cmd) {
        return cmd.regionMatches(true, 0, "player:", 0, 7)
                || cmd.regionMatches(true, 0, "console:", 0, 8);
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

    private CommandConfig.PapiContext flagsPapiContext(String rawFlags, CommandConfig.PapiContext fallback) {
        if (rawFlags == null) return fallback;
        String f = rawFlags.toLowerCase(Locale.ROOT);
        if (f.contains("ctx=target")) return CommandConfig.PapiContext.TARGET;
        if (f.contains("ctx=viewer")) return CommandConfig.PapiContext.VIEWER;
        return fallback;
    }

    // Detect and strip an optional per-line context directive: {{ctx=viewer}} or {{ctx=target}}
    private static class LineCtx {
        final String text;
        final CommandConfig.PapiContext ctx;
        LineCtx(String text, CommandConfig.PapiContext ctx) { this.text = text; this.ctx = ctx; }
    }
    private LineCtx extractLineContext(String raw) {
        if (raw == null) return new LineCtx("", CommandConfig.PapiContext.VIEWER);
        String s = raw;
        CommandConfig.PapiContext ctx = CommandConfig.PapiContext.VIEWER;
        if (s.startsWith("{{") && s.toLowerCase(Locale.ROOT).startsWith("{{ctx=")) {
            int end = s.indexOf("}}");
            if (end > 0) {
                String inside = s.substring(2, end).trim(); // ctx=target
                String[] kv = inside.split("=", 2);
                if (kv.length == 2) {
                    if (kv[1].equalsIgnoreCase("target")) ctx = CommandConfig.PapiContext.TARGET;
                    if (kv[1].equalsIgnoreCase("viewer")) ctx = CommandConfig.PapiContext.VIEWER;
                }
                s = s.substring(end + 2).trim();
            }
        }
        return new LineCtx(s, ctx);
    }

    private void sendChatMenu(Player viewer, String targetName, CommandConfig cfg) {
        for (CommandConfig.Line line : cfg.lines) {
            Component full = Component.empty();

            for (CommandConfig.Segment segment : line.segments) {
                CommandConfig.PapiContext ctx = segment.context();

                if (segment instanceof CommandConfig.TextSegment textSeg) {
                    if (!textSeg.text.isEmpty()) {
                        full = full.append(parseText(textSeg.text, viewer, targetName, ctx));
                    } else {
                        full = full.append(Component.text(""));
                    }
                    continue;
                }

                if (segment instanceof CommandConfig.ButtonSegment button) {
                    Component displayComp = parseText(button.display, viewer, targetName, ctx);
                    Component hoverComp = parseText(button.hover, viewer, targetName, ctx);

                    List<String> encoded = encodeCommands(button);

                    if (encoded.isEmpty()) {
                        full = full.append(displayComp);
                    } else {
                        String viewerName = viewer.getName();
                        String targetResolved = (cfg.type == CommandType.TARGET)
                                ? (targetName == null ? viewerName : targetName)
                                : viewerName;

                        UUID token = registerPendingBatch(viewerName, targetResolved, encoded);
                        Component clickable = displayComp
                                .hoverEvent(HoverEvent.showText(hoverComp))
                                .clickEvent(ClickEvent.runCommand("/cmrun " + token));
                        full = full.append(clickable);
                    }

                    if (button.appendSpace) {
                        full = full.append(Component.text(" "));
                    }
                }
            }

            viewer.sendMessage(full);
        }
    }

    /* ---------- PlaceholderAPI context handling with Offline fallback ---------- */

    static final class PendingBatch {
        final String viewerName;
        final String targetName;
        final List<String> commands;
        private volatile long expiresAt;

        PendingBatch(String viewerName, String targetName, List<String> commands) {
            this.viewerName = viewerName;
            this.targetName = targetName;
            this.commands = commands;
            touch(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return expiresAt < now;
        }

        void touch(long now) {
            this.expiresAt = now + PENDING_TTL_MS;
        }
    }

    private void purgeExpiredBatches() {
        long now = System.currentTimeMillis();
        pendingBatches.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    UUID registerPendingBatch(String viewerName, String targetName, List<String> commands) {
        purgeExpiredBatches();
        UUID token = UUID.randomUUID();
        pendingBatches.put(token, new PendingBatch(viewerName, targetName, List.copyOf(commands)));
        return token;
    }

    PendingBatch claimPendingBatch(UUID token, String requesterName) {
        purgeExpiredBatches();
        PendingBatch batch = pendingBatches.get(token);
        if (batch == null) return null;
        if (!batch.viewerName.equalsIgnoreCase(requesterName)) return null;

        long now = System.currentTimeMillis();
        if (batch.isExpired(now)) {
            pendingBatches.remove(token);
            return null;
        }

        batch.touch(now);
        return batch;
    }

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

    private Component parseText(String raw, Player viewer, String targetName, CommandConfig.PapiContext ctx) {
        if (raw == null || raw.isEmpty()) return Component.empty();

        String s = raw.replace("%player%", viewer.getName());
        if (targetName != null) s = s.replace("%target%", targetName);

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                if (ctx == CommandConfig.PapiContext.TARGET && targetName != null) {
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
