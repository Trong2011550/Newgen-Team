package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.gui.menu.ConfirmMenu;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.model.TeamRole;
import me.newgen.team.permission.AdminPermission;
import me.newgen.team.service.AdminService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AdminMemberMenu extends PaginatedMenu<TeamMember> {

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;

    public AdminMemberMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId, int page) {
        super(viewer, page);
        this.plugin = plugin;
        this.teamId = teamId;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() { return L("gui.admin.member.title"); }
    @Override protected int pageSize() { return 45; }

    private boolean canEdit() { return AdminPermission.EDIT.held(viewer); }
    private AdminService admin() { return plugin.admin(); }

    @Override
    protected List<TeamMember> elements() {
        Team team = plugin.teams().byId(teamId);
        if (team == null) return List.of();
        List<TeamMember> list = new ArrayList<>(team.members());
        list.sort(Comparator.comparingInt((TeamMember m) -> m.role().weight()).reversed());
        return list;
    }

    @Override
    protected ItemStack render(TeamMember m) {
        Team team = plugin.teams().byId(teamId);
        boolean leader = team != null && team.leader().equals(m.uuid());
        boolean online = Bukkit.getPlayer(m.uuid()) != null;
        List<String> lore = new ArrayList<>();
        lore.add(L("gui.admin.member.role-line", "role", L("gui.role.names." + m.role().name())));
        lore.add(L("gui.admin.member.status-line", "status",
                L(online ? "gui.admin.member.state-online" : "gui.admin.member.state-offline")));
        if (leader) lore.add(L("gui.admin.member.leader-tag"));
        if (canEdit()) {
            lore.add("");
            lore.add(L("gui.admin.member.left-promote"));
            lore.add(L("gui.admin.member.right-demote"));
            lore.add(L("gui.admin.member.shift-left-leader"));
            lore.add(L("gui.admin.member.shift-right-kick"));
        }
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(m.uuid()))
                .name(L("gui.admin.member.entry-name", "player", safeName(m)))
                .lore(lore)
                .glow(leader)
                .build();
    }

    @Override
    protected void onElementClick(TeamMember m, InventoryClickEvent event) {
        if (!canEdit()) { Sounds.error(viewer); return; }
        Team team = plugin.teams().byId(teamId);
        if (team == null) { Sounds.error(viewer); return; }
        ClickType t = event.getClick();
        UUID staff = viewer.getUniqueId();
        if (t == ClickType.SHIFT_LEFT) {
            new ConfirmMenu(plugin, viewer, L("gui.admin.member.leader-confirm-title"),
                    L("gui.admin.member.leader-confirm-lore", "player", safeName(m)),
                    () -> { report(admin().forceLeader(team, staff, viewer.getName(), m.uuid())); reopen(); },
                    this::reopen).open();
            return;
        }
        if (t == ClickType.SHIFT_RIGHT) {
            new ConfirmMenu(plugin, viewer, L("gui.admin.member.kick-confirm-title"),
                    L("gui.admin.member.kick-confirm-lore", "player", safeName(m)),
                    () -> { report(admin().kick(team, staff, viewer.getName(), m.uuid())); reopen(); },
                    this::reopen).open();
            return;
        }
        TeamRole next = t.isRightClick() ? lower(m.role()) : higher(m.role());
        if (next == null || next == m.role()) { Sounds.error(viewer); return; }
        report(admin().setRole(team, staff, viewer.getName(), m.uuid(), next));
        reopen();
    }

    private TeamRole higher(TeamRole r) {
        return switch (r) {
            case RECRUIT -> TeamRole.MEMBER;
            case MEMBER -> TeamRole.MOD;
            case MOD -> TeamRole.ADMIN;
            case ADMIN -> TeamRole.CO_OWNER;
            case CO_OWNER, OWNER -> null;
        };
    }

    private TeamRole lower(TeamRole r) {
        return switch (r) {
            case CO_OWNER -> TeamRole.ADMIN;
            case ADMIN -> TeamRole.MOD;
            case MOD -> TeamRole.MEMBER;
            case MEMBER -> TeamRole.RECRUIT;
            case RECRUIT, OWNER -> null;
        };
    }

    @Override
    protected void onBack() { new AdminTeamDetailMenu(plugin, viewer, teamId).open(); }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
        if (canEdit()) {
            set(46, ItemBuilder.of(Material.LIME_DYE)
                    .name(L("gui.admin.member.add"))
                    .lore(L("gui.admin.member.add-lore"), L("gui.common.type-name")).build(),
                    e -> { Sounds.click(viewer); promptAdd(); });
            set(47, ItemBuilder.of(Material.BARRIER)
                    .name(L("gui.admin.member.clear"))
                    .lore(L("gui.admin.member.clear-lore"), L("gui.common.irreversible")).build(),
                    e -> { Sounds.click(viewer); confirmRemoveAll(); });
        }
    }

    private void promptAdd() {
        viewer.closeInventory();
        plugin.messages().send(viewer, "admin.add-member-prompt");
        plugin.signInput().await(viewer, in -> {
            Team team = plugin.teams().byId(teamId);
            if (team == null || in == null || in.isBlank()) { reopen(); return; }
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(in.trim());
            if (op == null) {
                Player online = Bukkit.getPlayerExact(in.trim());
                if (online != null) op = online;
            }
            if (op == null) {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "general.player-not-found");
                reopen();
                return;
            }
            report(admin().addMember(team, viewer.getUniqueId(), viewer.getName(),
                    op.getUniqueId(), op.getName() == null ? in.trim() : op.getName()));
            reopen();
        });
    }

    private void confirmRemoveAll() {
        Team team = plugin.teams().byId(teamId);
        if (team == null) return;
        new ConfirmMenu(plugin, viewer, L("gui.admin.member.clear-confirm-title"),
                L("gui.admin.member.clear-confirm-lore", "team", team.name()),
                () -> { report(admin().removeAllMembers(team, viewer.getUniqueId(), viewer.getName())); reopen(); },
                this::reopen).open();
    }

    private String safeName(TeamMember m) {
        return m.name() == null ? m.uuid().toString().substring(0, 8) : m.name();
    }

    private void report(AdminService.AdminResult r) {
        if (r.ok()) Sounds.success(viewer); else Sounds.error(viewer);
        viewer.sendMessage(me.newgen.team.util.Text.mini(plugin.messages().prefix())
                .append(me.newgen.team.util.Text.legacy((r.ok() ? "&#b7f0c0" : "&#ff5555") + r.message())));
    }

    private void reopen() { new AdminMemberMenu(plugin, viewer, teamId, page).open(); }
}
