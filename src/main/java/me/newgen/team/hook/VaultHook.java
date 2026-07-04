package me.newgen.team.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VaultHook {

    private final Logger log;
    private boolean available;
    private Economy economy;

    public VaultHook(Logger log) {
        this.log = log;
    }

    public void hook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            available = false;
            log.warning("Vault not found - economy features are disabled.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            available = false;
            log.warning("No Vault economy provider found - economy features are disabled.");
            return;
        }
        this.economy = rsp.getProvider();
        this.available = true;
        log.info("Hooked into Vault economy: " + economy.getName());
    }

    public boolean isAvailable() {
        return available;
    }

    private OfflinePlayer player(UUID uuid) {
        return Bukkit.getOfflinePlayer(uuid);
    }

    public long balance(UUID player) {
        if (!available) return 0;
        try {
            return (long) economy.getBalance(player(player));
        } catch (Throwable t) {
            log.log(Level.WARNING, "Vault balance lookup failed", t);
            return 0;
        }
    }

    public boolean has(UUID player, long amount) {
        return available && economy.has(player(player), (double) amount);
    }

    public boolean take(UUID player, long amount) {
        if (!available || amount <= 0) return false;
        try {
            return economy.withdrawPlayer(player(player), (double) amount).transactionSuccess();
        } catch (Throwable t) {
            log.log(Level.WARNING, "Vault withdraw failed", t);
            return false;
        }
    }

    public boolean give(UUID player, long amount) {
        if (!available || amount <= 0) return false;
        try {
            return economy.depositPlayer(player(player), (double) amount).transactionSuccess();
        } catch (Throwable t) {
            log.log(Level.WARNING, "Vault deposit failed", t);
            return false;
        }
    }

    public String currencyName() {
        if (!available) return "$";
        try {
            return economy.currencyNamePlural();
        } catch (Throwable t) {
            return "$";
        }
    }
}
