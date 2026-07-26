package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.gui.menu.ConfirmMenu;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamHome;
import me.newgen.team.permission.AdminPermission;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class AdminHomeMenu extends Menu {

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;

    public AdminHomeMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId) {
        super(viewer);
        this.plugin = plugin;
        this.teamId = teamId;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 45; }
    @Override protected String title() { return L("gui.admin.home.title"); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) {
            boolean edge = i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8;
            set(i, edge ? Icons.edgeFiller() : Icons.filler());
        }
        Team team = plugin.teams().byId(teamId);
        if (team == null) {
            set(22, ItemBuilder.of(Material.BARRIER).name(L("admin.team-missing")).build());
            set(40, Icons.back(), e -> { Sounds.click(viewer); back(); });
            return;
        }

        int slot = 10;
        if (team.namedHomes().isEmpty()) {
            set(13, ItemBuilder.of(Material.GRAY_DYE)
                    .name(L("gui.admin.home.no-home"))
                    .lore(L("gui.admin.home.no-home-lore")).build());
        } else {
            for (Map.Entry<String, TeamHome> e : team.namedHomes().entrySet()) {
                if (slot > 34) break;
                if (slot % 9 == 8) slot += 2;
                TeamHome h = e.getValue();
                set(slot++, ItemBuilder.of(Material.RED_BED)
                        .name(L("gui.admin.home.entry-name", "name", e.getKey()))
                        .lore(L("gui.admin.home.world-line", "world", h.world),
                                L("gui.admin.home.coords-line", "x", (int) h.x, "y", (int) h.y, "z", (int) h.z)).build());
            }
        }

        if (AdminPermission.HOME.held(viewer)) {
            set(38, ItemBuilder.of(Material.LIME_DYE)
                    .name(L("gui.admin.home.set-here"))
                    .lore(L("gui.admin.home.set-lore"), L("gui.common.click-set")).build(),
                    e -> {
                        Sounds.click(viewer);
                        var r = plugin.admin().setHome(team, viewer.getUniqueId(), viewer.getName(), viewer.getLocation());
                        report(r);
                        reopen();
                    });
            set(42, ItemBuilder.of(Material.BARRIER)
                    .name(L("gui.admin.home.clear-all"))
                    .lore(L("gui.admin.home.clear-all-lore"), L("gui.common.irreversible")).build(),
                    e -> { Sounds.click(viewer); confirmClear(team); });
        }

        set(40, Icons.back(), e -> { Sounds.click(viewer); back(); });
    }

    private void confirmClear(Team team) {
        new ConfirmMenu(plugin, viewer, L("gui.admin.home.clear-confirm-title"),
                L("gui.admin.home.clear-confirm-lore", "team", team.name()),
                () -> { report(plugin.admin().clearHome(team, viewer.getUniqueId(), viewer.getName())); reopen(); },
                this::reopen).open();
    }

    private void report(me.newgen.team.service.AdminService.AdminResult r) {
        if (r.ok()) Sounds.success(viewer); else Sounds.error(viewer);
        viewer.sendMessage(me.newgen.team.util.Text.mini(plugin.messages().prefix())
                .append(me.newgen.team.util.Text.legacy((r.ok() ? "&#b7f0c0" : "&#ff5555") + r.message())));
    }

    private void back() { new AdminTeamDetailMenu(plugin, viewer, teamId).open(); }
    private void reopen() { new AdminHomeMenu(plugin, viewer, teamId).open(); }
}
