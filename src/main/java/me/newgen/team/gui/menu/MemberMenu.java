package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MemberMenu extends PaginatedMenu<TeamMember> {

    private final NewGenTeamPlugin plugin;

    public MemberMenu(NewGenTeamPlugin plugin, Player viewer, int page) {
        super(viewer, page);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? L("gui.member.title") : L("gui.member.title-team", "team", t.name());
    }

    @Override
    protected List<TeamMember> elements() {
        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return List.of();
        List<TeamMember> list = new ArrayList<>(team.members());
        list.sort(Comparator.comparingInt((TeamMember m) -> m.role().weight()).reversed());
        return list;
    }

    @Override
    protected ItemStack render(TeamMember m) {
        boolean online = Bukkit.getPlayer(m.uuid()) != null;
        String status = L(online ? "gui.member.status-online" : "gui.member.status-offline");
        return ItemBuilder.of(org.bukkit.Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(m.uuid()))
                .name(L("gui.member.entry-name", "player", m.name(), "status", status))
                .lore(L("gui.member.role-line", "role", L("gui.role.names." + m.role().name())),
                        "",
                        L("gui.member.left-click"),
                        L("gui.member.shift-kick"))
                .build();
    }

    @Override
    protected void onElementClick(TeamMember m, InventoryClickEvent event) {
        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return;
        if (m.uuid().equals(viewer.getUniqueId())) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "member.cannot-target-self");
            return;
        }

        if (event.isShiftClick()) {
            if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.KICK_MEMBER)) {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "general.no-permission");
                return;
            }
            new ConfirmMenu(plugin, viewer, L("gui.member.kick-confirm-title"),
                    L("gui.member.kick-confirm-lore", "player", m.name()),
                    () -> {
                        var r = plugin.teams().kick(team, viewer.getUniqueId(), m.uuid());
                        if (r == me.newgen.team.service.TeamService.Result.SUCCESS) {
                            plugin.messages().send(viewer, "member.kicked", "player", m.name());
                            plugin.chat().clear(m.uuid());
                            plugin.chat().notifyTeam(team, "notify.kicked",
                                    "player", viewer.getName(), "target", m.name());
                        } else {
                            plugin.messages().send(viewer, "general.no-permission");
                        }
                        new MemberMenu(plugin, viewer, page).open();
                    },
                    () -> new MemberMenu(plugin, viewer, page).open()).open();
            return;
        }

        if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.MANAGE_ROLES)) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        new RoleMenu(plugin, viewer, m.uuid(), page).open();
    }

    @Override
    protected void onBack() {
        plugin.menus().openMain(viewer);
    }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
    }
}
