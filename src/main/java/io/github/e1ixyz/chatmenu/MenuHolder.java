package io.github.e1ixyz.chatmenu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * InventoryHolder that tags a ChatMenu chest GUI and carries everything the click
 * listener needs: which menu/target/context is open, the current page, the slot→button
 * mapping, and the navigation slot indices.
 */
public class MenuHolder implements InventoryHolder {

    final CommandConfig cfg;
    final String targetName;   // may be null for self menus
    final String context;      // may be blank
    final int page;

    // slot index -> button to run when clicked
    final Map<Integer, CommandConfig.ButtonSegment> buttons = new HashMap<>();

    int prevSlot = -1;
    int nextSlot = -1;
    int closeSlot = -1;

    private Inventory inventory;

    MenuHolder(CommandConfig cfg, String targetName, String context, int page) {
        this.cfg = cfg;
        this.targetName = targetName;
        this.context = context == null ? "" : context;
        this.page = page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    CommandConfig.ButtonSegment buttonAt(int slot) {
        return buttons.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
