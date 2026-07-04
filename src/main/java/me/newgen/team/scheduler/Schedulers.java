package me.newgen.team.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Schedulers {

    private static boolean FOLIA;
    private static Plugin PLUGIN;

    private Schedulers() {}

    public static void init(Plugin plugin) {
        PLUGIN = plugin;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            FOLIA = true;
        } catch (ClassNotFoundException e) {
            FOLIA = false;
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void global(Runnable task) {
        if (FOLIA) {
            PLUGIN.getServer().getGlobalRegionScheduler().execute(PLUGIN, task);
        } else {
            PLUGIN.getServer().getScheduler().runTask(PLUGIN, task);
        }
    }

    public static void region(Location loc, Runnable task) {
        if (FOLIA) {
            PLUGIN.getServer().getRegionScheduler().execute(PLUGIN, loc, task);
        } else {
            PLUGIN.getServer().getScheduler().runTask(PLUGIN, task);
        }
    }

    public static void entity(Entity entity, Runnable task) {
        if (FOLIA) {
            entity.getScheduler().run(PLUGIN, t -> task.run(), null);
        } else {
            PLUGIN.getServer().getScheduler().runTask(PLUGIN, task);
        }
    }

    public static void entityLater(Entity entity, Runnable task, long delayTicks) {
        if (FOLIA) {
            entity.getScheduler().runDelayed(PLUGIN, t -> task.run(), null, Math.max(1, delayTicks));
        } else {
            PLUGIN.getServer().getScheduler().runTaskLater(PLUGIN, task, delayTicks);
        }
    }

    public static void async(Runnable task) {
        if (FOLIA) {
            PLUGIN.getServer().getAsyncScheduler().runNow(PLUGIN, t -> task.run());
        } else {
            PLUGIN.getServer().getScheduler().runTaskAsynchronously(PLUGIN, task);
        }
    }

    public static void asyncRepeating(Runnable task, long initialDelaySec, long periodSec) {
        if (FOLIA) {
            PLUGIN.getServer().getAsyncScheduler().runAtFixedRate(
                    PLUGIN, t -> task.run(), initialDelaySec, periodSec, TimeUnit.SECONDS);
        } else {
            PLUGIN.getServer().getScheduler().runTaskTimerAsynchronously(
                    PLUGIN, task, initialDelaySec * 20L, periodSec * 20L);
        }
    }

    public static void globalRepeating(Runnable task, long initialTicks, long periodTicks) {
        if (FOLIA) {
            PLUGIN.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    PLUGIN, t -> task.run(), Math.max(1, initialTicks), Math.max(1, periodTicks));
        } else {
            PLUGIN.getServer().getScheduler().runTaskTimer(PLUGIN, task, initialTicks, periodTicks);
        }
    }
}
