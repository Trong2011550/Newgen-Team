package me.newgen.team.gui;

import me.newgen.team.util.Sounds;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public abstract class PaginatedMenu<T> extends Menu {

    protected static final int CONTENT_SLOTS = 45;
    protected static final int PREV_SLOT = 48;
    protected static final int BACK_SLOT = 49;
    protected static final int NEXT_SLOT = 50;
    protected static final int CLOSE_SLOT = 53;

    protected int page;

    protected PaginatedMenu(Player viewer, int page) {
        super(viewer);
        this.page = Math.max(0, page);
    }

    @Override protected int size() { return 54; }

    protected abstract List<T> elements();

    protected abstract ItemStack render(T element);

    protected abstract void onElementClick(T element, InventoryClickEvent event);

    protected abstract void onBack();

    protected void decorate() {}

    protected int pageSize() { return CONTENT_SLOTS; }

    @Override
    protected void build() {
        decorate();
        List<T> all = elements();
        int pageSize = pageSize();
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        if (page >= totalPages) page = totalPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, all.size());
        for (int i = start; i < end; i++) {
            final T element = all.get(i);
            int slot = i - start;
            set(slot, render(element), e -> onElementClick(element, e));
        }

        set(BACK_SLOT, Icons.back(), e -> {
            Sounds.click(viewer);
            onBack();
        });
        set(CLOSE_SLOT, Icons.close(), e -> {
            Sounds.click(viewer);
            viewer.closeInventory();
        });

        if (page > 0) {
            set(PREV_SLOT, Icons.prevPage(page), e -> {
                Sounds.page(viewer);
                page--;
                refresh();
                viewer.openInventory(inventory);
            });
        } else {
            set(PREV_SLOT, Icons.disabledPage());
        }

        if (page < totalPages - 1) {
            set(NEXT_SLOT, Icons.nextPage(page + 2), e -> {
                Sounds.page(viewer);
                page++;
                refresh();
                viewer.openInventory(inventory);
            });
        } else {
            set(NEXT_SLOT, Icons.disabledPage());
        }
    }
}
