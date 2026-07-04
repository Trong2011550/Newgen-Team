package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.service.SearchManager;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class SearchResultMenu extends PaginatedMenu<Object> {

    private final NewGenTeamPlugin plugin;
    private final String query;
    private final boolean players;

    public SearchResultMenu(NewGenTeamPlugin plugin, Player viewer, String query,
                            boolean players, int page) {
        super(viewer, page);
        this.plugin = plugin;
        this.query = query;
        this.players = players;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override
    protected String title() {
        return players
                ? L("gui.search-result.title-players", "query", trim(query))
                : L("gui.search-result.title-teams", "query", trim(query));
    }

    @Override protected int pageSize() { return 45; }

    @Override
    protected List<Object> elements() {
        List<Object> out = new ArrayList<>();
        if (players) {
            out.addAll(plugin.search().searchPlayers(query));
        } else {
            out.addAll(plugin.search().searchTeams(query));
        }
        return out;
    }

    @Override
    protected ItemStack render(Object element) {
        if (element instanceof SearchManager.PlayerHit hit) {
            String teamLine = hit.team() == null
                    ? L("gui.common.no-team")
                    : L("gui.search-result.team-line", "team", hit.team().name());
            String status = L(hit.online() ? "gui.search-result.status-online" : "gui.search-result.status-offline");
            String state = L(hit.online() ? "gui.search-result.state-online" : "gui.search-result.state-offline");
            return ItemBuilder.of(Material.PLAYER_HEAD)
                    .head(Bukkit.getOfflinePlayer(hit.uuid()))
                    .name(L("gui.search-result.entry-player", "player", hit.name(), "status", status))
                    .lore(teamLine,
                            L("gui.search-result.status-line", "status", state),
                            "",
                            hit.team() == null ? L("gui.search-result.no-action") : L("gui.search-result.view-team-hint"))
                    .build();
        }
        Team team = (Team) element;
        return ItemBuilder.of(Material.WHITE_BANNER)
                .name(L("gui.search-result.entry-team", "team", team.name(), "tag", team.tag()))
                .lore(L("gui.common.members", "members", team.size()),
                        L("gui.common.chest-level", "level", team.chestLevel()),
                        L("gui.search-result.kills-kdr", "kills", team.kills(),
                                "kdr", String.format("%.2f", team.kdr())),
                        "",
                        L("gui.search-result.view-detail-hint"))
                .build();
    }

    @Override
    protected void onElementClick(Object element, InventoryClickEvent event) {
        Sounds.click(viewer);
        if (element instanceof SearchManager.PlayerHit hit) {
            if (hit.team() == null) {
                Sounds.error(viewer);
                return;
            }
            new TeamDetailMenu(plugin, viewer, hit.team().id(),
                    () -> new SearchResultMenu(plugin, viewer, query, true, page).open()).open();
            return;
        }
        Team team = (Team) element;
        new TeamDetailMenu(plugin, viewer, team.id(),
                () -> new SearchResultMenu(plugin, viewer, query, false, page).open()).open();
    }

    @Override
    protected void onBack() {
        new SearchMenu(plugin, viewer).open();
    }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
        List<Object> all = elements();
        if (all.isEmpty()) {
            set(22, ItemBuilder.of(Material.GRAY_DYE)
                    .name(L("gui.search-result.empty"))
                    .lore(L("gui.search-result.empty-lore", "query", trim(query)),
                            L("gui.search-result.empty-hint")).build());
        }
    }

    private static String trim(String s) {
        return s.length() > 24 ? s.substring(0, 24) : s;
    }
}
