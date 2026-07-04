package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.permission.AdminPermission;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class AdminMainMenu extends Menu {

    private final NewGenTeamPlugin plugin;

    public AdminMainMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 45; }
    @Override protected String title() { return L("gui.admin.main.title"); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) {
            boolean edge = i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8;
            set(i, edge ? Icons.edgeFiller() : Icons.filler());
        }

        set(4, ItemBuilder.of(Material.NETHER_STAR)
                .name(L("gui.admin.main.panel"))
                .lore(L("gui.admin.main.total-teams", "count", plugin.teams().all().size()),
                        L("gui.admin.main.panel-lore"))
                .glow(true).build());

        if (AdminPermission.SEARCH.held(viewer) || AdminPermission.VIEW.held(viewer)) {
            set(20, ItemBuilder.of(Material.WHITE_BANNER)
                    .name(L("gui.admin.main.browse"))
                    .lore(L("gui.admin.main.browse-lore"),
                            "",
                            L("gui.common.click-open"))
                    .glow(true).build(),
                    e -> { Sounds.click(viewer); new AdminTeamListMenu(plugin, viewer, 0, null).open(); });
        }

        if (AdminPermission.SEARCH.held(viewer)) {
            set(22, ItemBuilder.of(Material.ENDER_EYE)
                    .name(L("gui.admin.main.find-player"))
                    .lore(L("gui.admin.main.find-player-lore"),
                            "",
                            L("gui.common.type-name"))
                    .glow(true).build(),
                    e -> { Sounds.click(viewer); promptPlayerSearch(); });
        }

        if (AdminPermission.VIEW.held(viewer)) {
            set(24, ItemBuilder.of(Material.GOLD_INGOT)
                    .name(L("gui.admin.main.leaderboard"))
                    .lore(L("gui.main.leaderboard-simple-lore"),
                            "",
                            L("gui.common.click-open"))
                    .build(),
                    e -> { Sounds.click(viewer); plugin.menus().openTop(viewer); });
        }

        set(40, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void promptPlayerSearch() {
        viewer.closeInventory();
        plugin.messages().send(viewer, "admin.search-player-prompt");
        plugin.signInput().await(viewer, q -> {
            if (q == null || q.isBlank()) {
                plugin.messages().send(viewer, "search.cancelled");
                new AdminMainMenu(plugin, viewer).open();
                return;
            }
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayerIfCached(q);
            java.util.UUID id = op == null ? null : op.getUniqueId();
            me.newgen.team.model.Team team = null;
            if (id != null) team = plugin.teams().byPlayer(id);
            if (team == null) {

                org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayerExact(q);
                if (online != null) team = plugin.teams().byPlayer(online.getUniqueId());
            }
            if (team == null) {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "admin.player-no-team", "player", q);
                new AdminMainMenu(plugin, viewer).open();
                return;
            }
            new AdminTeamDetailMenu(plugin, viewer, team.id()).open();
        });
    }
}
