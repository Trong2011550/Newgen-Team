package me.newgen.team.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class TierConfig {

    public static final class Tier {
        public final int level;
        public final int chestSlots;
        public final int memberLimit;
        public final int homeLimit;
        public final long price;

        public Tier(int level, int chestSlots, int memberLimit, int homeLimit, long price) {
            this.level = level;
            this.chestSlots = chestSlots;
            this.memberLimit = memberLimit;
            this.homeLimit = homeLimit;
            this.price = price;
        }
    }

    private List<Tier> tiers = defaults();

    public void load(ConfigurationSection section) {
        if (section == null) {
            this.tiers = defaults();
            return;
        }
        List<Tier> loaded = new ArrayList<>();
        int level = 1;
        while (section.contains(String.valueOf(level))) {
            ConfigurationSection s = section.getConfigurationSection(String.valueOf(level));
            if (s == null) break;

            int slots;
            if (s.contains("pages")) {
                int pages = Math.max(1, s.getInt("pages", 1));
                slots = pages * SLOTS_PER_PAGE;
            } else {
                slots = roundToMultipleOfNine(s.getInt("chest-slots", 45));
            }
            int members = Math.max(1, s.getInt("member-limit", 5));
            int homes = Math.max(1, s.getInt("home-limit", 1));
            long price = Math.max(0, s.getLong("price", 0));
            loaded.add(new Tier(level, Math.max(9, slots), members, homes, price));
            level++;
        }
        this.tiers = loaded.isEmpty() ? defaults() : loaded;
    }

    private static final int SLOTS_PER_PAGE = 45;

    private static List<Tier> defaults() {
        List<Tier> t = new ArrayList<>();

        t.add(new Tier(1, 1 * SLOTS_PER_PAGE, 5, 1, 0));
        t.add(new Tier(2, 3 * SLOTS_PER_PAGE, 8, 2, 50_000));
        t.add(new Tier(3, 5 * SLOTS_PER_PAGE, 12, 3, 150_000));
        t.add(new Tier(4, 7 * SLOTS_PER_PAGE, 20, 5, 200_000));
        return t;
    }

    private int roundToMultipleOfNine(int slots) {
        return ((slots + 8) / 9) * 9;
    }

    public List<Tier> tiers() { return tiers; }

    public int maxLevel() { return tiers.size(); }

    public Tier tier(int level) {
        int idx = Math.max(0, Math.min(level - 1, tiers.size() - 1));
        return tiers.get(idx);
    }

    public boolean isMaxLevel(int level) {
        return level >= tiers.size();
    }

    public Tier nextTier(int currentLevel) {
        if (isMaxLevel(currentLevel)) return null;
        return tier(currentLevel + 1);
    }

    public int chestSlots(int level) { return tier(level).chestSlots; }
    public int memberLimit(int level) { return tier(level).memberLimit; }
    public int homeLimit(int level) { return tier(level).homeLimit; }

    public long priceFor(int nextLevel) {
        if (nextLevel < 1 || nextLevel > tiers.size()) return -1;
        return tiers.get(nextLevel - 1).price;
    }
}
