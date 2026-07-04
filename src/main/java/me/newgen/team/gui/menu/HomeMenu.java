package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamHome;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.service.HomeService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HomeMenu extends PaginatedMenu<Map.Entry<String, TeamHome>> {

    private final NewGenTeamPlugin plugin;

    public HomeMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer, 0);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? L("gui.home.title") : L("gui.home.title-team", "team", t.name());
    }
    @Override protected int pageSize() { return 45; }

    @Override
    protected List<Map.Entry<String, TeamHome>> elements() {
        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return new ArrayList<>();
        return new ArrayList<>(team.namedHomes().entrySet());
    }

    @Override
    protected ItemStack render(Map.Entry<String, TeamHome> entry) {
        TeamHome h = entry.getValue();
        return ItemBuilder.of(Material.ENDER_CHEST)
                .name(L("gui.home.entry-name", "name", entry.getKey()))
                .lore(L("gui.home.world-line", "world", h.world),
                        L("gui.home.coords-line", "x", (int) h.x, "y", (int) h.y, "z", (int) h.z),
                        "",
                        L("gui.home.left-teleport"),
                        L("gui.home.right-delete"))
                .glow(true).build();
    }

    @Override
    protected void onElementClick(Map.Entry<String, TeamHome> entry, InventoryClickEvent event) {
        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return;

        if (event.isRightClick()) {
            if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.SET_HOME)) {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "general.no-permission");
                return;
            }
            if (plugin.homes().deleteNamedHome(team, entry.getKey())) {
                Sounds.click(viewer);
                plugin.messages().send(viewer, "home.named-deleted", "name", entry.getKey());
            }
            refresh();
            viewer.openInventory(inventory);
            return;
        }

        if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.TELEPORT_HOME)) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        viewer.closeInventory();
        HomeService.Result r = plugin.homes().teleportNamed(viewer, team, entry.getKey(),
                () -> plugin.messages().send(viewer, "home.warmup",
                        "seconds", plugin.config().homeTeleportWarmupSeconds()),
                () -> { Sounds.success(viewer); plugin.messages().send(viewer, "home.teleported"); },
                () -> plugin.messages().send(viewer, "home.cancelled"));
        switch (r) {
            case NO_HOME -> plugin.messages().send(viewer, "home.none");
            case ON_COOLDOWN -> plugin.messages().send(viewer, "home.cooldown",
                    "seconds", plugin.homes().cooldownRemaining(viewer.getUniqueId()) / 1000);
            case WORLD_MISSING -> plugin.messages().send(viewer, "home.world-missing");
            default -> {}
        }
    }

    @Override
    protected void onBack() { plugin.menus().openMain(viewer); }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());

        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return;
        int limit = plugin.tiers().homeLimit(team);
        int used = team.namedHomes().size();

        set(47, ItemBuilder.of(Material.LODESTONE)
                .name(L("gui.home.add-new"))
                .lore(L("gui.home.count", "used", used, "limit", limit),
                        L("gui.home.set-hint"))
                .glow(true).build(),
                e -> {
                    if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.SET_HOME)) {
                        Sounds.error(viewer);
                        plugin.messages().send(viewer, "general.no-permission");
                        return;
                    }
                    if (used >= limit) {
                        Sounds.error(viewer);
                        plugin.messages().send(viewer, "home.limit-reached");
                        return;
                    }
                    promptNewHome(team);
                });
    }

    private void promptNewHome(Team team) {
        viewer.closeInventory();
        plugin.messages().send(viewer, "home.prompt-name");
        plugin.signInput().await(viewer, name -> {
            if (name == null) {
                new HomeMenu(plugin, viewer).open();
                return;
            }
            int limit = plugin.tiers().homeLimit(team);
            HomeService.SetResult r = plugin.homes().setNamedHome(team, name, viewer.getLocation(), limit);
            switch (r) {
                case SUCCESS -> {
                    Sounds.success(viewer);
                    plugin.messages().send(viewer, "home.named-set", "name", name);
                }
                case LIMIT_REACHED -> { Sounds.error(viewer); plugin.messages().send(viewer, "home.limit-reached"); }
                case NAME_TAKEN -> { Sounds.error(viewer); plugin.messages().send(viewer, "home.name-taken"); }
                case INVALID_NAME -> { Sounds.error(viewer); plugin.messages().send(viewer, "home.name-invalid"); }
            }
            new HomeMenu(plugin, viewer).open();
        });
    }
}
