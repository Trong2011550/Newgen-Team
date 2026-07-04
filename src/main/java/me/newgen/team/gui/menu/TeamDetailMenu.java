package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.ChestTier;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class TeamDetailMenu extends Menu {

    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy");

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;
    private final Runnable onBack;

    public TeamDetailMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId, Runnable onBack) {
        super(viewer);
        this.plugin = plugin;
        this.teamId = teamId;
        this.onBack = onBack;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 54; }
    @Override protected String title() { return L("gui.team-detail.title"); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) set(i, Icons.filler());

        Team team = plugin.teams().byId(teamId);
        if (team == null) {
            set(22, ItemBuilder.of(Material.BARRIER)
                    .name(L("gui.team-detail.missing"))
                    .lore(L("gui.team-detail.missing-lore")).build());
            set(49, Icons.back(), e -> { Sounds.click(viewer); back(); });
            return;
        }

        ChestTier tier = plugin.config().chestConfig().tier(team.chestLevel());
        String leaderName = Bukkit.getOfflinePlayer(team.leader()).getName();

        set(4, ItemBuilder.of(Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(team.leader()))
                .name(L("gui.main.team-header", "team", team.name(), "tag", team.tag()))
                .lore(L("gui.common.leader", "leader", leaderName == null ? "?" : leaderName),
                        L("gui.common.created", "date", DATE.format(new Date(team.createdAt()))))
                .glow(true).build());

        set(19, ItemBuilder.of(Material.PLAYER_HEAD).head(Bukkit.getOfflinePlayer(team.leader()))
                .name(L("gui.main.members"))
                .lore(L("gui.common.members-limit", "members", team.size(), "limit", plugin.teams().memberLimit(team))).build());
        set(20, ItemBuilder.of(Material.IRON_SWORD)
                .name(L("gui.team-detail.kills"))
                .lore(L("gui.team-detail.value", "value", team.kills())).hideAll().build());
        set(21, ItemBuilder.of(Material.SKELETON_SKULL)
                .name(L("gui.team-detail.deaths"))
                .lore(L("gui.team-detail.value", "value", team.deaths())).build());
        set(22, ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(L("gui.team-detail.kdr"))
                .lore(L("gui.team-detail.value", "value", String.format("%.2f", team.kdr()))).hideAll().build());
        set(23, ItemBuilder.of(Material.CHEST)
                .name(L("gui.main.chest")).lore(L("gui.common.team-tier", "tier", team.tier()),
                        L("gui.team-detail.chest-slots-line", "slots", tier.slots)).build());
        set(24, ItemBuilder.of(Material.GOLD_INGOT)
                .name(L("gui.team-detail.balance"))
                .lore(L("gui.common.team-balance", "balance", team.balance())).build());

        List<TeamMember> sorted = new ArrayList<>(team.members());
        sorted.sort(Comparator.comparingInt((TeamMember m) -> m.role().weight()).reversed());
        int slot = 28;
        for (TeamMember m : sorted) {
            if (slot > 43) break;
            boolean online = Bukkit.getPlayer(m.uuid()) != null;
            String status = L(online ? "gui.team-detail.status-online" : "gui.team-detail.status-offline");
            set(slot++, ItemBuilder.of(Material.PLAYER_HEAD)
                    .head(Bukkit.getOfflinePlayer(m.uuid()))
                    .name(L("gui.team-detail.member-name", "player", m.name(), "status", status))
                    .lore(L("gui.team-detail.member-role-line", "role", L("gui.role.names." + m.role().name()))).build());
        }

        if (!plugin.teams().hasTeam(viewer.getUniqueId())) {
            set(48, ItemBuilder.of(Material.LIME_DYE)
                    .name(L("gui.team-detail.request-join"))
                    .lore(L("gui.team-detail.request-join-lore1"),
                            L("gui.team-detail.request-join-lore2"),
                            "",
                            L("gui.team-detail.request-join-hint"))
                    .glow(true).build(),
                    e -> requestJoin(team));
        }

        set(49, Icons.back(), e -> { Sounds.click(viewer); back(); });
        set(53, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void requestJoin(Team team) {
        me.newgen.team.service.TeamService.Result r =
                plugin.teams().requestJoin(viewer.getUniqueId(), viewer.getName(), team);
        switch (r) {
            case SUCCESS -> {
                Sounds.success(viewer);
                plugin.messages().send(viewer, "join.requested", "team", team.name());
                for (var m : team.members()) {
                    Player online = Bukkit.getPlayer(m.uuid());
                    if (online != null && plugin.permissions().has(team, m.uuid(),
                            me.newgen.team.permission.TeamPermission.REVIEW_JOIN_REQUESTS)) {
                        plugin.messages().send(online, "join.officer-notify", "player", viewer.getName());
                    }
                }
            }
            case ALREADY_REQUESTED -> { Sounds.error(viewer); plugin.messages().send(viewer, "join.already-requested"); }
            case ALREADY_IN_TEAM -> { Sounds.error(viewer); plugin.messages().send(viewer, "team.already-in"); }
            case TEAM_FULL -> { Sounds.error(viewer); plugin.messages().send(viewer, "invite.team-full"); }
            default -> { Sounds.error(viewer); plugin.messages().send(viewer, "general.error"); }
        }
    }

    private void back() {

        plugin.menus().openMain(viewer);
    }
}
