package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ConfirmMenu extends Menu {

    private final NewGenTeamPlugin plugin;
    private final String prompt;
    private final String detail;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmMenu(NewGenTeamPlugin plugin, Player viewer, String prompt, String detail,
                       Runnable onConfirm, Runnable onCancel) {
        super(viewer);
        this.plugin = plugin;
        this.prompt = prompt;
        this.detail = detail;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override protected int size() { return 27; }
    @Override protected String title() { return prompt; }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) set(i, Icons.edgeFiller());

        set(13, ItemBuilder.of(Material.PAPER)
                .name(prompt)
                .lore("&#d8b8c6" + detail).glow(true).build());

        set(11, Icons.confirm(detail), e -> {
            Sounds.success(viewer);
            if (onConfirm != null) onConfirm.run();
        });
        set(15, Icons.cancel(), e -> {
            Sounds.click(viewer);
            if (onCancel != null) onCancel.run();
            else viewer.closeInventory();
        });

        set(22, Icons.back(), e -> { Sounds.click(viewer); plugin.menus().openMain(viewer); });
    }
}
