package me.newgen.team.service;

import me.newgen.team.config.ChestConfig;
import me.newgen.team.model.ChestTier;
import me.newgen.team.model.Team;
import me.newgen.team.scheduler.Schedulers;
import me.newgen.team.storage.StorageProvider;
import me.newgen.team.util.Inventories;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ChestService {

    private final Logger log;
    private final StorageProvider storage;
    private final ChestConfig chestConfig;

    private final Map<UUID, ChestHolder> holders = new ConcurrentHashMap<>();

    public ChestService(Logger log, StorageProvider storage, ChestConfig chestConfig) {
        this.log = log;
        this.storage = storage;
        this.chestConfig = chestConfig;
    }

    public final class ChestHolder implements InventoryHolder {
        private final UUID teamId;
        private final int page;
        private Inventory inventory;

        ChestHolder(UUID teamId, int page) {
            this.teamId = teamId;
            this.page = page;
        }

        public UUID teamId() { return teamId; }
        public int page() { return page; }
        @Override public Inventory getInventory() { return inventory; }
        void setInventory(Inventory inv) { this.inventory = inv; }
    }

    private static final class TeamChestData {
        final Team team;
        final List<Inventory> pages = new ArrayList<>();
        int viewers = 0;
        boolean dirty = false;

        TeamChestData(Team team) {
            this.team = team;
        }
    }

    private final Map<UUID, TeamChestData> data = new ConcurrentHashMap<>();

    public ChestTier tier(Team team) {
        return chestConfig.tier(team.chestLevel());
    }

    public int pages(Team team) {
        return tier(team).pages();
    }

    public void open(Player viewer, Team team, int page) {
        final UUID teamId = team.id();

        ensureLoaded(team, () -> Schedulers.entity(viewer, () -> {
            team.chestLock.lock();
            try {
                TeamChestData d = data.get(teamId);
                if (d == null) return;
                int clamped = Math.max(0, Math.min(page, d.pages.size() - 1));
                Inventory inv = d.pages.get(clamped);
                d.viewers++;
                viewer.openInventory(inv);
            } finally {
                team.chestLock.unlock();
            }
        }));
    }

    private void ensureLoaded(Team team, Runnable then) {
        final UUID teamId = team.id();
        if (data.containsKey(teamId)) { then.run(); return; }
        storage.loadChest(teamId).whenComplete((blob, ex) -> {
            if (ex != null) {
                log.severe("Failed loading chest for " + teamId + ": " + ex.getMessage());
                return;
            }

            Schedulers.global(() -> {
                team.chestLock.lock();
                try {
                    if (!data.containsKey(teamId)) {
                        TeamChestData d = buildPages(team, blob);
                        data.put(teamId, d);
                    }
                } finally {
                    team.chestLock.unlock();
                }
                then.run();
            });
        });
    }

    private TeamChestData buildPages(Team team, byte[] blob) {
        ChestTier t = tier(team);
        int totalSlots = t.slots;
        int pageCount = t.pages();
        ItemStack[] stored = blob == null ? new ItemStack[0] : Inventories.fromBytes(blob);

        TeamChestData d = new TeamChestData(team);
        for (int p = 0; p < pageCount; p++) {

            ChestHolder holder = new ChestHolder(team.id(), p);
            String title = chestConfig.pageTitle(team, p + 1, pageCount);
            Inventory inv = Bukkit.createInventory(holder, NAV_ROW_START + 9, me.newgen.team.util.Text.legacy(title));
            holder.setInventory(inv);

            int storageThisPage = Math.min(ChestTier.STORAGE_PER_PAGE, Math.max(0, totalSlots - p * ChestTier.STORAGE_PER_PAGE));
            for (int i = 0; i < storageThisPage; i++) {
                int globalIndex = p * ChestTier.STORAGE_PER_PAGE + i;
                if (globalIndex < stored.length && stored[globalIndex] != null) {
                    inv.setItem(i, stored[globalIndex]);
                }
            }

            decorateNav(inv, p, pageCount);
            d.pages.add(inv);
        }
        return d;
    }

    public static final int NAV_ROW_START = 45;

    public static final int BACK_SLOT = 49;
    public static final int PREV_SLOT = 48;
    public static final int NEXT_SLOT = 50;

    private void decorateNav(Inventory inv, int page, int pageCount) {
        org.bukkit.inventory.ItemStack edge = me.newgen.team.gui.Icons.edgeFiller();
        for (int i = NAV_ROW_START; i < NAV_ROW_START + 9; i++) {
            inv.setItem(i, edge);
        }
        if (page > 0) {
            inv.setItem(PREV_SLOT, me.newgen.team.gui.Icons.prevPage(page));
        } else {
            inv.setItem(PREV_SLOT, me.newgen.team.gui.Icons.disabledPage());
        }
        if (page < pageCount - 1) {
            inv.setItem(NEXT_SLOT, me.newgen.team.gui.Icons.nextPage(page + 2));
        } else {
            inv.setItem(NEXT_SLOT, me.newgen.team.gui.Icons.disabledPage());
        }
        inv.setItem(BACK_SLOT, me.newgen.team.gui.Icons.back());
    }

    public void onClose(Team team) {
        final UUID teamId = team.id();
        team.chestLock.lock();
        try {
            TeamChestData d = data.get(teamId);
            if (d == null) return;
            d.viewers = Math.max(0, d.viewers - 1);
            d.dirty = true;
            if (d.viewers == 0) {
                byte[] blob = snapshot(d);
                storage.saveChest(teamId, blob);

            }
        } finally {
            team.chestLock.unlock();
        }
    }

    public void markDirty(Team team) {
        TeamChestData d = data.get(team.id());
        if (d != null) d.dirty = true;
    }

    private byte[] snapshot(TeamChestData d) {
        List<ItemStack> flat = new ArrayList<>();
        for (Inventory inv : d.pages) {
            for (int i = 0; i < ChestTier.STORAGE_PER_PAGE; i++) {
                flat.add(inv.getItem(i));
            }
        }
        return Inventories.toBytes(flat.toArray(new ItemStack[0]));
    }

    public void resizeForUpgrade(Team team) {
        final UUID teamId = team.id();
        Schedulers.global(() -> {
            team.chestLock.lock();
            try {
                TeamChestData old = data.get(teamId);
                byte[] blob;
                if (old != null) {

                    for (Inventory inv : old.pages) {
                        new ArrayList<>(inv.getViewers()).forEach(hv -> {
                            if (hv instanceof Player pl) Schedulers.entity(pl, pl::closeInventory);
                        });
                    }
                    blob = snapshot(old);
                } else {
                    blob = null;
                }
                TeamChestData fresh = buildPages(team, blob);
                data.put(teamId, fresh);
                storage.saveChest(teamId, snapshot(fresh));
            } finally {
                team.chestLock.unlock();
            }
        });
    }

    public void flushAll() {
        for (Map.Entry<UUID, TeamChestData> e : data.entrySet()) {
            TeamChestData d = e.getValue();
            if (!d.dirty) continue;
            d.team.chestLock.lock();
            try {
                if (!d.dirty) continue;
                byte[] blob = snapshot(d);
                storage.saveChest(e.getKey(), blob);
                d.dirty = false;
            } finally {
                d.team.chestLock.unlock();
            }
        }
    }

    public void saveAllOnShutdown() {
        for (Map.Entry<UUID, TeamChestData> e : data.entrySet()) {
            TeamChestData d = e.getValue();
            d.team.chestLock.lock();
            try {
                byte[] blob = snapshot(d);
                storage.saveChest(e.getKey(), blob);
            } finally {
                d.team.chestLock.unlock();
            }
        }
    }

    public boolean isChestInventory(Inventory inv) {
        return inv != null && inv.getHolder() instanceof ChestHolder;
    }

    public void resetChest(Team team) {
        final UUID teamId = team.id();
        Schedulers.global(() -> {
            team.chestLock.lock();
            try {
                TeamChestData d = data.get(teamId);
                if (d != null) {

                    for (Inventory inv : d.pages) {
                        new ArrayList<>(inv.getViewers()).forEach(hv -> {
                            if (hv instanceof Player pl) Schedulers.entity(pl, pl::closeInventory);
                        });
                    }
                    for (Inventory inv : d.pages) {
                        for (int i = 0; i < ChestTier.STORAGE_PER_PAGE; i++) {
                            inv.setItem(i, null);
                        }
                    }
                    d.dirty = false;
                    storage.saveChest(teamId, snapshot(d));
                } else {

                    storage.saveChest(teamId, Inventories.toBytes(new ItemStack[0]));
                }
            } finally {
                team.chestLock.unlock();
            }
        });
    }

    public void invalidate(Team team) {
        final UUID teamId = team.id();
        team.chestLock.lock();
        try {
            TeamChestData d = data.remove(teamId);
            if (d != null) {

                for (Inventory inv : d.pages) {
                    new ArrayList<>(inv.getViewers()).forEach(hv -> {
                        if (hv instanceof Player pl) Schedulers.entity(pl, pl::closeInventory);
                    });
                }
                storage.saveChest(teamId, snapshot(d));
            }
        } finally {
            team.chestLock.unlock();
        }
    }
}
