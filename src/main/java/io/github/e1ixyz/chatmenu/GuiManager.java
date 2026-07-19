package io.github.e1ixyz.chatmenu;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders ChatMenu configs as chest GUIs and handles clicks.
 *
 * Layout: each config line becomes a "group" that starts on a new row. Leading text
 * (before the first button) and trailing text (after the last button) become the group's
 * label item; inter-button separators (", ") are dropped. Buttons wrap every 9 slots.
 * Rows over 6 paginate (5 content rows + a nav row). Non-clickable slots are filler panes.
 */
public class GuiManager implements Listener {

    private static final int COLS = 9;
    private static final int MAX_ROWS = 6;
    private static final int CONTENT_ROWS_PER_PAGE = 5; // remaining row is nav
    private static final long PROMPT_TTL_MS = 60_000L;

    private final ChatMenu plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    GuiManager(ChatMenu plugin) {
        this.plugin = plugin;
    }

    private record Cell(ItemStack item, CommandConfig.ButtonSegment button) {}
    private record Group(String label, CommandConfig.PapiContext labelCtx, List<CommandConfig.ButtonSegment> buttons) {}
    private record Prompt(CommandConfig cfg, String targetName, CommandConfig.ButtonSegment button, long expiresAt) {}

    /* ------------------------------ opening ----------------------------- */

    void open(Player viewer, CommandConfig cfg, String targetName, String context, int page) {
        List<List<Cell>> rows = buildRows(viewer, cfg, targetName, context);
        if (rows.isEmpty()) rows.add(new ArrayList<>());

        int totalRows = rows.size();
        boolean paginate = totalRows > MAX_ROWS;

        int size;
        List<List<Cell>> pageRows;
        int pages = 1;
        if (!paginate) {
            size = totalRows * COLS;
            pageRows = rows;
            page = 0;
        } else {
            pages = (totalRows + CONTENT_ROWS_PER_PAGE - 1) / CONTENT_ROWS_PER_PAGE;
            page = Math.max(0, Math.min(page, pages - 1));
            int from = page * CONTENT_ROWS_PER_PAGE;
            int to = Math.min(from + CONTENT_ROWS_PER_PAGE, totalRows);
            pageRows = rows.subList(from, to);
            size = MAX_ROWS * COLS; // 54
        }

        MenuHolder holder = new MenuHolder(cfg, targetName, context, page);
        Component title = plugin.parseText(plugin.guiTitleTemplate().replace("%menu%", cfg.name),
                viewer, targetName, context, CommandConfig.PapiContext.VIEWER);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        ItemStack filler = namedItem(plugin.guiFiller(), Component.text(" "));
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        for (int r = 0; r < pageRows.size(); r++) {
            List<Cell> row = pageRows.get(r);
            int n = Math.min(row.size(), COLS);
            int startCol = (COLS - n) / 2; // center the row's items
            for (int c = 0; c < n; c++) {
                int slot = r * COLS + startCol + c;
                Cell cell = row.get(c);
                inv.setItem(slot, cell.item());
                if (cell.button() != null) holder.buttons.put(slot, cell.button());
            }
        }

        if (paginate) {
            int base = (MAX_ROWS - 1) * COLS; // 45
            if (page > 0) {
                holder.prevSlot = base;
                inv.setItem(holder.prevSlot, namedItem(Material.ARROW, text("&a« Previous Page")));
            }
            holder.closeSlot = base + 4;
            inv.setItem(holder.closeSlot, namedItem(Material.BARRIER,
                    text("&cClose &8(Page " + (page + 1) + "/" + pages + ")")));
            if (page < pages - 1) {
                holder.nextSlot = base + 8;
                inv.setItem(holder.nextSlot, namedItem(Material.ARROW, text("&aNext Page »")));
            }
        }

        viewer.openInventory(inv);
    }

    private List<List<Cell>> buildRows(Player viewer, CommandConfig cfg, String targetName, String context) {
        List<Group> groups = flatten(viewer, cfg);
        List<List<Cell>> rows = new ArrayList<>();
        for (Group g : groups) {
            List<Cell> row = new ArrayList<>();
            rows.add(row);
            if (!g.label().isBlank()) {
                row.add(new Cell(namedItem(plugin.guiLabelIcon(),
                        plugin.parseText(g.label(), viewer, targetName, context, g.labelCtx())), null));
            }
            for (CommandConfig.ButtonSegment b : g.buttons()) {
                if (row.size() == COLS) {
                    row = new ArrayList<>();
                    rows.add(row);
                }
                row.add(new Cell(buttonItem(b, viewer, targetName, context), b));
            }
        }
        return rows;
    }

