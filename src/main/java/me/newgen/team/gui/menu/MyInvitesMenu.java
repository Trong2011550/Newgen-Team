package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.service.TeamService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MyInvitesMenu extends PaginatedMenu<Team> {

    private final NewGenTeamPlugin plugin;

    public MyInvitesMenu(NewGenTeamPlugin plugin, Player viewer, int page) {
        super(viewer, page);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() { return L("gui.my-invites.title"); }
    @Override protected int pageSize() { return 45; }

    @Override
    protected List<Team> elements() {
        List<Team> out = new ArrayList<>();
        Map<UUID, Long> invites = plugin.teams().invitesFor(viewer.getUniqueId());
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : invites.entrySet()) {
            if (e.getValue() < now) continue;
            Team t = plugin.teams().byId(e.getKey());
            if (t != null) out.add(t);
        }
        return out;
    }

    @Override
    protected ItemStack render(Team team) {
        return ItemBuilder.of(Material.WHITE_BANNER)
                .name(L("gui.main.team-header", "team", team.name(), "tag", team.tag()))
                .lore(L("gui.common.members", "members", team.size()),
                        L("gui.common.chest-level", "level", team.chestLevel()),
                        "",
                        L("gui.my-invites.left-accept"),
                        L("gui.my-invites.right-decline"))
                .glow(true).build();
    }

    @Override
    protected void onElementClick(Team team, InventoryClickEvent event) {
        if (event.isRightClick()) {
            TeamService.Result r = plugin.teams().declineInvite(viewer.getUniqueId(), team.id());
            if (r == TeamService.Result.SUCCESS) {
                Sounds.click(viewer);
                plugin.messages().send(viewer, "invite.denied");
            } else {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "invite.expired");
            }
            refresh();
            viewer.openInventory(inventory);
            return;
        }

        TeamService.Result r = plugin.teams().acceptInvite(viewer.getUniqueId(), viewer.getName(), team.id());
        switch (r) {
            case SUCCESS -> {
                Sounds.success(viewer);
                plugin.messages().send(viewer, "invite.accepted", "team", team.name());
                plugin.chat().notifyTeam(team, "notify.joined", "player", viewer.getName());
                plugin.menus().openMain(viewer);
            }
            case ALREADY_IN_TEAM -> { Sounds.error(viewer); plugin.messages().send(viewer, "team.already-in"); }
            case TEAM_FULL -> { Sounds.error(viewer); plugin.messages().send(viewer, "invite.team-full"); }
            case NOT_INVITED -> {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "invite.expired");
                refresh();
                viewer.openInventory(inventory);
            }
            default -> { Sounds.error(viewer); plugin.messages().send(viewer, "general.error"); }
        }
    }

    @Override
    protected void onBack() {
        plugin.menus().openMain(viewer);
    }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
        List<Team> all = elements();
        if (all.isEmpty()) {
            set(22, ItemBuilder.of(Material.GRAY_DYE)
                    .name(L("gui.my-invites.empty"))
                    .lore(L("gui.my-invites.empty-lore"),
                            L("gui.my-invites.empty-hint")).build());
        }
    }
}
