package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamSettings;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public final class SettingsMenu extends Menu {

    private final NewGenTeamPlugin plugin;

    public SettingsMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 45; }
    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? L("gui.settings.title") : L("gui.settings.title-team", "team", t.name());
    }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) set(i, Icons.filler());

        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) { viewer.closeInventory(); return; }
        boolean canEdit = plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.EDIT_SETTINGS);
        TeamSettings s = team.settings();

        toggle(team, canEdit, 10, Material.IRON_DOOR, L("gui.settings.open-team"),
                L("gui.settings.open-team-desc"), () -> s.open, v -> s.open = v);
        toggle(team, canEdit, 11, Material.IRON_SWORD, L("gui.settings.friendly-fire"),
                L("gui.settings.friendly-fire-desc"), () -> s.friendlyFire, v -> s.friendlyFire = v);
        toggle(team, canEdit, 12, Material.PAPER, L("gui.settings.team-chat"),
                L("gui.settings.team-chat-desc"), () -> s.teamChat, v -> s.teamChat = v);
        toggle(team, canEdit, 13, Material.WRITABLE_BOOK, L("gui.settings.auto-accept"),
                L("gui.settings.auto-accept-desc"), () -> s.autoAcceptInvite, v -> s.autoAcceptInvite = v);

        toggle(team, canEdit, 19, Material.OAK_SIGN, L("gui.settings.public-join"),
                L("gui.settings.public-join-desc"), () -> s.publicJoin, v -> s.publicJoin = v);
        toggle(team, canEdit, 20, Material.BELL, L("gui.settings.join-notify"),
                L("gui.settings.join-notify-desc"), () -> s.notifyJoinLeave, v -> s.notifyJoinLeave = v);
        toggle(team, canEdit, 21, Material.EMERALD, L("gui.settings.ally-requests"),
                L("gui.settings.ally-requests-desc"), () -> s.allyRequests, v -> s.allyRequests = v);

        set(40, Icons.back(), e -> { Sounds.click(viewer); plugin.menus().openMain(viewer); });
        set(44, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void toggle(Team team, boolean canEdit, int slot, Material icon, String name,
                        String desc, Predicate0 getter, Consumer<Boolean> setter) {
        boolean state = getter.get();
        set(slot, ItemBuilder.of(state ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(L("gui.settings.toggle-name", "name", name,
                        "state", L(state ? "gui.settings.state-on" : "gui.settings.state-off")))
                .lore(L("gui.settings.desc-line", "desc", desc), "",
                        canEdit ? L("gui.settings.toggle-hint") : L("gui.settings.no-permission")).build(),
                e -> {
                    if (!canEdit) { Sounds.error(viewer); plugin.messages().send(viewer, "general.no-permission"); return; }
                    setter.accept(!state);
                    plugin.storage().saveTeam(team);
                    Sounds.click(viewer);
                    refresh();
                    viewer.openInventory(inventory);
                });
    }

    @FunctionalInterface private interface Predicate0 { boolean get(); }
}
