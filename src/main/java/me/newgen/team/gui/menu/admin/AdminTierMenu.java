package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.config.TierConfig;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.Team;
import me.newgen.team.permission.AdminPermission;
import me.newgen.team.service.AdminService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class AdminTierMenu extends Menu {

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;

    public AdminTierMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId) {
        super(viewer);
        this.plugin = plugin;
        this.teamId = teamId;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 45; }
    @Override protected String title() { return L("gui.admin.tier.title"); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) {
            boolean edge = i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8;
            set(i, edge ? Icons.edgeFiller() : Icons.filler());
        }
        Team team = plugin.teams().byId(teamId);
        if (team == null) {
            set(22, ItemBuilder.of(Material.BARRIER).name(L("admin.team-missing")).build());
            set(40, Icons.back(), e -> { Sounds.click(viewer); back(); });
            return;
        }
        TierConfig cfg = plugin.config().tierConfig();
        boolean canEdit = AdminPermission.EDIT.held(viewer);

        int slot = 10;
        for (TierConfig.Tier tier : cfg.tiers()) {
            if (slot % 9 == 8) slot++;
            if (slot > 34) break;
            boolean current = team.tier() == tier.level;
            final int level = tier.level;
            set(slot++, ItemBuilder.of(current ? Material.NETHER_STAR : Material.ANVIL)
                    .name(L("gui.admin.tier.entry", "level", tier.level,
                            "current", current ? L("gui.admin.tier.current-suffix") : ""))
                    .lore(L("gui.admin.tier.chest-slots", "slots", tier.chestSlots),
                            L("gui.upgrade.member-limit", "limit", tier.memberLimit),
                            L("gui.admin.tier.home-limit", "limit", tier.homeLimit),
                            "",
                            canEdit ? L("gui.admin.tier.set-hint") : L("gui.admin.tier.view-only"))
                    .glow(current).build(),
                    canEdit ? e -> {
                        Sounds.click(viewer);
                        AdminService.AdminResult r = plugin.admin().setTier(
                                team, viewer.getUniqueId(), viewer.getName(), level);
                        if (r.ok()) Sounds.success(viewer); else Sounds.error(viewer);
                        viewer.sendMessage(me.newgen.team.util.Text.legacy(plugin.messages().prefix()
                                + (r.ok() ? "&#f7c6d9" : "&#f3a9c8") + r.message()));
                        reopen();
                    } : null);
        }

        set(40, Icons.back(), e -> { Sounds.click(viewer); back(); });
        set(44, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void back() { new AdminTeamDetailMenu(plugin, viewer, teamId).open(); }
    private void reopen() { new AdminTierMenu(plugin, viewer, teamId).open(); }
}
