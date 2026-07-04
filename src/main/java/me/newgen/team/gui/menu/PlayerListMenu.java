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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PlayerListMenu extends PaginatedMenu<OfflinePlayer> {

    private final NewGenTeamPlugin plugin;
    private final String query;

    public PlayerListMenu(NewGenTeamPlugin plugin, Player viewer, int page) {
        this(plugin, viewer, page, null);
    }

    public PlayerListMenu(NewGenTeamPlugin plugin, Player viewer, int page, String query) {
        super(viewer, page);
        this.plugin = plugin;
        this.query = query;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() {
        return query == null || query.isBlank()
                ? L("gui.player-list.title")
                : L("gui.player-list.title-query", "query", trim(query));
    }
    @Override protected int pageSize() { return 45; }

    @Override
    protected List<OfflinePlayer> elements() {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<OfflinePlayer> out = new ArrayList<>();
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.getName() == null) continue;
            if (!q.isEmpty() && !p.getName().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(p);
        }
        out.sort(Comparator
                .comparing((OfflinePlayer p) -> !p.isOnline())
                .thenComparing(p -> p.getName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    protected ItemStack render(OfflinePlayer p) {
        Team team = plugin.teams().byPlayer(p.getUniqueId());
        boolean online = p.isOnline();
        String teamLine = team == null
                ? L("gui.common.no-team")
                : L("gui.player-list.team-line", "team", team.name());
        String action = team == null
                ? L("gui.player-list.invite-hint")
                : L("gui.player-list.view-hint");
        String status = L(online ? "gui.player-list.status-online" : "gui.player-list.status-offline");
        String state = L(online ? "gui.player-list.state-online" : "gui.player-list.state-offline");
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .head(p)
                .name(L("gui.player-list.entry-name", "player", p.getName(), "status", status))
                .lore(teamLine,
                        L("gui.player-list.status-line", "status", state),
                        "",
                        action)
                .build();
    }

    @Override
    protected void onElementClick(OfflinePlayer p, InventoryClickEvent event) {
        Team targetTeam = plugin.teams().byPlayer(p.getUniqueId());
        if (targetTeam != null) {

            Sounds.click(viewer);
            new TeamDetailMenu(plugin, viewer, targetTeam.id(),
                    () -> new PlayerListMenu(plugin, viewer, page, query).open()).open();
            return;
        }

        Team myTeam = plugin.teams().byPlayer(viewer.getUniqueId());
        if (myTeam == null) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "team.not-in");
            return;
        }
        if (!plugin.permissions().has(myTeam, viewer.getUniqueId(), TeamPermission.INVITE_MEMBER)) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        TeamService.Result r = plugin.teams().invite(myTeam, p.getUniqueId());
        switch (r) {
            case SUCCESS -> {
                Sounds.success(viewer);
                plugin.messages().send(viewer, "invite.sent", "player", p.getName());
                Player online = Bukkit.getPlayer(p.getUniqueId());
                if (online != null) plugin.messages().send(online, "invite.received", "team", myTeam.name());
                plugin.chat().notifyTeam(myTeam, "notify.invited",
                        "player", viewer.getName(), "target", p.getName());
            }
            case TEAM_FULL -> { Sounds.error(viewer); plugin.messages().send(viewer, "invite.team-full"); }
            case TARGET_IN_TEAM -> { Sounds.error(viewer); plugin.messages().send(viewer, "invite.already-in-team"); }
            default -> { Sounds.error(viewer); plugin.messages().send(viewer, "general.error"); }
        }
    }

    @Override
    protected void onBack() { plugin.menus().openMain(viewer); }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());

        set(47, ItemBuilder.of(Material.OAK_SIGN)
                .name(L("gui.player-list.search"))
                .lore(L("gui.player-list.search-lore"),
                        query == null || query.isBlank()
                                ? L("gui.player-list.showing-all")
                                : L("gui.player-list.filtering", "query", trim(query)),
                        "",
                        L("gui.common.type-name"))
                .glow(true).build(),
                e -> {
                    Sounds.click(viewer);
                    promptSearch();
                });

        if (query != null && !query.isBlank()) {
            set(51, ItemBuilder.of(Material.BARRIER)
                    .name(L("gui.player-list.clear-filter"))
                    .lore(L("gui.player-list.clear-filter-lore")).build(),
                    e -> { Sounds.click(viewer); new PlayerListMenu(plugin, viewer, 0, null).open(); });
        }
    }

    private void promptSearch() {
        viewer.closeInventory();
        plugin.messages().send(viewer, "search.prompt");
        plugin.signInput().await(viewer, q -> {
            if (q == null) {
                plugin.messages().send(viewer, "search.cancelled");
                new PlayerListMenu(plugin, viewer, 0, query).open();
                return;
            }
            new PlayerListMenu(plugin, viewer, 0, q).open();
        });
    }

    private static String trim(String s) {
        return s.length() > 24 ? s.substring(0, 24) : s;
    }
}
