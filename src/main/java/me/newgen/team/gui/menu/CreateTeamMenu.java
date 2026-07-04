package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.service.TeamService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class CreateTeamMenu extends Menu {

    private final NewGenTeamPlugin plugin;

    public CreateTeamMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected int size() { return 27; }
    @Override protected String title() { return L("gui.create.title"); }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) set(i, Icons.filler());

        set(13, ItemBuilder.of(Material.NETHER_STAR)
                .name(L("gui.main.create"))
                .lore(L("gui.create.name-header"),
                        L("gui.create.name-length", "max", plugin.config().maxNameLength()),
                        L("gui.create.name-allowed"),
                        "",
                        L("gui.create.sign-hint"))
                .glow(true).build(), e -> {
            Sounds.click(viewer);
            promptForName();
        });

        set(18, Icons.back(), e -> { Sounds.click(viewer); plugin.menus().openMain(viewer); });
        set(26, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void promptForName() {
        viewer.closeInventory();
        plugin.signInput().await(viewer, input -> {
            if (input == null) {
                plugin.messages().send(viewer, "search.cancelled");
                return;
            }

            String name = input;
            TeamService.Result r = plugin.teams().createTeam(
                    viewer.getUniqueId(), viewer.getName(), name, null);
            switch (r) {
                case SUCCESS -> {
                    Sounds.success(viewer);
                    plugin.messages().send(viewer, "team.created", "name", name);
                    plugin.menus().openMain(viewer);
                }
                case ALREADY_IN_TEAM -> {
                    Sounds.error(viewer);
                    plugin.messages().send(viewer, "team.already-in");
                }
                case NAME_TAKEN -> {
                    Sounds.error(viewer);
                    plugin.messages().send(viewer, "team.name-taken");
                    new CreateTeamMenu(plugin, viewer).open();
                }
                case NAME_INVALID -> {
                    Sounds.error(viewer);
                    plugin.messages().send(viewer, "team.name-invalid");
                    new CreateTeamMenu(plugin, viewer).open();
                }
                default -> {
                    Sounds.error(viewer);
                    plugin.messages().send(viewer, "general.error");
                }
            }
        });
    }
}
