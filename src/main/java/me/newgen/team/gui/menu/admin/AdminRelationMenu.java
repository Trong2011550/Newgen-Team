package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.Team;
import me.newgen.team.permission.AdminPermission;
import me.newgen.team.service.AdminService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AdminRelationMenu extends PaginatedMenu<Team> {

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;

    public AdminRelationMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId, int page) {
        super(viewer, page);
        this.plugin = plugin;
        this.teamId = teamId;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() { return L("gui.admin.relation.title"); }
    @Override protected int pageSize() { return 45; }

    private Team subject() { return plugin.teams().byId(teamId); }

    @Override
    protected List<Team> elements() {
        Team subject = subject();
        if (subject == null) return List.of();
        List<Team> out = new ArrayList<>();
        for (Team t : plugin.teams().all()) {
            if (!t.id().equals(teamId)) out.add(t);
        }
        out.sort(Comparator.comparing(Team::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    protected ItemStack render(Team other) {
        Team subject = subject();
        Team.RelationType rel = subject == null ? null : subject.relations().get(other.id());
        String relText = rel == null ? L("gui.admin.relation.state-neutral")
                : rel == Team.RelationType.ALLY ? L("gui.admin.relation.state-ally") : L("gui.admin.relation.state-enemy");
        Material mat = rel == null ? Material.WHITE_BANNER
                : rel == Team.RelationType.ALLY ? Material.LIME_BANNER : Material.RED_BANNER;
        List<String> lore = new ArrayList<>();
        lore.add(L("gui.admin.relation.current-line", "relation", relText));
        if (AdminPermission.RELATION.held(viewer)) {
            lore.add("");
            lore.add(L("gui.admin.relation.left-ally"));
            lore.add(L("gui.admin.relation.right-enemy"));
            lore.add(L("gui.admin.relation.shift-neutral"));
        }
        return ItemBuilder.of(mat)
                .name(L("gui.admin.relation.entry-name", "team", other.name(), "tag", other.tag()))
                .lore(lore).build();
    }

    @Override
    protected void onElementClick(Team other, InventoryClickEvent event) {
        if (!AdminPermission.RELATION.held(viewer)) { Sounds.error(viewer); return; }
        Team subject = subject();
        if (subject == null) { Sounds.error(viewer); return; }
        ClickType t = event.getClick();
        UUID staff = viewer.getUniqueId();
        AdminService a = plugin.admin();
        AdminService.AdminResult r;
        if (t.isShiftClick()) {
            r = a.setNeutral(subject, other, staff, viewer.getName());
        } else if (t.isRightClick()) {
            r = a.setEnemy(subject, other, staff, viewer.getName());
        } else {
            r = a.setAlly(subject, other, staff, viewer.getName());
        }
        if (r.ok()) Sounds.success(viewer); else Sounds.error(viewer);
        viewer.sendMessage(me.newgen.team.util.Text.legacy(plugin.messages().prefix()
                + (r.ok() ? "&#f7c6d9" : "&#f3a9c8") + r.message()));
        reopen();
    }

    @Override
    protected void onBack() { new AdminTeamDetailMenu(plugin, viewer, teamId).open(); }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
        Team subject = subject();
        if (subject != null) {
            set(46, ItemBuilder.of(Material.NETHER_STAR)
                    .name(L("gui.admin.relation.subject-name", "team", subject.name()))
                    .lore(L("gui.admin.relation.subject-lore")).glow(true).build());
        }
    }

    private void reopen() { new AdminRelationMenu(plugin, viewer, teamId, page).open(); }
}
