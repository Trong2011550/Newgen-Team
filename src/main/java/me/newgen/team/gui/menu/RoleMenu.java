package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.model.TeamRole;
import me.newgen.team.service.TeamService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class RoleMenu extends Menu {

    private final NewGenTeamPlugin plugin;
    private final UUID target;
    private final int returnPage;

    public RoleMenu(NewGenTeamPlugin plugin, Player viewer, UUID target, int returnPage) {
        super(viewer);
        this.plugin = plugin;
        this.target = target;
        this.returnPage = returnPage;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 27; }
    @Override protected String title() { return L("gui.role.title"); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) set(i, Icons.filler());

        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) { viewer.closeInventory(); return; }
        TeamMember tm = team.member(target);
        if (tm == null) { plugin.menus().openMembers(viewer); return; }

        set(4, ItemBuilder.of(Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(target))
                .name(L("gui.role.target-name", "player", tm.name()))
                .lore(L("gui.role.current-role", "role", L("gui.role.names." + tm.role().name()))).build());

        TeamRole actorRole = team.leader().equals(viewer.getUniqueId())
                ? TeamRole.OWNER : team.member(viewer.getUniqueId()).role();

        int[] slots = {10, 11, 12, 13, 14};
        TeamRole[] assignable = {TeamRole.CO_OWNER, TeamRole.ADMIN, TeamRole.MOD,
                TeamRole.MEMBER, TeamRole.RECRUIT};
        for (int i = 0; i < assignable.length; i++) {
            TeamRole role = assignable[i];
            boolean canAssign = actorRole.canManage(role) && actorRole.canManage(tm.role());
            set(slots[i], ItemBuilder.of(canAssign ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(L("gui.role.names." + role.name()))
                    .lore(canAssign ? L("gui.role.assign-hint") : L("gui.role.assign-denied"))
                    .build(), e -> {
                if (!canAssign) { Sounds.error(viewer); return; }
                TeamService.Result r = plugin.teams().setRole(team, viewer.getUniqueId(), target, role);
                if (r == TeamService.Result.SUCCESS) {
                    Sounds.success(viewer);
                    plugin.messages().send(viewer, "member.role-set",
                            "player", tm.name(), "role", role.name());
                    plugin.chat().notifyTeam(team, "notify.role-set",
                            "player", viewer.getName(), "target", tm.name(), "role", L("gui.role.names." + role.name()));
                } else {
                    Sounds.error(viewer);
                    plugin.messages().send(viewer, "general.no-permission");
                }
                new MemberMenu(plugin, viewer, returnPage).open();
            });
        }

        if (team.leader().equals(viewer.getUniqueId())) {
            set(22, ItemBuilder.of(Material.NETHER_STAR)
                    .name(L("gui.role.transfer"))
                    .lore(L("gui.role.transfer-target", "player", tm.name()),
                            L("gui.role.transfer-lore")).glow(true).build(),
                    e -> {
                        Sounds.click(viewer);
                        new ConfirmMenu(plugin, viewer, L("gui.role.transfer-confirm-title"),
                                L("gui.role.transfer-confirm-lore", "player", tm.name()),
                                () -> {
                                    TeamService.Result r = plugin.teams()
                                            .transferLeader(team, viewer.getUniqueId(), target);
                                    if (r == TeamService.Result.SUCCESS) {
                                        Sounds.success(viewer);
                                        plugin.messages().send(viewer, "member.transferred",
                                                "player", tm.name());
                                        plugin.chat().notifyTeam(team, "notify.transferred",
                                                "player", viewer.getName(), "target", tm.name());
                                    } else {
                                        plugin.messages().send(viewer, "general.no-permission");
                                    }
                                    plugin.menus().openMembers(viewer);
                                },
                                () -> new RoleMenu(plugin, viewer, target, returnPage).open()).open();
                    });
        }

        set(18, Icons.back(), e -> { Sounds.click(viewer); plugin.menus().openMain(viewer); });
        set(26, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }
}
