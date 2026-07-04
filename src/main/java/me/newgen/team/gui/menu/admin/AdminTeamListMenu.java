package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
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

public final class AdminTeamListMenu extends PaginatedMenu<Team> {

    private final NewGenTeamPlugin plugin;
    private final String query;

    public AdminTeamListMenu(NewGenTeamPlugin plugin, Player viewer, int page, String query) {
        super(viewer, page);
        this.plugin = plugin;
        this.query = query;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() {
        return query == null || query.isBlank()
                ? L("gui.admin.team-list.title")
                : L("gui.admin.team-list.title-query", "query", trim(query));
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
                .thenComparing(Team::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    protected ItemStack render(Team team) {
        return ItemBuilder.of(Material.WHITE_BANNER)
                .name(L("gui.admin.team-list.entry-name", "team", team.name(), "tag", team.tag()))
                .lore(L("gui.common.members-limit", "members", team.size(), "limit", plugin.teams().memberLimit(team)),
                        L("gui.common.team-tier", "tier", team.tier()),
                        L("gui.common.team-balance", "balance", team.balance()),
                        L("gui.admin.team-list.kdr-line", "kdr", String.format("%.2f", team.kdr())),
                        "",
                        L("gui.admin.team-list.manage-hint"))
                .build();
    }

    @Override
    protected void onElementClick(Team team, InventoryClickEvent event) {
        Sounds.click(viewer);
        new AdminTeamDetailMenu(plugin, viewer, team.id()).open();
    }

    @Override
    protected void onBack() {
        new AdminMainMenu(plugin, viewer).open();
    }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
        set(47, ItemBuilder.of(Material.OAK_SIGN)
                .name(L("gui.admin.team-list.search"))
                .lore(L("gui.admin.team-list.search-lore"),
                        query == null || query.isBlank()
                                ? L("gui.admin.team-list.showing-all")
                                : L("gui.admin.team-list.filtering", "query", trim(query)),
                        "",
                        L("gui.common.type-name"))
                .glow(true).build(),
                e -> { Sounds.click(viewer); promptSearch(); });
        if (query != null && !query.isBlank()) {
            set(51, ItemBuilder.of(Material.BARRIER)
                    .name(L("gui.admin.team-list.clear-filter"))
                    .lore(L("gui.admin.team-list.clear-filter-lore")).build(),
                    e -> { Sounds.click(viewer); new AdminTeamListMenu(plugin, viewer, 0, null).open(); });
        }
        if (elements().isEmpty()) {
            set(22, ItemBuilder.of(Material.GRAY_DYE)
                    .name(L("gui.admin.team-list.empty"))
                    .lore(L("gui.admin.team-list.empty-lore")).build());
        }
    }

    private void promptSearch() {
        viewer.closeInventory();
        plugin.messages().send(viewer, "search.prompt");
        plugin.signInput().await(viewer, q -> {
            if (q == null) {
                plugin.messages().send(viewer, "search.cancelled");
                new AdminTeamListMenu(plugin, viewer, 0, query).open();
                return;
            }
            new AdminTeamListMenu(plugin, viewer, 0, q).open();
        });
    }

    private static String trim(String s) {
        return s.length() > 24 ? s.substring(0, 24) : s;
    }
}