    /** Turn a menu's lines into label+buttons groups, honouring the same permission gates as chat. */
    private List<Group> flatten(Player viewer, CommandConfig cfg) {
        List<Group> groups = new ArrayList<>();
        for (CommandConfig.Line line : cfg.lines) {
            if (!plugin.hasPermission(viewer, line.permission)) continue;

            List<CommandConfig.Segment> visible = new ArrayList<>();
            for (CommandConfig.Segment seg : line.segments) {
                if (plugin.hasPermission(viewer, seg.permission())) visible.add(seg);
            }

            List<CommandConfig.ButtonSegment> buttons = new ArrayList<>();
            int first = -1, last = -1;
            for (int i = 0; i < visible.size(); i++) {
                if (visible.get(i) instanceof CommandConfig.ButtonSegment b) {
                    if (first < 0) first = i;
                    last = i;
                    buttons.add(b);
                }
            }

            StringBuilder sb = new StringBuilder();
            CommandConfig.PapiContext labelCtx = CommandConfig.PapiContext.VIEWER;
            boolean ctxSet = false;
            if (first < 0) {
                // text-only line -> whole thing is a header label
                for (CommandConfig.Segment seg : visible) {
                    if (seg instanceof CommandConfig.TextSegment ts && appendLabel(sb, ts.text) && !ctxSet) {
                        labelCtx = ts.context();
                        ctxSet = true;
                    }
                }
            } else {
                // keep leading text (before first button) + trailing text (after last button); drop separators
                for (int i = 0; i < first; i++) {
                    if (visible.get(i) instanceof CommandConfig.TextSegment ts && appendLabel(sb, ts.text) && !ctxSet) {
                        labelCtx = ts.context();
                        ctxSet = true;
                    }
                }
                for (int i = last + 1; i < visible.size(); i++) {
                    if (visible.get(i) instanceof CommandConfig.TextSegment ts && appendLabel(sb, ts.text) && !ctxSet) {
                        labelCtx = ts.context();
                        ctxSet = true;
                    }
                }
            }
            String label = sb.toString().strip();

            if (label.isBlank() && buttons.isEmpty()) continue; // blank separator line
            groups.add(new Group(label, labelCtx, buttons));
        }
        return groups;
    }

    private boolean appendLabel(StringBuilder sb, String text) {
        if (text == null) return false;
        String t = text.strip();
        if (t.isEmpty()) return false;
        if (sb.length() > 0) sb.append(' ');
        sb.append(t);
        return true;
    }

    /* ---------------------------- item helpers -------------------------- */

    private ItemStack buttonItem(CommandConfig.ButtonSegment b, Player viewer, String target, String context) {
        Material mat = plugin.resolveMaterial(b.icon, plugin.guiDefaultIcon());
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(plugin.parseText(b.display, viewer, target, context, b.context())));
            if (b.hover != null && !b.hover.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String ln : b.hover.split("\n", -1)) {
                    lore.add(noItalic(plugin.parseText(ln, viewer, target, context, b.context())));
                }
                meta.lore(lore);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack namedItem(Material mat, Component name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(name));
            it.setItemMeta(meta);
        }
        return it;
    }

    private Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private Component text(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        return s.contains("&")
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(s)
                : plugin.miniMessage().deserialize(s);
    }

    /* ------------------------------ clicks ------------------------------ */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder holder)) return;
        e.setCancelled(true); // menu is read-only; never let items move
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int topSize = e.getView().getTopInventory().getSize();
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= topSize) return; // clicked own inventory

        if (slot == holder.prevSlot) { open(p, holder.cfg, holder.targetName, holder.context, holder.page - 1); return; }
        if (slot == holder.nextSlot) { open(p, holder.cfg, holder.targetName, holder.context, holder.page + 1); return; }
        if (slot == holder.closeSlot) { p.closeInventory(); return; }

        CommandConfig.ButtonSegment btn = holder.buttonAt(slot);
        if (btn == null) return; // label or filler

        String ctx = holder.context;
        if ((ctx == null || ctx.isBlank()) && plugin.buttonNeedsContext(btn)) {
            startPrompt(p, holder, btn); // startPrompt closes the inventory itself
            return;
        }
        // Close the menu on click, unless this button opens another ChatMenu menu
        // (that sub-menu will replace the inventory on the next tick).
        if (!plugin.buttonOpensMenu(btn)) {
            p.closeInventory();
        }
        plugin.runButton(p, holder.cfg, holder.targetName, ctx, btn);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }

    /* --------------------------- text prompt ---------------------------- */

    private void startPrompt(Player p, MenuHolder holder, CommandConfig.ButtonSegment btn) {
        p.closeInventory();
        prompts.put(p.getUniqueId(), new Prompt(holder.cfg, holder.targetName, btn,
                System.currentTimeMillis() + PROMPT_TTL_MS));
        p.sendMessage(text("&eType a reason in chat, or type &ccancel&e to abort. &8(60s)"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Prompt prompt = prompts.remove(e.getPlayer().getUniqueId());
        if (prompt == null) return;
        e.setCancelled(true); // capture the reason; don't broadcast it

        final Player p = e.getPlayer();
        if (System.currentTimeMillis() > prompt.expiresAt()) {
            p.sendMessage(text("&cThat menu prompt expired."));
            return;
        }
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (msg.isEmpty() || msg.equalsIgnoreCase("cancel")) {
            p.sendMessage(text("&7Cancelled."));
            return;
        }
        // hop back to the main thread to run commands
        Bukkit.getScheduler().runTask(plugin,
                () -> plugin.runButton(p, prompt.cfg(), prompt.targetName(), msg, prompt.button()));
    }

    void shutdown() {
        prompts.clear();
    }
}
