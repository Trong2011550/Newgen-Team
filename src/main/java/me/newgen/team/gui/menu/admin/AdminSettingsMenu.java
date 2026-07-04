package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamSettings;
import me.newgen.team.permission.AdminPermission;
import me.newgen.team.service.AdminService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class AdminSettingsMenu extends Menu {

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;

    public AdminSettingsMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId) {
        super(viewer);
        this.plugin = plugin;
        this.teamId = teamId;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 45; }
    @Override protected String title() { return L("gui.admin.settings.title"); }

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
        TeamSettings s = team.settings();
        boolean canEdit = AdminPermission.EDIT.held(viewer);

        toggle(11, "open", L("gui.settings.open-team"), s.open, canEdit, team);
        toggle(12, "friendlyFire", L("gui.settings.friendly-fire"), s.friendlyFire, canEdit, team);
        toggle(13, "teamChat", L("gui.settings.team-chat"), s.teamChat, canEdit, team);
        toggle(14, "autoAcceptInvite", L("gui.settings.auto-accept"), s.autoAcceptInvite, canEdit, team);
        toggle(15, "publicJoin", L("gui.settings.public-join"), s.publicJoin, canEdit, team);
        toggle(21, "notifyJoinLeave", L("gui.settings.join-notify"), s.notifyJoinLeave, canEdit, team);
        toggle(23, "allyRequests", L("gui.settings.ally-requests"), s.allyRequests, canEdit, team);

        set(40, Icons.back(), e -> { Sounds.click(viewer); back(); });
        set(44, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void toggle(int slot, String key, String label, boolean value, boolean canEdit, Team team) {
        set(slot, ItemBuilder.of(value ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(L("gui.settings.toggle-name", "name", label,
                        "state", L(value ? "gui.settings.state-on" : "gui.settings.state-off")))
                .lore(canEdit ? L("gui.settings.toggle-hint") : L("gui.common.view-only")).build(),
                canEdit ? e -> {
                    Sounds.click(viewer);
                    AdminService.AdminResult r = plugin.admin().toggleSetting(
                            team, viewer.getUniqueId(), viewer.getName(), key, !value);
                    if (!r.ok()) Sounds.error(viewer);
                    reopen();
                } : null);
    }

    private void back() { new AdminTeamDetailMenu(plugin, viewer, teamId).open(); }
    private void reopen() { new AdminSettingsMenu(plugin, viewer, teamId).open(); }
}
