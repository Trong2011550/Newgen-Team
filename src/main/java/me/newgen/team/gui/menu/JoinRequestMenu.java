package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.service.TeamService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JoinRequestMenu extends PaginatedMenu<UUID> {

    private final NewGenTeamPlugin plugin;

    public JoinRequestMenu(NewGenTeamPlugin plugin, Player viewer, int page) {
        super(viewer, page);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? L("gui.join-request.title") : L("gui.join-request.title-team", "team", t.name());
    }
    @Override protected int pageSize() { return 45; }

    @Override
    protected List<UUID> elements() {
        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return new ArrayList<>();
        return new ArrayList<>(plugin.teams().joinRequestsFor(team.id()).keySet());
    }

    @Override
    protected ItemStack render(UUID applicant) {
        String name = plugin.teams().joinRequestName(applicant);
        if (name == null) {
            String resolved = Bukkit.getOfflinePlayer(applicant).getName();
            name = resolved == null ? applicant.toString().substring(0, 8) : resolved;
        }
        boolean online = Bukkit.getPlayer(applicant) != null;
        String status = L(online ? "gui.join-request.status-online" : "gui.join-request.status-offline");
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(applicant))
                .name(L("gui.join-request.entry-name", "player", name, "status", status))
                .lore(L("gui.join-request.wants-join"),
                        "",
                        L("gui.join-request.left-accept"),
                        L("gui.join-request.right-decline"))
                .glow(true).build();
    }

    @Override
    protected void onElementClick(UUID applicant, InventoryClickEvent event) {
        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return;
        if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.REVIEW_JOIN_REQUESTS)) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        String name = plugin.teams().joinRequestName(applicant);
        if (name == null) {
            String resolved = Bukkit.getOfflinePlayer(applicant).getName();
            name = resolved == null ? "?" : resolved;
        }

        if (event.isRightClick()) {
            TeamService.Result r = plugin.teams().declineJoinRequest(team, applicant);
            if (r == TeamService.Result.SUCCESS) {
                Sounds.click(viewer);
                plugin.messages().send(viewer, "join.declined", "player", name);
            }
            refresh();
            viewer.openInventory(inventory);
            return;
        }

        TeamService.Result r = plugin.teams().acceptJoinRequest(team, applicant);
        switch (r) {
            case SUCCESS -> {
                Sounds.success(viewer);
                plugin.messages().send(viewer, "join.accepted", "player", name);
                Player target = Bukkit.getPlayer(applicant);
                if (target != null) plugin.messages().send(target, "join.you-accepted", "team", team.name());
                plugin.chat().notifyTeam(team, "notify.joined", "player", name);
            }
            case TEAM_FULL -> { Sounds.error(viewer); plugin.messages().send(viewer, "invite.team-full"); }
            case ALREADY_IN_TEAM -> { Sounds.error(viewer); plugin.messages().send(viewer, "invite.already-in-team"); }
            default -> { Sounds.error(viewer); plugin.messages().send(viewer, "general.error"); }
        }
        refresh();
        viewer.openInventory(inventory);
    }

    @Override
    protected void onBack() { plugin.menus().openMain(viewer); }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
        if (elements().isEmpty()) {
            set(22, ItemBuilder.of(Material.GRAY_DYE)
                    .name(L("gui.join-request.empty"))
                    .lore(L("gui.join-request.empty-lore1"),
                            L("gui.join-request.empty-lore2")).build());
        }
    }
}
