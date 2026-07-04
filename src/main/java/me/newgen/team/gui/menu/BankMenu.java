package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.Team;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.scheduler.Schedulers;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class BankMenu extends Menu {

    private final NewGenTeamPlugin plugin;

    public BankMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 54; }
    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? L("gui.bank.title") : L("gui.bank.title-team", "team", t.name());
    }

    @Override
    protected void build() {

        for (int i = 0; i < size(); i++) {
            boolean edge = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            set(i, edge ? Icons.edgeFiller() : Icons.filler());
        }

        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) { viewer.closeInventory(); return; }

        UUID uuid = viewer.getUniqueId();
        boolean economy = plugin.points().isAvailable();
        long personal = economy ? plugin.points().balance(uuid) : 0;
        boolean canWithdraw = plugin.permissions().has(team, uuid, TeamPermission.WITHDRAW_BALANCE);

        String currency = plugin.points().currencyName();
        set(4, ItemBuilder.of(Material.GOLD_BLOCK)
                .name(L("gui.bank.title-team", "team", team.name()))
                .lore(L("gui.bank.team-balance", "balance", team.balance()),
                        "",
                        L("gui.main.bank-lore"))
                .glow(true).build());

        set(22, ItemBuilder.of(Material.SUNFLOWER)
                .name(L("gui.bank.your-balance"))
                .lore(economy
                        ? L("gui.bank.personal-wallet", "balance", personal)
                        : L("bank.no-economy")).glow(true).build());

        set(38, ItemBuilder.of(Material.LIME_DYE)
                .name(L("gui.bank.deposit"))
                .lore(L("gui.bank.deposit-lore1"),
                        L("gui.bank.deposit-lore2"),
                        "",
                        L("gui.bank.amount-hint"))
                .glow(true).build(),
                e -> { Sounds.click(viewer); promptDeposit(team); });

        if (canWithdraw) {
            set(42, ItemBuilder.of(Material.RED_DYE)
                    .name(L("gui.bank.withdraw"))
                    .lore(L("gui.bank.withdraw-lore1"),
                            L("gui.bank.withdraw-lore2"),
                            "",
                            L("gui.bank.amount-hint"))
                    .glow(true).build(),
                    e -> { Sounds.click(viewer); promptWithdraw(team); });
        } else {
            set(42, ItemBuilder.of(Material.GRAY_DYE)
                    .name(L("gui.bank.withdraw-locked"))
                    .lore(L("gui.bank.no-withdraw"),
                            L("gui.bank.withdraw-locked-lore")).build());
        }

        set(48, Icons.back(), e -> { Sounds.click(viewer); plugin.menus().openMain(viewer); });
        set(50, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });

        set(49, Icons.edgeFiller());
    }

    private void promptDeposit(Team team) {
        if (!plugin.points().isAvailable()) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "bank.no-economy");
            return;
        }
        plugin.messages().send(viewer, "bank.prompt-deposit");
        viewer.closeInventory();
        plugin.signInput().await(viewer, raw -> handleDeposit(raw));
    }

    private void promptWithdraw(Team team) {
        if (!plugin.points().isAvailable()) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "bank.no-economy");
            return;
        }
        plugin.messages().send(viewer, "bank.prompt-withdraw");
        viewer.closeInventory();
        plugin.signInput().await(viewer, raw -> handleWithdraw(raw));
    }

    private void handleDeposit(String raw) {

        if (raw == null) { reopen(); return; }
        long amount = parseAmount(raw);
        if (amount <= 0) { Sounds.error(viewer); plugin.messages().send(viewer, "bank.invalid-amount"); reopen(); return; }

        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return;

        if (!plugin.points().has(viewer.getUniqueId(), amount)) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "bank.not-enough-personal");
            reopen();
            return;
        }

        team.chestLock.lock();
        try {
            if (!plugin.points().take(viewer.getUniqueId(), amount)) {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "bank.not-enough-personal");
                reopen();
                return;
            }
            team.balance(team.balance() + amount);
        } finally {
            team.chestLock.unlock();
        }
        plugin.storage().saveTeam(team);
        Sounds.success(viewer);
        plugin.messages().send(viewer, "bank.deposit-success",
                "amount", amount, "balance", team.balance());
        reopen();
    }

    private void handleWithdraw(String raw) {
        if (raw == null) { reopen(); return; }
        long amount = parseAmount(raw);
        if (amount <= 0) { Sounds.error(viewer); plugin.messages().send(viewer, "bank.invalid-amount"); reopen(); return; }

        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return;

        if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.WITHDRAW_BALANCE)) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "general.no-permission");
            reopen();
            return;
        }

        boolean ok;
        team.chestLock.lock();
        try {
            if (team.balance() < amount) {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "bank.not-enough-team");
                reopen();
                return;
            }
            team.balance(team.balance() - amount);
            ok = true;
        } finally {
            team.chestLock.unlock();
        }

        if (ok) {
            if (!plugin.points().give(viewer.getUniqueId(), amount)) {

                team.chestLock.lock();
                try { team.balance(team.balance() + amount); }
                finally { team.chestLock.unlock(); }
                Sounds.error(viewer);
                plugin.messages().send(viewer, "bank.no-economy");
                reopen();
                return;
            }
            plugin.storage().saveTeam(team);
            Sounds.success(viewer);
            plugin.messages().send(viewer, "bank.withdraw-success",
                    "amount", amount, "balance", team.balance());
        }
        reopen();
    }

    private long parseAmount(String raw) {
        try {
            return Long.parseLong(raw.trim().replaceAll("[,.\\s]", ""));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void reopen() {
        Schedulers.entity(viewer, () -> new BankMenu(plugin, viewer).open());
    }
}
