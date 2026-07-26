package me.newgen.team.model;

import java.util.ArrayList;
import java.util.List;

public final class ChestTier {
    public final int level;
    public final int slots;
    public final long price;
    public static final int STORAGE_PER_PAGE = 45;

    public ChestTier(int level, int slots, long price) {
        this.level = level;
        this.slots = slots;
        this.price = price;
    }

    public static List<ChestTier> defaults() {
        ArrayList<ChestTier> tiers = new ArrayList<ChestTier>();
        tiers.add(new ChestTier(1, 27, 0L));
        tiers.add(new ChestTier(2, 54, 50000L));
        tiers.add(new ChestTier(3, 81, 120000L));
        tiers.add(new ChestTier(4, 108, 250000L));
        return tiers;
    }

    public int pages() {
        return Math.max(1, (int)Math.ceil((double)this.slots / 45.0));
    }
}
