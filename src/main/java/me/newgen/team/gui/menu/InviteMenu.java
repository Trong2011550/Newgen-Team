package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class InviteMenu extends PaginatedMenu<Player> {

    private final NewGenTeamPlugin plugin;

    public InviteMenu(NewGenTeamPlugin plugin, Player viewer, int page) {
        super(viewer, page);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? L("gui.invite.title") : L("gui.invite.title-team", "team", t.name());
    }

    @Override
    protected List<Player> elements() {
        List<Player> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.teams().hasTeam(p.getUniqueId()) && !p.equals(viewer)) {
                list.add(p);
            }
        }
        return list;
    }

    @Override
    protected ItemStack render(Player p) {
        return ItemBuilder.of(Material.PLAYER_HEAD).head(p)
                .name(L("gui.invite.entry-name", "player", p.getName()))
                .lore(L("gui.common.no-team"), "", L("gui.player-list.invite-hint")).build();
    }

    @Override
    protected void onElementClick(Player p, InventoryClickEvent event) {
        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) return;
        if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.INVITE_MEMBER)) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        var r = plugin.teams().invite(team, p.getUniqueId());
        switch (r) {
            case SUCCESS -> {
                Sounds.success(viewer);
                plugin.messages().send(viewer, "invite.sent", "player", p.getName());
                plugin.messages().send(p, "invite.received", "team", team.name());
                plugin.chat().notifyTeam(team, "notify.invited",
                        "player", viewer.getName(), "target", p.getName());
                refresh();
                viewer.openInventory(inventory);
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
    }
}
