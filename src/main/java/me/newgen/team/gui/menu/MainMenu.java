package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class MainMenu extends Menu {

    private final NewGenTeamPlugin plugin;

    public MainMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 54; }

    @Override
    protected String title() {
        return L("gui.main.title");
    }

    @Override
    protected void build() {

        for (int i = 0; i < size(); i++) {
            boolean edge = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
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

        set(29, ItemBuilder.of(Material.NETHER_STAR)
                .name(L("gui.main.create"))
                .lore(L("gui.main.create-lore1"),
                        L("gui.main.create-lore-desc1"),
                        L("gui.main.create-lore-desc2"),
                        "",
                        L("gui.main.create-lore2"))
                .glow(true)
                .build(), e -> {
            Sounds.click(viewer);
            new me.newgen.team.gui.menu.CreateTeamMenu(plugin, viewer).open();
        });

        set(31, ItemBuilder.of(Material.NAME_TAG)
                .name(L("gui.main.my-invites"))
                .lore(L("gui.main.my-invites-lore1"),
                        L("gui.main.my-invites-lore2"),
                        "",
                        L("gui.common.click-open"))
                .glow(true)
                .build(), e -> {
            Sounds.click(viewer);
            plugin.menus().openMyInvites(viewer);
        });

        set(33, ItemBuilder.of(Material.ENDER_EYE)
                .name(L("gui.main.players"))
                .lore(L("gui.main.players-lore"),
                        "",
                        L("gui.common.click-open"))
                .glow(true)
                .build(), e -> {
            Sounds.click(viewer);
            plugin.menus().openPlayers(viewer);
        });

        set(47, ItemBuilder.of(Material.WHITE_BANNER)
                .name(L("gui.main.browse-teams"))
                .lore(L("gui.main.browse-teams-join-lore"),
                        "",
                        L("gui.common.click-open"))
                .glow(true)
                .build(), e -> {
            Sounds.click(viewer);
            plugin.menus().openTeams(viewer);
        });

        set(49, ItemBuilder.of(Material.BOOK)
                .name(L("gui.main.leaderboard"))
                .lore(L("gui.main.leaderboard-simple-lore"))
                .build(), e -> {
            Sounds.click(viewer);
            plugin.menus().openTop(viewer);
        });

        set(53, Icons.close(), e -> {
            Sounds.click(viewer);
            viewer.closeInventory();
        });
    }

    private void buildWithTeam(Team team) {
        TeamMember self = team.member(viewer.getUniqueId());
        String role = self == null ? "-" : L("gui.role.names." + self.role().name());

        set(4, ItemBuilder.of(Material.PLAYER_HEAD)
                .head(viewer)
                .name(L("gui.main.team-header", "team", team.name(), "tag", team.tag()))
                .lore(L("gui.common.members-limit", "members", team.size(), "limit", plugin.teams().memberLimit(team)),
                        L("gui.main.your-role", "role", role),
                        L("gui.common.team-tier", "tier", team.tier()),
                        L("gui.common.team-balance", "balance", team.balance()),
                        "",
                        L("gui.main.kd", "kills", team.kills(), "deaths", team.deaths(),
                                "kdr", String.format("%.2f", team.kdr())))
                .glow(true)
                .build());

        set(19, ItemBuilder.of(Material.PLAYER_HEAD).head(viewer)
                .name(L("gui.main.members"))
                .lore(L("gui.main.members-lore"), L("gui.common.click-open")).build(),
                e -> { Sounds.click(viewer); plugin.menus().openMembers(viewer); });

        set(22, ItemBuilder.of(Material.NAME_TAG)
                .name(L("gui.main.invites"))
                .lore(L("gui.main.invites-lore"), L("gui.common.click-open")).build(),
                e -> { Sounds.click(viewer); plugin.menus().openInvites(viewer); });

        set(25, ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(L("gui.main.permissions"))
                .lore(L("gui.main.permissions-lore"), L("gui.common.click-open")).build(),
                e -> { Sounds.click(viewer); plugin.menus().openPermissions(viewer); });

        set(28, ItemBuilder.of(Material.CHEST)
                .name(L("gui.main.chest"))
                .lore(L("gui.main.chest-lore", "level", team.chestLevel()),
                        L("gui.common.click-open")).glow(true).build(),
                e -> { Sounds.click(viewer); openChest(team); });

        set(31, ItemBuilder.of(Material.GOLD_BLOCK)
                .name(L("gui.main.bank"))
                .lore(L("gui.common.team-balance", "balance", team.balance()),
                        L("gui.main.bank-lore"), L("gui.common.click-open")).glow(true).build(),
                e -> { Sounds.click(viewer); plugin.menus().openBank(viewer); });

        set(34, ItemBuilder.of(Material.ANVIL)
                .name(L("gui.main.upgrade"))
                .lore(L("gui.main.upgrade-lore"), L("gui.common.click-open")).build(),
                e -> { Sounds.click(viewer); plugin.menus().openUpgrades(viewer); });

        set(37, ItemBuilder.of(Material.ENDER_CHEST)
                .name(L("gui.main.homes"))
                .lore(L("gui.main.homes-lore"), L("gui.common.click-open")).build(),
                e -> { Sounds.click(viewer); plugin.menus().openHome(viewer); });

        set(40, ItemBuilder.of(Material.COMPARATOR)
                .name(L("gui.main.settings"))
                .lore(L("gui.main.settings-lore"), L("gui.common.click-open")).build(),
                e -> { Sounds.click(viewer); plugin.menus().openSettings(viewer); });

        set(43, ItemBuilder.of(Material.DIAMOND)
                .name(L("gui.main.stats"))
                .lore(L("gui.main.stats-lore"), L("gui.common.click-open")).build(),
                e -> { Sounds.click(viewer); plugin.menus().openStats(viewer); });

        boolean isLeader = team.leader().equals(viewer.getUniqueId());
        if (isLeader) {
            set(45, ItemBuilder.of(Material.TNT)
                    .name(L("gui.main.disband"))
                    .lore(L("gui.main.disband-lore"), L("gui.common.irreversible")).build(),
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
            set(45, ItemBuilder.of(Material.OAK_DOOR)
                    .name(L("gui.main.leave"))
                    .lore(L("gui.main.leave-lore", "team", team.name())).build(),
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
        set(46, ItemBuilder.of(on ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(L("gui.main.chat-toggle", "status", L(on ? "gui.main.chat-on" : "gui.main.chat-off")))
                .lore(L("gui.main.team-chat-lore"), L("gui.common.click-toggle")).build(),
                e -> {
                    Sounds.click(viewer);
                    plugin.chat().toggle(viewer.getUniqueId());
                    refresh();
                    viewer.openInventory(inventory);
                });

        set(47, ItemBuilder.of(Material.ENDER_EYE)
                .name(L("gui.main.players"))
                .lore(L("gui.main.players-lore"),
                        L("gui.main.invite-players-lore"), L("gui.common.click-open")).glow(true).build(),
                e -> { Sounds.click(viewer); plugin.menus().openPlayers(viewer); });

        set(48, ItemBuilder.of(Material.EMERALD)
                .name(L("gui.main.relations"))
                .lore(L("gui.main.relations-lore"), L("gui.common.click-open")).build(),
                e -> { Sounds.click(viewer); plugin.menus().openRelations(viewer); });

        set(49, ItemBuilder.of(Material.WHITE_BANNER)
                .name(L("gui.main.browse-teams"))
                .lore(L("gui.main.browse-teams-lore"), L("gui.common.click-open")).glow(true).build(),
                e -> { Sounds.click(viewer); plugin.menus().openTeams(viewer); });

        set(50, ItemBuilder.of(Material.GOLD_INGOT)
                .name(L("gui.main.leaderboard"))
                .lore(L("gui.main.leaderboard-lore"), L("gui.common.click-open")).build(),
                e -> { Sounds.click(viewer); plugin.menus().openTop(viewer); });

        int pendingReqs = plugin.teams().joinRequestsFor(team.id()).size();
        set(51, ItemBuilder.of(Material.LIME_DYE)
                .name(L("gui.main.join-requests"))
                .lore(L("gui.main.join-requests-lore"),
                        L("gui.main.join-requests-pending", "count", pendingReqs),
                        L("gui.common.click-open")).glow(pendingReqs > 0).build(),
                e -> { Sounds.click(viewer); plugin.menus().openJoinRequests(viewer); });

        set(53, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
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
