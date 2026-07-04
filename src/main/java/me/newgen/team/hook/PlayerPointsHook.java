package me.newgen.team.hook;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerPointsHook {

    private final Logger log;
    private boolean available;
    private PlayerPointsAPI api;

    public PlayerPointsHook(Logger log) {
        this.log = log;
    }

    public void hook() {
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) {
            available = false;
            log.warning("PlayerPoints not found - upgrade economy is disabled.");
            return;
        }
        try {
            this.api = PlayerPoints.getInstance().getAPI();
            this.available = api != null;
            if (available) {
                log.info("Hooked into PlayerPoints economy.");
            } else {
                log.warning("PlayerPoints API unavailable - upgrade economy is disabled.");
            }
        } catch (Throwable t) {
            available = false;
            log.log(Level.WARNING, "Failed to hook into PlayerPoints", t);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private int clamp(long amount) {
        if (amount > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (amount < 0) return 0;
        return (int) amount;
    }

    public long balance(UUID player) {
        if (!available) return 0;
        try {
            return api.look(player);
        } catch (Throwable t) {
            log.log(Level.WARNING, "PlayerPoints balance lookup failed", t);
            return 0;
        }
    }

    public boolean has(UUID player, long amount) {
        if (!available || amount <= 0) return available;
        try {
            return api.look(player) >= amount;
        } catch (Throwable t) {
            log.log(Level.WARNING, "PlayerPoints balance check failed", t);
            return false;
        }
    }

    public boolean take(UUID player, long amount) {
        if (!available || amount <= 0) return false;
        try {
            return api.take(player, clamp(amount));
        } catch (Throwable t) {
            log.log(Level.WARNING, "PlayerPoints take failed", t);
            return false;
        }
    }

    public boolean give(UUID player, long amount) {
        if (!available || amount <= 0) return false;
        try {
            return api.give(player, clamp(amount));
        } catch (Throwable t) {
            log.log(Level.WARNING, "PlayerPoints give failed", t);
            return false;
        }
    }

    public String currencyName() {
        return "\u0111i\u1ec3m";
    }
}
