package me.newgen.team.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class Inventories {
    private Inventories() {
    }

    public static byte[] toBytes(ItemStack[] items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();){
            byte[] byArray;
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream((OutputStream)baos);){
                out.writeInt(items.length);
                for (ItemStack item : items) {
                    out.writeObject((Object)item);
                }
                out.flush();
                byArray = baos.toByteArray();
            }
            return byArray;
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to serialize inventory", e);
        }
    }

    public static ItemStack[] fromBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return new ItemStack[0];
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);){
            ItemStack[] itemStackArray;
            try (BukkitObjectInputStream in = new BukkitObjectInputStream((InputStream)bais);){
                int len = in.readInt();
                ItemStack[] items = new ItemStack[len];
                for (int i = 0; i < len; ++i) {
                    items[i] = (ItemStack)in.readObject();
                }
                itemStackArray = items;
            }
            return itemStackArray;
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to deserialize inventory", e);
        }
    }

    public static void copyInto(Inventory from, Inventory to) {
        ItemStack[] src = from.getContents();
        int limit = Math.min(src.length, to.getSize());
        for (int i = 0; i < limit; ++i) {
            to.setItem(i, src[i]);
        }
    }
}
