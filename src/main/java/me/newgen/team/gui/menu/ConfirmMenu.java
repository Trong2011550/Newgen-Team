package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.gui.MenuLayoutManager;
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
    private boolean confirmed;

    public ConfirmMenu(NewGenTeamPlugin plugin, Player viewer, String prompt, String detail,
                       Runnable onConfirm, Runnable onCancel) {
        super(viewer);
        this.plugin = plugin;
        this.prompt = prompt;
        this.detail = detail;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    private MenuLayoutManager.MenuLayout layout() {
        return plugin.menuLayouts().layout("confirm");
    }

    @Override protected int size() { return layout().size(27); }
    @Override protected String title() { return prompt; }

    @Override
    protected void build() {
        MenuLayoutManager.MenuLayout l = layout();
        for (int i = 0; i < size(); i++) set(i, Icons.edgeFiller());

        if (l.enabled("info")) {
            ItemBuilder b = ItemBuilder.of(l.material("info", Material.PAPER))
                    .name(prompt)
                    .lore(detail)
                    .glow(l.glow("info", true));
            int model = l.modelData("info", 0);
            if (model > 0) b.modelData(model);
            set(l.slot("info", 13), b.build());
        }

        set(l.slot("confirm", 11), Icons.confirm(detail), e -> {
            // The button stays clickable for a tick while the follow-up menu opens.
            if (confirmed) return;
            confirmed = true;
            Sounds.success(viewer);
            if (onConfirm != null) onConfirm.run();
        });
        set(l.slot("cancel", 15), Icons.cancel(), e -> {
            Sounds.click(viewer);
            if (onCancel != null) onCancel.run();
            else viewer.closeInventory();
        });

        set(l.slot("back", 22), Icons.back(), e -> {
            Sounds.click(viewer);
            if (onCancel != null) onCancel.run();
            else plugin.menus().openMain(viewer);
        });
    }
}
