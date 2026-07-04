package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamRole;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public final class PermissionMenu extends PaginatedMenu<TeamRole> {

    private final NewGenTeamPlugin plugin;

    public PermissionMenu(NewGenTeamPlugin plugin, Player viewer, int page) {
        super(viewer, page);
        this.plugin = plugin;
    }

    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? plugin.messages().raw("permission.title")
                : plugin.messages().format("permission.title-team", "team", t.name());
    }
    @Override protected int pageSize() { return 45; }

    @Override
    protected List<TeamRole> elements() {
        return Arrays.asList(TeamRole.values());
    }

    @Override
    protected ItemStack render(TeamRole role) {
        var perms = plugin.permissions().permissionsOf(role);
        var msg = plugin.messages();
        var lore = new java.util.ArrayList<String>();
        lore.add(msg.format("permission.weight", "weight", role.weight()));
        lore.add("");
        lore.add(msg.raw("permission.header"));
        if (role == TeamRole.OWNER) {
            lore.add(msg.raw("permission.all"));
        } else {
            for (var p : me.newgen.team.permission.TeamPermission.values()) {
                String label = msg.has("permission.names." + p.name())
                        ? msg.raw("permission.names." + p.name())
                        : p.name().toLowerCase().replace('_', ' ');
                lore.add((perms.contains(p) ? msg.raw("permission.has") : msg.raw("permission.missing")) + label);
            }
        }
        return ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(role.display())
                .lore(lore).glow(role == TeamRole.OWNER).build();
    }

    @Override
    protected void onElementClick(TeamRole role, InventoryClickEvent event) {
        Sounds.click(viewer);
    }

    @Override
    protected void onBack() { plugin.menus().openMain(viewer); }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
    }
}
