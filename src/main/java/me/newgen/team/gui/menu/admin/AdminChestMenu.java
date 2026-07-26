package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.gui.menu.ConfirmMenu;
import me.newgen.team.model.ChestTier;
import me.newgen.team.model.Team;
import me.newgen.team.permission.AdminPermission;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class AdminChestMenu extends Menu {

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;

    public AdminChestMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId) {
        super(viewer);
        this.plugin = plugin;
        this.teamId = teamId;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 27; }
    @Override protected String title() { return L("gui.admin.chest.title"); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) {
            boolean edge = i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8;
            set(i, edge ? Icons.edgeFiller() : Icons.filler());
        }
        Team team = plugin.teams().byId(teamId);
        if (team == null) {
            set(13, ItemBuilder.of(Material.BARRIER).name(L("admin.team-missing")).build());
            set(18, Icons.back(), e -> { Sounds.click(viewer); back(); });
            return;
        }
        ChestTier tier = plugin.config().chestConfig().tier(team.chestLevel());

        set(4, ItemBuilder.of(Material.CHEST)
                .name(L("gui.admin.chest.info", "team", team.name()))
                .lore(L("gui.admin.chest.level", "level", team.chestLevel()),
                        L("gui.admin.chest.slots", "slots", tier.slots),
                        L("gui.admin.chest.pages", "pages", tier.pages()))
                .glow(true).build());

        if (AdminPermission.CHEST.held(viewer)) {
            set(13, ItemBuilder.of(Material.LAVA_BUCKET)
                    .name(L("gui.admin.chest.reset"))
                    .lore(L("gui.admin.chest.reset-lore"),
                            L("gui.common.irreversible")).build(),
                    e -> { Sounds.click(viewer); confirmReset(team); });
        }

        set(18, Icons.back(), e -> { Sounds.click(viewer); back(); });
        set(26, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void confirmReset(Team team) {
        new ConfirmMenu(plugin, viewer, L("gui.admin.chest.reset-confirm-title"),
                L("gui.admin.chest.reset-confirm-lore", "team", team.name()),
                () -> {
                    var r = plugin.admin().resetChest(team, viewer.getUniqueId(), viewer.getName());
                    if (r.ok()) Sounds.success(viewer); else Sounds.error(viewer);
                    viewer.sendMessage(me.newgen.team.util.Text.mini(plugin.messages().prefix())
                .append(me.newgen.team.util.Text.legacy((r.ok() ? "&#b7f0c0" : "&#ff5555") + r.message())));
                    back();
                },
                this::back).open();
    }

    private void back() { new AdminTeamDetailMenu(plugin, viewer, teamId).open(); }
}
