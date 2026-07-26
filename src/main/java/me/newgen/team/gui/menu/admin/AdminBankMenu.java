package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.Team;
import me.newgen.team.service.AdminService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class AdminBankMenu extends Menu {

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;

    public AdminBankMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId) {
        super(viewer);
        this.plugin = plugin;
        this.teamId = teamId;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 27; }
    @Override protected String title() { return L("gui.admin.bank.title"); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) {
            boolean edge = i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8;
            set(i, edge ? Icons.edgeFiller() : Icons.filler());
        }
        Team team = plugin.teams().byId(teamId);
        if (team == null) { closeMissing(); return; }

        set(4, ItemBuilder.of(Material.GOLD_BLOCK)
                .name(L("gui.main.bank"))
                .lore(L("gui.admin.bank.team-line", "team", team.name()),
                        L("gui.admin.bank.balance", "balance", team.balance()))
                .glow(true).build());

        set(11, ItemBuilder.of(Material.LIME_DYE)
                .name(L("gui.admin.bank.add"))
                .lore(L("gui.admin.bank.add-lore"), L("gui.common.type-amount")).build(),
                e -> { Sounds.click(viewer); prompt("add"); });
        set(13, ItemBuilder.of(Material.GOLD_INGOT)
                .name(L("gui.admin.bank.set"))
                .lore(L("gui.admin.bank.set-lore"), L("gui.common.type-amount")).build(),
                e -> { Sounds.click(viewer); prompt("set"); });
        set(15, ItemBuilder.of(Material.LIME_DYE)
                .name(L("gui.admin.bank.remove"))
                .lore(L("gui.admin.bank.remove-lore"), L("gui.common.type-amount")).build(),
                e -> { Sounds.click(viewer); prompt("remove"); });

        set(18, Icons.back(), e -> { Sounds.click(viewer); back(); });
        set(26, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void prompt(String mode) {
        viewer.closeInventory();
        plugin.messages().send(viewer, "admin.bank-prompt");
        plugin.signInput().await(viewer, in -> {
            Team team = plugin.teams().byId(teamId);
            if (team == null) { closeMissing(); return; }
            if (in == null || in.isBlank()) { reopen(); return; }
            long amount;
            try {
                amount = Long.parseLong(in.trim().replace(",", "").replace(" ", "").replace("_", ""));
            } catch (NumberFormatException ex) {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "admin.invalid-number");
                reopen();
                return;
            }
            if (amount == Long.MIN_VALUE) {
                // Math.abs(Long.MIN_VALUE) stays negative.
                Sounds.error(viewer);
                plugin.messages().send(viewer, "admin.invalid-number");
                reopen();
                return;
            }
            AdminService a = plugin.admin();
            UUID staff = viewer.getUniqueId();
            AdminService.AdminResult r = switch (mode) {
                case "add" -> a.adjustBank(team, staff, viewer.getName(), Math.abs(amount));
                case "remove" -> a.adjustBank(team, staff, viewer.getName(), -Math.abs(amount));
                default -> a.setBank(team, staff, viewer.getName(), amount);
            };
            report(r);
            reopen();
        });
    }

    private void report(AdminService.AdminResult r) {
        if (r.ok()) Sounds.success(viewer); else Sounds.error(viewer);
        viewer.sendMessage(me.newgen.team.util.Text.mini(plugin.messages().prefix())
                .append(me.newgen.team.util.Text.legacy((r.ok() ? "&#b7f0c0" : "&#ff5555") + r.message())));
    }

    private void back() { new AdminTeamDetailMenu(plugin, viewer, teamId).open(); }
    private void reopen() { new AdminBankMenu(plugin, viewer, teamId).open(); }
    private void closeMissing() {
        Sounds.error(viewer);
        plugin.messages().send(viewer, "admin.team-missing");
        viewer.closeInventory();
    }
}
