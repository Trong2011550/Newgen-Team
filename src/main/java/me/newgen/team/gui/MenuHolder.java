package me.newgen.team.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private Inventory inventory;

    public MenuHolder(Menu menu) {
        this.menu = menu;
    }

    public Menu menu() { return menu; }

    void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
