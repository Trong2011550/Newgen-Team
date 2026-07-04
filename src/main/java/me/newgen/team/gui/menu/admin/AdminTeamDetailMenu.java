package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.gui.menu.ConfirmMenu;
import me.newgen.team.model.Team;
import me.newgen.team.permission.AdminPermission;
import me.newgen.team.service.AdminService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public final class AdminTeamDetailMenu extends Menu {

    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;

    public AdminTeamDetailMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId) {
        super(viewer);
        this.plugin = plugin;
        this.teamId = teamId;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 54; }
    @Override protected String title() { return L("gui.admin.team-detail.title"); }

    private AdminService admin() { return plugin.admin(); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) {
            boolean edge = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            set(i, edge ? Icons.edgeFiller() : Icons.filler());
        }

        Team team = plugin.teams().byId(teamId);
        if (team == null) {
            set(22, ItemBuilder.of(Material.BARRIER)
                    .name(L("admin.team-missing"))
                    .lore(L("gui.admin.team-detail.missing-lore")).build());
            set(49, Icons.back(), e -> { Sounds.click(viewer); new AdminMainMenu(plugin, viewer).open(); });
            return;
        }

        String leaderName = Bukkit.getOfflinePlayer(team.leader()).getName();
        set(4, ItemBuilder.of(Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(team.leader()))
                .name(L("gui.admin.team-detail.header-name", "team", team.name(), "tag", team.tag()))
                .lore(L("gui.common.leader", "leader", leaderName == null ? "?" : leaderName),
                        L("gui.common.members-limit", "members", team.size(), "limit", plugin.teams().memberLimit(team)),
                        L("gui.common.team-tier", "tier", team.tier()),
                        L("gui.common.team-balance", "balance", team.balance()),
                        L("gui.main.kd", "kills", team.kills(), "deaths", team.deaths(),
                                "kdr", String.format("%.2f", team.kdr())),
                        L("gui.common.created", "date", DATE.format(new Date(team.createdAt()))))
                .glow(true).build());

        if (AdminPermission.VIEW.held(viewer)) {
            set(19, ItemBuilder.of(Material.PLAYER_HEAD).head(Bukkit.getOfflinePlayer(team.leader()))
                    .name(L("gui.admin.team-detail.members"))
                    .lore(L("gui.admin.team-detail.members-lore"), L("gui.common.click-open")).build(),
                    e -> { Sounds.click(viewer); new AdminMemberMenu(plugin, viewer, teamId, 0).open(); });
        }
        if (AdminPermission.EDIT.held(viewer)) {
            set(20, ItemBuilder.of(Material.NAME_TAG)
                    .name(L("gui.admin.team-detail.rename"))
                    .lore(L("gui.admin.team-detail.rename-lore", "name", team.name()),
                            L("gui.admin.team-detail.rename-hint")).build(),
                    e -> { Sounds.click(viewer); promptRename(team); });
            set(21, ItemBuilder.of(Material.OAK_SIGN)
                    .name(L("gui.admin.team-detail.tag"))
                    .lore(L("gui.admin.team-detail.tag-lore", "tag", team.tag()),
                            L("gui.admin.team-detail.tag-hint")).build(),
                    e -> { Sounds.click(viewer); promptTag(team); });
            set(22, ItemBuilder.of(Material.COMPARATOR)
                    .name(L("gui.admin.team-detail.settings"))
                    .lore(L("gui.admin.team-detail.settings-lore"), L("gui.common.click-open")).build(),
                    e -> { Sounds.click(viewer); new AdminSettingsMenu(plugin, viewer, teamId).open(); });
            set(23, ItemBuilder.of(Material.ANVIL)
                    .name(L("gui.admin.team-detail.tier"))
                    .lore(L("gui.admin.team-detail.tier-lore", "tier", team.tier()),
                            L("gui.admin.team-detail.tier-max-line", "max", plugin.config().tierConfig().maxLevel()),
                            L("gui.common.click-edit")).build(),
                    e -> { Sounds.click(viewer); new AdminTierMenu(plugin, viewer, teamId).open(); });
        }

        if (AdminPermission.BANK.held(viewer)) {
            set(28, ItemBuilder.of(Material.GOLD_BLOCK)
                    .name(L("gui.admin.team-detail.bank"))
                    .lore(L("gui.admin.team-detail.bank-lore", "balance", team.balance()),
                            L("gui.common.click-edit")).glow(true).build(),
                    e -> { Sounds.click(viewer); new AdminBankMenu(plugin, viewer, teamId).open(); });
        }
        if (AdminPermission.CHEST.held(viewer)) {
            set(29, ItemBuilder.of(Material.CHEST)
                    .name(L("gui.admin.team-detail.chest"))
                    .lore(L("gui.admin.team-detail.chest-lore1", "level", team.chestLevel()),
                            L("gui.admin.team-detail.chest-lore2", "slots", plugin.config().chestConfig().tier(team.chestLevel()).slots),
                            L("gui.admin.team-detail.chest-hint")).build(),
                    e -> { Sounds.click(viewer); new AdminChestMenu(plugin, viewer, teamId).open(); });
        }
        if (AdminPermission.HOME.held(viewer)) {
            set(30, ItemBuilder.of(Material.ENDER_CHEST)
                    .name(L("gui.main.homes"))
                    .lore(L("gui.admin.team-detail.homes-lore", "count", team.namedHomes().size()),
                            L("gui.common.click-manage")).build(),
                    e -> { Sounds.click(viewer); new AdminHomeMenu(plugin, viewer, teamId).open(); });
        }
        if (AdminPermission.RELATION.held(viewer)) {
            set(31, ItemBuilder.of(Material.EMERALD)
                    .name(L("gui.main.relations"))
                    .lore(L("gui.admin.team-detail.relations-lore"), L("gui.common.click-manage")).build(),
                    e -> { Sounds.click(viewer); new AdminRelationMenu(plugin, viewer, teamId, 0).open(); });
        }
        if (AdminPermission.LOGS.held(viewer)) {
            set(32, ItemBuilder.of(Material.WRITABLE_BOOK)
                    .name(L("gui.admin.team-detail.logs"))
                    .lore(L("gui.admin.team-detail.logs-lore"), L("gui.common.click-view")).glow(true).build(),
                    e -> { Sounds.click(viewer); new AdminLogsMenu(plugin, viewer, teamId, 0).open(); });
        }
        if (AdminPermission.EDIT.held(viewer)) {
            set(33, ItemBuilder.of(Material.BEACON)
                    .name(L("gui.admin.team-detail.transfer"))
                    .lore(L("gui.admin.team-detail.transfer-lore"), L("gui.common.click-select")).build(),
                    e -> { Sounds.click(viewer); new AdminMemberMenu(plugin, viewer, teamId, 0).open(); });
        }

        if (AdminPermission.DELETE.held(viewer)) {
            set(47, ItemBuilder.of(Material.LAVA_BUCKET)
                    .name(L("gui.admin.team-detail.reset-data"))
                    .lore(L("gui.admin.team-detail.reset-data-lore1"),
                            L("gui.admin.team-detail.reset-data-lore2"),
                            L("gui.common.irreversible")).build(),
                    e -> { Sounds.click(viewer); confirmResetData(team); });
            set(51, ItemBuilder.of(Material.TNT)
                    .name(L("gui.main.disband"))
                    .lore(L("gui.main.disband-lore"),
                            L("gui.common.irreversible")).build(),
                    e -> { Sounds.click(viewer); confirmDisband(team); });
        }

        set(49, Icons.back(), e -> { Sounds.click(viewer); new AdminTeamListMenu(plugin, viewer, 0, null).open(); });
        set(53, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void promptRename(Team team) {
        viewer.closeInventory();
        plugin.messages().send(viewer, "admin.rename-prompt");
        plugin.signInput().await(viewer, in -> {
            if (in == null || in.isBlank()) { reopen(); return; }
            report(admin().rename(team, viewer.getUniqueId(), viewer.getName(), in));
            reopen();
        });
    }

    private void promptTag(Team team) {
        viewer.closeInventory();
        plugin.messages().send(viewer, "admin.tag-prompt");
        plugin.signInput().await(viewer, in -> {
            if (in == null || in.isBlank()) { reopen(); return; }
            report(admin().changeTag(team, viewer.getUniqueId(), viewer.getName(), in));
            reopen();
        });
    }

    private void confirmResetData(Team team) {
        new ConfirmMenu(plugin, viewer, L("gui.admin.team-detail.reset-confirm-title"),
                L("gui.admin.team-detail.reset-confirm-lore", "team", team.name()),
                () -> {
                    report(admin().resetData(team, viewer.getUniqueId(), viewer.getName()));
                    reopen();
                },
                this::reopen).open();
    }

    private void confirmDisband(Team team) {
        new ConfirmMenu(plugin, viewer, L("gui.admin.team-detail.disband-confirm-title"),
                L("gui.admin.team-detail.disband-confirm-lore", "team", team.name()),
                () -> {
                    report(admin().disband(team, viewer.getUniqueId(), viewer.getName()));
                    new AdminTeamListMenu(plugin, viewer, 0, null).open();
                },
                this::reopen).open();
    }

    private void report(AdminService.AdminResult r) {
        if (r.ok()) Sounds.success(viewer); else Sounds.error(viewer);
        viewer.sendMessage(me.newgen.team.util.Text.legacy(plugin.messages().prefix()
                + (r.ok() ? "&#f7c6d9" : "&#f3a9c8") + r.message()));
    }

    private void reopen() {
        new AdminTeamDetailMenu(plugin, viewer, teamId).open();
    }
}
