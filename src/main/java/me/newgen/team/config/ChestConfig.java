package me.newgen.team.config;

import me.newgen.team.model.ChestTier;
import me.newgen.team.model.Team;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class ChestConfig {

    private List<ChestTier> tiers;
    private String pageTitleFormat = "&d&lTeam Chest &7- &f%team% &8(%page%/%pages%)";

    public ChestConfig() {
        this.tiers = ChestTier.defaults();
    }

    public void load(ConfigurationSection chestSection) {
        if (chestSection == null) {
            this.tiers = ChestTier.defaults();
            return;
        }
        String fmt = chestSection.getString("page-title");
        if (fmt != null && !fmt.isEmpty()) this.pageTitleFormat = fmt;
    }

    public void syncFromTiers(TierConfig tierConfig) {
        List<ChestTier> built = new ArrayList<>();
        for (TierConfig.Tier t : tierConfig.tiers()) {
            built.add(new ChestTier(t.level, Math.max(9, t.chestSlots), 0));
        }
        this.tiers = built.isEmpty() ? ChestTier.defaults() : built;
    }

    private int roundToMultipleOfNine(int slots) {
        return ((slots + 8) / 9) * 9;
    }

    public List<ChestTier> tiers() {
        return tiers;
    }

    public int maxLevel() {
        return tiers.size();
    }

    public ChestTier tier(int level) {
        int idx = Math.max(0, Math.min(level - 1, tiers.size() - 1));
        return tiers.get(idx);
    }

    public boolean isMaxLevel(int level) {
        return level >= tiers.size();
    }

    public ChestTier nextTier(int currentLevel) {
        if (isMaxLevel(currentLevel)) return null;
        return tier(currentLevel + 1);
    }

    public String pageTitle(Team team, int page, int pages) {
        return pageTitleFormat
                .replace("%team%", team.name())
                .replace("%page%", String.valueOf(page))
                .replace("%pages%", String.valueOf(pages));
    }
}
