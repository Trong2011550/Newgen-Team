package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.gui.MenuLayoutManager;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public final class MainMenu extends Menu {

    private final NewGenTeamPlugin plugin;

    public MainMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    private MenuLayoutManager.MenuLayout layout() {
        return plugin.menuLayouts().layout("main");
    }

    /** Places a layout-aware item: slot/material/glow/model-data come from menus/main.yml. */
    private void put(String key, int defSlot, Material defMat, boolean defGlow,
                     java.util.function.Function<ItemBuilder, ItemBuilder> decorate,
                     Consumer<InventoryClickEvent> onClick) {
        MenuLayoutManager.MenuLayout l = layout();
        if (!l.enabled(key)) return;
        ItemBuilder b = ItemBuilder.of(l.material(key, defMat));
        b = decorate.apply(b);
        b.glow(l.glow(key, defGlow));
        int model = l.modelData(key, 0);
        if (model > 0) b.modelData(model);
        set(l.slot(key, defSlot), b.build(), onClick);
    }

    @Override protected int size() { return layout().size(54); }

    @Override
    protected String title() {
        return layout().title(L("gui.main.title"));
    }

    @Override
    protected void build() {

        for (int i = 0; i < size(); i++) {
            boolean edge = i < 9 || i >= size() - 9 || i % 9 == 0 || i % 9 == 8;
            set(i, edge ? Icons.edgeFiller() : Icons.filler());
        }

        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) {
            buildNoTeam();
            return;
        }
        buildWithTeam(team);
    }

    private void buildNoTeam() {

        put("create", 29, Material.NETHER_STAR, true,
                b -> b.name(L("gui.main.create"))
                        .lore(L("gui.main.create-lore1"),
                                L("gui.main.create-lore-desc1"),
                                L("gui.main.create-lore-desc2"),
                                "",
                                L("gui.main.create-lore2")),
                e -> {
                    Sounds.click(viewer);
                    new me.newgen.team.gui.menu.CreateTeamMenu(plugin, viewer).open();
                });

        put("my-invites", 31, Material.NAME_TAG, true,
                b -> b.name(L("gui.main.my-invites"))
                        .lore(L("gui.main.my-invites-lore1"),
                                L("gui.main.my-invites-lore2"),
                                "",
                                L("gui.common.click-open")),
                e -> {
                    Sounds.click(viewer);
                    plugin.menus().openMyInvites(viewer);
                });

        put("players", 33, Material.ENDER_EYE, true,
                b -> b.name(L("gui.main.players"))
                        .lore(L("gui.main.players-lore"),
                                "",
                                L("gui.common.click-open")),
                e -> {
                    Sounds.click(viewer);
                    plugin.menus().openPlayers(viewer);
                });

        put("browse-teams", 47, Material.WHITE_BANNER, true,
                b -> b.name(L("gui.main.browse-teams"))
                        .lore(L("gui.main.browse-teams-join-lore"),
                                "",
                                L("gui.common.click-open")),
                e -> {
                    Sounds.click(viewer);
                    plugin.menus().openTeams(viewer);
                });

        put("leaderboard", 49, Material.BOOK, false,
                b -> b.name(L("gui.main.leaderboard"))
                        .lore(L("gui.main.leaderboard-simple-lore")),
                e -> {
                    Sounds.click(viewer);
                    plugin.menus().openTop(viewer);
                });

        set(layout().slot("close", 53), Icons.close(), e -> {
            Sounds.click(viewer);
            viewer.closeInventory();
        });
    }

    private void buildWithTeam(Team team) {
        TeamMember self = team.member(viewer.getUniqueId());
        String role = self == null ? "-" : L("gui.role.names." + self.role().name());

        put("team-info", 4, Material.PLAYER_HEAD, true,
                b -> b.head(viewer)
                        .name(L("gui.main.team-header", "team", team.name(), "tag", team.tag()))
                        .lore(L("gui.common.members-limit", "members", team.size(), "limit", plugin.teams().memberLimit(team)),
                                L("gui.main.your-role", "role", role),
                                L("gui.common.team-tier", "tier", team.tier()),
                                L("gui.common.team-balance", "balance", team.balance()),
                                "",
                                L("gui.main.kd", "kills", team.kills(), "deaths", team.deaths(),
                                        "kdr", String.format("%.2f", team.kdr()))),
                null);

        put("members", 19, Material.PLAYER_HEAD, false,
                b -> b.head(viewer)
                        .name(L("gui.main.members"))
                        .lore(L("gui.main.members-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openMembers(viewer); });

        put("invites", 22, Material.NAME_TAG, false,
                b -> b.name(L("gui.main.invites"))
                        .lore(L("gui.main.invites-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openInvites(viewer); });

        put("permissions", 25, Material.WRITABLE_BOOK, false,
                b -> b.name(L("gui.main.permissions"))
                        .lore(L("gui.main.permissions-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openPermissions(viewer); });

        put("chest", 28, Material.CHEST, true,
                b -> b.name(L("gui.main.chest"))
                        .lore(L("gui.main.chest-lore", "level", team.chestLevel()),
                                L("gui.common.click-open")),
                e -> { Sounds.click(viewer); openChest(team); });

        put("bank", 31, Material.GOLD_BLOCK, true,
                b -> b.name(L("gui.main.bank"))
                        .lore(L("gui.common.team-balance", "balance", team.balance()),
                                L("gui.main.bank-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openBank(viewer); });

        put("upgrade", 34, Material.ANVIL, false,
                b -> b.name(L("gui.main.upgrade"))
                        .lore(L("gui.main.upgrade-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openUpgrades(viewer); });

        put("homes", 37, Material.ENDER_CHEST, false,
                b -> b.name(L("gui.main.homes"))
                        .lore(L("gui.main.homes-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openHome(viewer); });

        put("settings", 40, Material.COMPARATOR, false,
                b -> b.name(L("gui.main.settings"))
                        .lore(L("gui.main.settings-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openSettings(viewer); });

        put("stats", 43, Material.DIAMOND, false,
                b -> b.name(L("gui.main.stats"))
                        .lore(L("gui.main.stats-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openStats(viewer); });

        boolean isLeader = team.leader().equals(viewer.getUniqueId());
        if (isLeader) {
            put("disband", 45, Material.TNT, false,
                    b -> b.name(L("gui.main.disband"))
                            .lore(L("gui.main.disband-lore"), L("gui.common.irreversible")),
                    e -> { Sounds.click(viewer);
                        new ConfirmMenu(plugin, viewer, L("gui.main.disband-confirm-title"),
                                L("gui.main.disband-confirm-lore", "team", team.name()),
                                () -> {
                                    plugin.chat().notifyTeam(team, "notify.disbanded", "player", viewer.getName());
                                    plugin.teams().disbandTeam(team);
                                    plugin.messages().send(viewer, "team.disbanded");
                                    viewer.closeInventory();
                                },
                                () -> plugin.menus().openMain(viewer)).open();
                    });
        } else {
            put("leave", 45, Material.OAK_DOOR, false,
                    b -> b.name(L("gui.main.leave"))
                            .lore(L("gui.main.leave-lore", "team", team.name())),
                    e -> { Sounds.click(viewer);
                        new ConfirmMenu(plugin, viewer, L("gui.main.leave-confirm-title"),
                                L("gui.main.leave-lore", "team", team.name()),
                                () -> {
                                    plugin.teams().leave(team, viewer.getUniqueId());
                                    plugin.chat().clear(viewer.getUniqueId());
                                    plugin.messages().send(viewer, "team.left");
                                    plugin.chat().notifyTeam(team, "notify.left", "player", viewer.getName());
                                    viewer.closeInventory();
                                },
                                () -> plugin.menus().openMain(viewer)).open();
                    });
        }

        boolean on = plugin.chat().isToggled(viewer.getUniqueId());
        put("chat-toggle", 46, on ? Material.LIME_DYE : Material.GRAY_DYE, false,
                b -> b.name(L("gui.main.chat-toggle", "status", L(on ? "gui.main.chat-on" : "gui.main.chat-off")))
                        .lore(L("gui.main.team-chat-lore"), L("gui.common.click-toggle")),
                e -> {
                    Sounds.click(viewer);
                    plugin.chat().toggle(viewer.getUniqueId());
                    refresh();
                    viewer.openInventory(inventory);
                });

        put("players", 47, Material.ENDER_EYE, true,
                b -> b.name(L("gui.main.players"))
                        .lore(L("gui.main.players-lore"),
                                L("gui.main.invite-players-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openPlayers(viewer); });

        put("relations", 48, Material.EMERALD, false,
                b -> b.name(L("gui.main.relations"))
                        .lore(L("gui.main.relations-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openRelations(viewer); });

        put("browse-teams", 49, Material.WHITE_BANNER, true,
                b -> b.name(L("gui.main.browse-teams"))
                        .lore(L("gui.main.browse-teams-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openTeams(viewer); });

        put("leaderboard", 50, Material.GOLD_INGOT, false,
                b -> b.name(L("gui.main.leaderboard"))
                        .lore(L("gui.main.leaderboard-lore"), L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openTop(viewer); });

        int pendingReqs = plugin.teams().joinRequestsFor(team.id()).size();
        put("join-requests", 51, Material.LIME_DYE, pendingReqs > 0,
                b -> b.name(L("gui.main.join-requests"))
                        .lore(L("gui.main.join-requests-lore"),
                                L("gui.main.join-requests-pending", "count", pendingReqs),
                                L("gui.common.click-open")),
                e -> { Sounds.click(viewer); plugin.menus().openJoinRequests(viewer); });

        set(layout().slot("close", 53), Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void openChest(Team team) {
        if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.CHEST_OPEN)) {
            plugin.messages().send(viewer, "chest.no-access");
            Sounds.error(viewer);
            return;
        }
        viewer.closeInventory();
        plugin.chests().open(viewer, team, 0);
    }
}
