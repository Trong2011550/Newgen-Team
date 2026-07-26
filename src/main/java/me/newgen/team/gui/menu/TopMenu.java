package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.service.StatsService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class TopMenu extends PaginatedMenu<Team> {

    private final NewGenTeamPlugin plugin;

    public TopMenu(Player viewer, NewGenTeamPlugin plugin, StatsService.TopType type, int page) {
        super(viewer, page);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() { return L("gui.top.title"); }
    @Override protected int pageSize() { return 36; }

    /** Leaderboard cached per refresh; computed once per build, not per rendered item. */
    private List<Team> cachedTop;

    @Override
    public void refresh() {
        cachedTop = null;
        super.refresh();
    }

    @Override
    protected List<Team> elements() {
        if (cachedTop == null) {
            cachedTop = plugin.stats().top(StatsService.TopType.KDR, 1000);
        }
        return cachedTop;
    }

    @Override
    protected ItemStack render(Team team) {
        List<Team> all = elements();
        int rank = all.indexOf(team) + 1;
        Material icon = switch (rank) {
            case 1 -> Material.NETHER_STAR;
            case 2 -> Material.DIAMOND;
            case 3 -> Material.GOLD_INGOT;
            default -> Material.PAPER;
        };
        ItemBuilder builder = ItemBuilder.of(icon);
        if (rank == 1) {

            builder.name(me.newgen.team.util.Text.legacy("&8#1 ")
                    .append(me.newgen.team.util.Text.galaxy(team.name()))
                    .append(me.newgen.team.util.Text.legacy(" &8[&#d9f7e0" + team.tag() + "&8]")));
        } else {
            builder.name(L("gui.top.entry", "rank", rank, "team", team.name(), "tag", team.tag()));
        }
        return builder
                .lore(L("gui.common.team-tier", "tier", team.tier()),
                        L("gui.common.members", "members", team.size()),
                        L("gui.top.kills-deaths", "kills", team.kills(), "deaths", team.deaths()),
                        L("gui.common.team-balance", "balance", team.balance()),
                        "",
                        L("gui.top.kdr-line", "kdr", String.format("%.2f", team.kdr())))
                .glow(rank <= 3).build();
    }

    @Override
    protected void onElementClick(Team team, InventoryClickEvent event) {
        Sounds.click(viewer);
    }

    @Override
    protected void onBack() { plugin.menus().openMain(viewer); }

    @Override
    protected void decorate() {
        for (int i = 36; i < 54; i++) set(i, Icons.edgeFiller());
        set(45, ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(L("gui.top.kdr-button"))
                .lore(L("gui.top.kdr-button-lore1"),
                        L("gui.top.kdr-button-lore2")).glow(true).hideAll().build());
    }
}
