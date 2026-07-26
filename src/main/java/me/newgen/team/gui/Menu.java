package me.newgen.team.gui;

import me.newgen.team.scheduler.Schedulers;
import me.newgen.team.util.Sounds;
import me.newgen.team.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class Menu {

    protected final Player viewer;
    protected Inventory inventory;
    protected final MenuHolder holder;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    protected Menu(Player viewer) {
        this.viewer = viewer;
        this.holder = new MenuHolder(this);
    }

    protected abstract int size();

    protected abstract String title();

    protected abstract void build();

    /** Set via abort() when the menu should close instead of open. */
    private boolean aborted;

    public void open() {
        Schedulers.entity(viewer, () -> {
            if (inventory == null) {
                inventory = Bukkit.createInventory(holder, size(), Text.auto(title()));
                holder.setInventory(inventory);
            }
            refresh();
            if (aborted) return;
            viewer.openInventory(inventory);
            Sounds.open(viewer);
        });
    }

    /** Closes the menu instead of showing it, e.g. when the viewer's team is gone. */
    protected void abort() {
        aborted = true;
        viewer.closeInventory();
    }

    public void refresh() {
        aborted = false;
        handlers.clear();
        if (inventory != null) inventory.clear();
        build();
    }

    protected void set(int slot, ItemStack item) {
        if (inventory != null && slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        set(slot, item);
        // Handlers outside the menu bounds would target the viewer's own inventory.
        if (onClick != null && inventory != null && slot >= 0 && slot < inventory.getSize()) {
            handlers.put(slot, onClick);
        }
    }

    public boolean handleClick(InventoryClickEvent event) {
        // A refresh may have shifted elements between the two clicks of a double-click.
        if (event.getClick() == ClickType.DOUBLE_CLICK) return false;
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
            return true;
        }
        return false;
    }

    public void onClose() {}

    public Player viewer() { return viewer; }
    public Inventory inventory() { return inventory; }

    protected boolean isLeft(ClickType type) { return type.isLeftClick(); }
    protected boolean isRight(ClickType type) { return type.isRightClick(); }
    protected boolean isShift(ClickType type) { return type.isShiftClick(); }
}
