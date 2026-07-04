package me.newgen.team.service;

import me.newgen.team.config.ConfigManager;
import me.newgen.team.config.TierConfig;
import me.newgen.team.hook.PlayerPointsHook;
import me.newgen.team.model.Team;
import me.newgen.team.storage.StorageProvider;

import java.util.UUID;

public final class TierService {

    private final ConfigManager config;
    private final PlayerPointsHook points;
    private final ChestService chestService;
    private final StorageProvider storage;

    public TierService(ConfigManager config, PlayerPointsHook points,
                       ChestService chestService, StorageProvider storage) {
        this.config = config;
        this.points = points;
        this.chestService = chestService;
        this.storage = storage;
    }

    public enum Result {
        SUCCESS, MAX_LEVEL, NO_ECONOMY, INSUFFICIENT_FUNDS, FAILED
    }

    private TierConfig cfg() { return config.tierConfig(); }

    public TierConfig.Tier currentTier(Team team) {
        return cfg().tier(team.tier());
    }

    public TierConfig.Tier nextTier(Team team) {
        return cfg().nextTier(team.tier());
    }

    public boolean isMaxLevel(Team team) {
        return cfg().isMaxLevel(team.tier());
    }

    public long nextPrice(Team team) {
        return cfg().priceFor(team.tier() + 1);
    }

    public int chestSlots(Team team) { return cfg().chestSlots(team.tier()); }
    public int memberLimit(Team team) { return cfg().memberLimit(team.tier()); }
    public int homeLimit(Team team) { return cfg().homeLimit(team.tier()); }

    public Result upgrade(Team team, UUID purchaser) {
        if (isMaxLevel(team)) return Result.MAX_LEVEL;
        if (!points.isAvailable()) return Result.NO_ECONOMY;

        long price = nextPrice(team);
        if (price < 0) return Result.MAX_LEVEL;
        if (!points.has(purchaser, price)) return Result.INSUFFICIENT_FUNDS;
        if (!points.take(purchaser, price)) return Result.INSUFFICIENT_FUNDS;

        try {
            team.tier(team.tier() + 1);
            storage.saveTeam(team);

            chestService.resizeForUpgrade(team);
            return Result.SUCCESS;
        } catch (Exception e) {

            team.tier(team.tier() - 1);
            points.give(purchaser, price);
            return Result.FAILED;
        }
    }
}
