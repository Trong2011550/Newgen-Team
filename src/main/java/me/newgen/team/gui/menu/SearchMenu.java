package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class SearchMenu extends Menu {

    private final NewGenTeamPlugin plugin;

    public SearchMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 36; }
    @Override protected String title() { return L("gui.search.title"); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) set(i, Icons.filler());

        set(10, ItemBuilder.of(Material.PLAYER_HEAD).head(viewer)
                .name(L("gui.search.find-player"))
                .lore(L("gui.search.find-player-lore"),
                        "",
                        L("gui.search.type-in-chat"))
                .glow(true).build(), e -> {
            Sounds.click(viewer);
            promptFor(true);
        });

        set(12, ItemBuilder.of(Material.ENDER_EYE)
                .name(L("gui.search.all-players"))
                .lore(L("gui.search.all-players-lore1"),
                        L("gui.search.all-players-lore2"),
                        "",
                        L("gui.search.open-list-hint"))
                .glow(true).build(), e -> {
            Sounds.click(viewer);
            new PlayerListMenu(plugin, viewer, 0).open();
        });

        set(14, ItemBuilder.of(Material.CYAN_BANNER)
                .name(L("gui.search.all-teams"))
                .lore(L("gui.search.all-teams-lore1"),
                        L("gui.search.all-teams-lore2"),
                        "",
                        L("gui.search.open-list-hint"))
                .glow(true).build(), e -> {
            Sounds.click(viewer);
            new TeamListMenu(plugin, viewer, 0).open();
        });

        set(16, ItemBuilder.of(Material.WHITE_BANNER)
                .name(L("gui.search.find-team"))
                .lore(L("gui.search.find-team-lore"),
                        "",
                        L("gui.search.type-in-chat"))
                .glow(true).build(), e -> {
            Sounds.click(viewer);
            promptFor(false);
        });

        set(31, Icons.back(), e -> { Sounds.click(viewer); plugin.menus().openMain(viewer); });
        set(35, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void promptFor(boolean players) {
        viewer.closeInventory();
        plugin.signInput().await(viewer, query -> {
            if (query == null) {
                plugin.messages().send(viewer, "search.cancelled");
                new SearchMenu(plugin, viewer).open();
                return;
            }
            plugin.messages().send(viewer, "search.searching", "query", query);
            if (players) {
                new SearchResultMenu(plugin, viewer, query, true, 0).open();
            } else {
                new SearchResultMenu(plugin, viewer, query, false, 0).open();
            }
        });
    }
}
