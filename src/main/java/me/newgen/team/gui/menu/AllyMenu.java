package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.service.RelationService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AllyMenu extends PaginatedMenu<Team> {

    private final NewGenTeamPlugin plugin;

    public AllyMenu(NewGenTeamPlugin plugin, Player viewer, int page) {
        super(viewer, page);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? L("gui.ally.title") : L("gui.ally.title-team", "team", t.name());
    }

    @Override
    protected List<Team> elements() {
        Team own = plugin.teams().byPlayer(viewer.getUniqueId());
        List<Team> list = new ArrayList<>();
        if (own == null) return list;
        for (Team t : plugin.teams().all()) {
            if (!t.id().equals(own.id())) list.add(t);
        }
        return list;
    }

    @Override
    protected ItemStack render(Team team) {
        Team own = plugin.teams().byPlayer(viewer.getUniqueId());
        if (own == null) return ItemBuilder.of(Material.BARRIER).name(L("gui.ally.no-team")).build();

        Team.RelationType rel = own.relations().get(team.id());
        Map<UUID, Long> incoming = plugin.relations().pendingRequests(own.id());
        Map<UUID, Long> outgoing = plugin.relations().pendingRequests(team.id());
        boolean pendingFromThem = incoming.containsKey(team.id());
        boolean pendingFromUs = outgoing.containsKey(own.id());

        Material icon;
        String relStr;
        boolean glow;
        List<String> lore = new ArrayList<>();

        if (rel == Team.RelationType.ALLY) {
            icon = Material.EMERALD;
            relStr = L("gui.ally.state-ally");
            glow = true;
            lore.add(L("gui.ally.relation-line", "relation", relStr));
            lore.add(L("gui.common.members", "members", team.size()));
            lore.add("");
            lore.add(L("gui.ally.left-remove-ally"));
            lore.add(L("gui.ally.right-mark-enemy"));
        } else if (rel == Team.RelationType.ENEMY) {
            icon = Material.REDSTONE;
            relStr = L("gui.ally.state-enemy");
            glow = true;
            lore.add(L("gui.ally.relation-line", "relation", relStr));
            lore.add(L("gui.common.members", "members", team.size()));
            lore.add("");
            lore.add(L("gui.ally.right-remove-enemy"));
        } else if (pendingFromThem) {
            icon = Material.GOLD_INGOT;
            relStr = L("gui.ally.state-pending");
            glow = true;
            lore.add(L("gui.ally.relation-line", "relation", relStr));
            lore.add(L("gui.common.members", "members", team.size()));
            lore.add("");
            lore.add(L("gui.ally.left-accept"));
        } else if (pendingFromUs) {
            icon = Material.PAPER;
            relStr = L("gui.ally.state-sent");
            glow = true;
            lore.add(L("gui.ally.relation-line", "relation", relStr));
            lore.add(L("gui.common.members", "members", team.size()));
            lore.add("");
            lore.add(L("gui.ally.waiting"));
            lore.add(L("gui.ally.left-cancel"));
        } else {
            icon = Material.PAPER;
            relStr = L("gui.ally.state-neutral");
            glow = false;
            lore.add(L("gui.ally.relation-line", "relation", relStr));
            lore.add(L("gui.common.members", "members", team.size()));
            lore.add("");
            lore.add(L("gui.ally.left-request"));
            lore.add(L("gui.ally.right-mark-enemy"));
        }

        return ItemBuilder.of(icon)
                .name(L("gui.ally.entry-name", "team", team.name(), "tag", team.tag()))
                .lore(lore)
                .glow(glow).build();
    }

    @Override
    protected void onElementClick(Team target, InventoryClickEvent event) {
        Team own = plugin.teams().byPlayer(viewer.getUniqueId());
        if (own == null) return;
        if (!plugin.permissions().has(own, viewer.getUniqueId(), TeamPermission.MANAGE_RELATIONS)) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        RelationService rs = plugin.relations();
        Team.RelationType rel = own.relations().get(target.id());
        boolean pendingFromThem = rs.pendingRequests(own.id()).containsKey(target.id());

        if (event.isRightClick()) {
            RelationService.Result r = (rel == Team.RelationType.ENEMY)
                    ? rs.removeEnemy(own, target) : rs.setEnemy(own, target);
            feedback(r, target, "enemy");
        } else if (rel == Team.RelationType.ALLY) {
            feedback(rs.removeAlly(own, target), target, "ally");
        } else if (pendingFromThem) {
            feedback(rs.acceptAlly(own, target), target, "ally-accept");
        } else {

            feedback(rs.requestAlly(own, target), target, "ally");
        }
        refresh();
        viewer.openInventory(inventory);
    }

    private void feedback(RelationService.Result r, Team target, String kind) {
        switch (r) {
            case SUCCESS -> {
                Sounds.success(viewer);
                String key = switch (kind) {
                    case "ally-accept" -> "relation.ally-accepted";
                    case "ally-cancel" -> "relation.ally-cancelled";
                    case "enemy" -> "relation.enemy-set";
                    default -> "relation.ally-set";
                };
                plugin.messages().send(viewer, key, "team", target.name());
            }
            case REQUEST_SENT -> { Sounds.success(viewer); plugin.messages().send(viewer,
                    "relation.ally-requested", "team", target.name()); }
            case REQUESTS_DISABLED -> { Sounds.error(viewer); plugin.messages().send(viewer,
                    "relation.requests-disabled"); }
            case ALREADY_ALLY -> { Sounds.error(viewer); plugin.messages().send(viewer,
                    "relation.already-ally"); }
            case NOT_ALLY -> { Sounds.error(viewer); plugin.messages().send(viewer,
                    "relation.not-ally"); }
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
