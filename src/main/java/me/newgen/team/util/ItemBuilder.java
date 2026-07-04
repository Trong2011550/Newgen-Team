package me.newgen.team.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack base) {
        this.item = base.clone();
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ItemBuilder name(String legacy) {
        if (meta != null) meta.displayName(Text.auto(legacy));
        return this;
    }

    public ItemBuilder name(Component component) {
        if (meta != null) meta.displayName(component);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null) meta.lore(Text.autoList(lines));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(new ArrayList<>(Arrays.asList(lines)));
    }

    public ItemBuilder glow(boolean glow) {
        if (meta != null && glow) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder hideAll() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    public ItemBuilder head(OfflinePlayer owner) {
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
        }
        return this;
    }

    public ItemBuilder modelData(int data) {
        if (meta != null) meta.setCustomModelData(data);
        return this;
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }
}
