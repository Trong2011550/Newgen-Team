package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.ChestTier;
import me.newgen.team.model.Team;
import me.newgen.team.service.StatsService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class StatsMenu extends Menu {

    private final NewGenTeamPlugin plugin;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd");

    public StatsMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 45; }
    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? L("gui.stats.title") : L("gui.stats.title-team", "team", t.name());
    }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) set(i, Icons.filler());

        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) { abort(); return; }

        ChestTier tier = plugin.config().chestConfig().tier(team.chestLevel());
        String leaderName = Bukkit.getOfflinePlayer(team.leader()).getName();
        int levelRank = plugin.stats().rank(team, StatsService.TopType.LEVEL);

        set(13, ItemBuilder.of(Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(team.leader()))
                .name(L("gui.main.team-header", "team", team.name(), "tag", team.tag()))
                .lore(L("gui.common.leader", "leader", leaderName == null ? "?" : leaderName),
                        L("gui.common.created", "date", DATE.format(new Date(team.createdAt()))))
                .glow(true).build());

        set(20, ItemBuilder.of(Material.PLAYER_HEAD).head(viewer)
                .name(L("gui.stats.members"))
                .lore(L("gui.stats.members-value", "members", team.size(),
                        "limit", plugin.config().maxMembers())).build());
        set(21, ItemBuilder.of(Material.IRON_SWORD)
                .name(L("gui.stats.kills"))
                .lore(L("gui.stats.value", "value", team.kills())).hideAll().build());
        set(22, ItemBuilder.of(Material.SKELETON_SKULL)
                .name(L("gui.stats.deaths"))
                .lore(L("gui.stats.value", "value", team.deaths())).build());
        set(23, ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(L("gui.stats.kdr"))
                .lore(L("gui.stats.value", "value", String.format("%.2f", team.kdr()))).hideAll().build());
        set(24, ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(L("gui.stats.team-level"))
                .lore(L("gui.stats.level-line", "level", team.tier()),
                        L("gui.stats.chest-slots-line", "slots", tier.slots)).glow(true).build());
        set(29, ItemBuilder.of(Material.GOLD_INGOT)
                .name(L("gui.stats.bank"))
                .lore(L("gui.stats.bank-lore", "balance", team.balance(),
                        "currency", plugin.points().currencyName())).build());
        set(30, ItemBuilder.of(Material.NETHER_STAR)
                .name(L("gui.stats.level-rank"))
                .lore(levelRank > 0 ? L("gui.stats.rank-line", "rank", levelRank)
                        : L("gui.stats.unranked")).build());

        set(40, Icons.back(), e -> { Sounds.click(viewer); plugin.menus().openMain(viewer); });
        set(44, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }
}
