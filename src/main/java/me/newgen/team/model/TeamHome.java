/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 */
package me.newgen.team.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class TeamHome {
    public final String world;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;

    public TeamHome(String world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static TeamHome from(Location l) {
        return new TeamHome(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location toLocation() {
        World w = Bukkit.getWorld((String)this.world);
        if (w == null) {
            return null;
        }
        return new Location(w, this.x, this.y, this.z, this.yaw, this.pitch);
    }
}
