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

    public void open() {
        Schedulers.entity(viewer, () -> {
            if (inventory == null) {
                inventory = Bukkit.createInventory(holder, size(), Text.auto(title()));
                holder.setInventory(inventory);
            }
            refresh();
            viewer.openInventory(inventory);
            Sounds.open(viewer);
        });
    }

    public void refresh() {
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
        if (onClick != null) handlers.put(slot, onClick);
    }

    public boolean handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
            return true;
        }
        return false;
    }

    public boolean allowItemMovement() {
        return false;
    }

    public void onClose() {}

    public Player viewer() { return viewer; }
    public Inventory inventory() { return inventory; }

    protected boolean isLeft(ClickType type) { return type.isLeftClick(); }
    protected boolean isRight(ClickType type) { return type.isRightClick(); }
    protected boolean isShift(ClickType type) { return type.isShiftClick(); }
}
