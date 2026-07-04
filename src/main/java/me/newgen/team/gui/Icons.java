package me.newgen.team.gui;

import me.newgen.team.config.MessageManager;
import me.newgen.team.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class Icons {

    public static final String PINK_SOFT = "#f7c6d9";

    public static final String PINK_MID = "#f3a9c8";

    public static final String PINK_LIGHT = "#ffd6e7";

    public static final String GRAY_SOFT = "#d8b8c6";

    private static MessageManager messages;

    private Icons() {}

    /** Wired once in NewGenTeamPlugin#onEnable so static icon factories can pull lang text. */
    public static void init(MessageManager msg) {
        messages = msg;
    }

    private static String lang(String key, Object... pairs) {
        return messages.format(key, pairs);
    }

    public static ItemStack filler() {
        return ItemBuilder.of(Material.PINK_STAINED_GLASS_PANE)
                .name(" ")
                .hideAll()
                .build();
    }

    public static ItemStack edgeFiller() {
        return ItemBuilder.of(Material.MAGENTA_STAINED_GLASS_PANE)
                .name(" ")
                .hideAll()
                .build();
    }

    public static ItemStack back() {
        return ItemBuilder.of(Material.ARROW)
                .name(lang("gui.common.back"))
                .lore(lang("gui.common.back-lore"))
                .build();
    }

    public static ItemStack close() {
        return ItemBuilder.of(Material.BARRIER)
                .name(lang("gui.common.close"))
                .lore(lang("gui.common.close-lore"))
                .build();
    }

    public static ItemStack confirm(String action) {
        return ItemBuilder.of(Material.LIME_DYE)
                .name(lang("gui.common.confirm"))
                .lore(action)
                .glow(true)
                .build();
    }

    public static ItemStack cancel() {
        return ItemBuilder.of(Material.PINK_DYE)
                .name(lang("gui.common.cancel"))
                .lore(lang("gui.common.cancel-lore"))
                .build();
    }

    public static ItemStack nextPage(int next) {
        return ItemBuilder.of(Material.SPECTRAL_ARROW)
                .name(lang("gui.common.next-page"))
                .lore(lang("gui.common.next-page-lore", "page", next))
                .build();
    }

    public static ItemStack prevPage(int prev) {
        return ItemBuilder.of(Material.SPECTRAL_ARROW)
                .name(lang("gui.common.prev-page"))
                .lore(lang("gui.common.prev-page-lore", "page", prev))
                .build();
    }

    public static ItemStack disabledPage() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name("<muted>—")
                .hideAll()
                .build();
    }
}
