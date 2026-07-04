package me.newgen.team.gui.menu;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.config.TierConfig;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.Menu;
import me.newgen.team.model.Team;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.service.TierService;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class UpgradeMenu extends Menu {

    private final NewGenTeamPlugin plugin;

    private static final int[] TIER_SLOTS = {19, 21, 23, 25};
    private static final int[] BUTTON_SLOTS = {28, 30, 32, 34};

    public UpgradeMenu(NewGenTeamPlugin plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    private String currency() {
        return plugin.points().currencyName();
    }

    @Override protected int size() { return 54; }
    @Override protected String title() {
        Team t = plugin.teams().byPlayer(viewer.getUniqueId());
        return t == null ? L("gui.upgrade.title") : L("gui.upgrade.title-team", "team", t.name());
    }

    @Override
    protected void build() {
        for (int i = 0; i < size(); i++) set(i, Icons.filler());

        Team team = plugin.teams().byPlayer(viewer.getUniqueId());
        if (team == null) { viewer.closeInventory(); return; }

        boolean economy = plugin.playerPoints().isAvailable();
        long balance = economy ? plugin.playerPoints().balance(viewer.getUniqueId()) : 0;

        set(4, ItemBuilder.of(Material.SUNFLOWER)
                .name(L("gui.upgrade.your-balance"))
                .lore(economy
                        ? L("gui.upgrade.balance-line", "balance", balance, "currency", currency())
                        : L("gui.upgrade.no-playerpoints")).glow(true).build());

        TierService tiers = plugin.tiers();
        List<TierConfig.Tier> table = plugin.config().tierConfig().tiers();
        int currentLevel = team.tier();
        int nextLevel = currentLevel + 1;

        for (int i = 0; i < table.size() && i < TIER_SLOTS.length; i++) {
            TierConfig.Tier t = table.get(i);
            boolean owned = t.level <= currentLevel;
            boolean isNext = t.level == nextLevel;
            buildTierTile(team, t, owned, isNext, economy, balance);
        }

        set(49, Icons.back(), e -> { Sounds.click(viewer); plugin.menus().openMain(viewer); });
        set(53, Icons.close(), e -> { Sounds.click(viewer); viewer.closeInventory(); });
    }

    private void buildTierTile(Team team, TierConfig.Tier t, boolean owned, boolean isNext,
                               boolean economy, long balance) {
        int slot = TIER_SLOTS[t.level - 1];
        int buttonSlot = BUTTON_SLOTS[t.level - 1];

        Material icon = owned ? Material.NETHER_STAR
                : isNext ? Material.CHEST : Material.GRAY_DYE;
        String status = owned ? L("gui.upgrade.status-owned")
                : isNext ? L("gui.upgrade.status-buyable") : L("gui.upgrade.status-locked");

        List<String> lore = new ArrayList<>();
        lore.add(L("gui.upgrade.perks-header", "level", t.level));
        lore.add(L("gui.upgrade.chest-pages", "pages", t.chestSlots / me.newgen.team.model.ChestTier.STORAGE_PER_PAGE));
        lore.add(L("gui.upgrade.member-limit", "limit", t.memberLimit));
        lore.add(L("gui.upgrade.home-limit", "limit", t.homeLimit));
        lore.add("");
        if (t.level == 1) {
            lore.add(L("gui.upgrade.default-free"));
        } else if (owned) {
            lore.add(L("gui.upgrade.owned-unlocked"));
        } else {
            lore.add(L("gui.upgrade.price", "price", t.price, "currency", currency()));
            if (!isNext) lore.add(L("gui.upgrade.need-previous"));
        }

        set(slot, ItemBuilder.of(icon)
                .name(L("gui.upgrade.tier-name", "level", t.level, "status", status))
                .lore(lore.toArray(new String[0]))
                .glow(owned || isNext).build());

        if (isNext) {
            boolean ok = economy && balance >= t.price;
            set(buttonSlot, button(ok, L("gui.upgrade.buy-line", "level", t.level), t.price),
                    e -> attempt(team));
        } else {
            set(buttonSlot, Icons.filler());
        }
    }

    private org.bukkit.inventory.ItemStack button(boolean affordable, String desc, long price) {
        return ItemBuilder.of(affordable ? Material.LIME_DYE : Material.RED_DYE)
                .name(affordable ? L("gui.upgrade.buy-now")
                        : L("gui.upgrade.not-enough", "currency", currency()))
                .lore(desc, L("gui.upgrade.price", "price", price, "currency", currency()))
                .glow(affordable).build();
    }

    private void attempt(Team team) {
        if (!plugin.permissions().has(team, viewer.getUniqueId(), TeamPermission.CHEST_UPGRADE)) {
            Sounds.error(viewer);
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        TierService.Result r = plugin.tiers().upgrade(team, viewer.getUniqueId());
        switch (r) {
            case SUCCESS -> {
                Sounds.upgrade(viewer);
                plugin.messages().send(viewer, "upgrade.success", "level", team.tier());
                refresh();
                viewer.openInventory(inventory);
            }
            case MAX_LEVEL -> { Sounds.error(viewer); plugin.messages().send(viewer, "upgrade.max-level"); }
            case NO_ECONOMY -> { Sounds.error(viewer); plugin.messages().send(viewer, "upgrade.no-economy"); }
            case INSUFFICIENT_FUNDS -> {
                Sounds.error(viewer);
                plugin.messages().send(viewer, "upgrade.insufficient");
            }
            default -> { Sounds.error(viewer); plugin.messages().send(viewer, "general.error"); }
        }
    }
}
