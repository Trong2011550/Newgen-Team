package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.service.TeamService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TeamListMenu extends PaginatedMenu<Team> {

    private final NewGenTeamPlugin plugin;
    private final String query;

    public TeamListMenu(NewGenTeamPlugin plugin, Player viewer, int page) {
        this(plugin, viewer, page, null);
    }

    public TeamListMenu(NewGenTeamPlugin plugin, Player viewer, int page, String query) {
        super(viewer, page);
        this.plugin = plugin;
        this.query = query;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() {
        return query == null || query.isBlank()
                ? L("gui.team-list.title")
                : L("gui.team-list.title-query", "query", trim(query));
    }
    @Override protected int pageSize() { return 45; }

    @Override
    protected List<Team> elements() {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<Team> out = new ArrayList<>();
        for (Team t : plugin.teams().all()) {
            if (!q.isEmpty()
                    && !t.name().toLowerCase(Locale.ROOT).contains(q)
                    && !(t.tag() != null && t.tag().toLowerCase(Locale.ROOT).contains(q))) {
                continue;
            }
            out.add(t);
        }
        out.sort(Comparator
                .comparingInt((Team t) -> t.size()).reversed()
                .thenComparing(t -> t.name(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    protected ItemStack render(Team team) {
        boolean teamless = !plugin.teams().hasTeam(viewer.getUniqueId());
        String action = teamless
                ? L("gui.team-list.join-hint")
                : L("gui.team-list.view-hint");
        return ItemBuilder.of(Material.WHITE_BANNER)
                .name(L("gui.main.team-header", "team", team.name(), "tag", team.tag()))
                .lore(L("gui.common.members-limit", "members", team.size(), "limit", plugin.teams().memberLimit(team)),
                        L("gui.common.team-tier", "tier", team.tier()),
                        L("gui.team-list.kdr-line", "kdr", String.format("%.2f", team.kdr())),
                        "",
                        action)
                .build();
    }

    @Override
    protected void onElementClick(Team team, InventoryClickEvent event) {
        if (plugin.teams().hasTeam(viewer.getUniqueId())) {

            Sounds.click(viewer);
            new TeamDetailMenu(plugin, viewer, team.id(),
                    () -> new TeamListMenu(plugin, viewer, page, query).open()).open();
            return;
        }

        TeamService.Result r = plugin.teams().requestJoin(viewer.getUniqueId(), viewer.getName(), team);
        switch (r) {
            case SUCCESS -> {
                Sounds.success(viewer);
                plugin.messages().send(viewer, "join.requested", "team", team.name());
                notifyOfficers(team);
            }
            case ALREADY_REQUESTED -> { Sounds.error(viewer); plugin.messages().send(viewer, "join.already-requested"); }
            case ALREADY_IN_TEAM -> { Sounds.error(viewer); plugin.messages().send(viewer, "team.already-in"); }
            case TEAM_FULL -> { Sounds.error(viewer); plugin.messages().send(viewer, "invite.team-full"); }
            default -> { Sounds.error(viewer); plugin.messages().send(viewer, "general.error"); }
        }
    }

    private void notifyOfficers(Team team) {
        for (var m : team.members()) {
            Player online = org.bukkit.Bukkit.getPlayer(m.uuid());
            if (online == null) continue;
            if (plugin.permissions().has(team, m.uuid(),
                    me.newgen.team.permission.TeamPermission.REVIEW_JOIN_REQUESTS)) {
                plugin.messages().send(online, "join.officer-notify", "player", viewer.getName());
            }
        }
    }

    @Override
    protected void onBack() { plugin.menus().openMain(viewer); }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
        set(47, ItemBuilder.of(Material.OAK_SIGN)
                .name(L("gui.player-list.search"))
                .lore(L("gui.team-list.search-lore"),
                        query == null || query.isBlank()
                                ? L("gui.team-list.showing-all")
                                : L("gui.team-list.filtering", "query", trim(query)),
                        "",
                        L("gui.common.type-name"))
                .glow(true).build(),
                e -> { Sounds.click(viewer); promptSearch(); });
        if (query != null && !query.isBlank()) {
            set(51, ItemBuilder.of(Material.BARRIER)
                    .name(L("gui.team-list.clear-filter"))
                    .lore(L("gui.team-list.clear-filter-lore")).build(),
                    e -> { Sounds.click(viewer); new TeamListMenu(plugin, viewer, 0, null).open(); });
        }
        if (elements().isEmpty()) {
            set(22, ItemBuilder.of(Material.GRAY_DYE)
                    .name(L("gui.team-list.empty"))
                    .lore(L("gui.team-list.empty-lore")).build());
        }
    }

    private void promptSearch() {
        viewer.closeInventory();
        plugin.messages().send(viewer, "search.prompt");
        plugin.signInput().await(viewer, q -> {
            if (q == null) {
                plugin.messages().send(viewer, "search.cancelled");
                new TeamListMenu(plugin, viewer, 0, query).open();
                return;
            }
            new TeamListMenu(plugin, viewer, 0, q).open();
        });
    }

    private static String trim(String s) {
        return s.length() > 24 ? s.substring(0, 24) : s;
    }
}
