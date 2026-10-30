package me.newgen.team.util;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.scheduler.Schedulers;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Accurate, plugin-attributable resource sampling for /newgenteam status.
 *
 * Everything expensive (the data-folder disk walk) runs off the main thread and
 * is cached, so repeated OP queries on a 500-player server never stall a tick.
 * JVM heap/CPU figures are process-wide (shared with the server) and are labelled
 * as such; the plugin-owned numbers are the data-folder size, live object counts
 * and accumulated scheduled-task CPU time.
 */
public final class ResourceMetrics {

    private ResourceMetrics() {}

    /** Disk-walk cache: avoids re-walking the data folder on every status call. */
    private static final long DISK_TTL_MS = 10_000L;
    private static volatile long diskCachedAt = 0L;
    private static volatile long diskBytes = 0L;
    private static volatile long diskFiles = 0L;

    /** Accumulated wall-clock time spent inside the plugin's own scheduled tasks. */
    private static final AtomicLong taskNanos = new AtomicLong();
    private static final AtomicLong taskRuns = new AtomicLong();

    public record Snapshot(
            int processors,
            double processCpuLoad,
            double systemCpuLoad,
            double loadAverage,
            long heapUsed, long heapCommitted, long heapMax, long nonHeapUsed,
            int teams, int members, int onlineMembers,
            long dataFolderBytes, long fileCount, long diskFree, long diskTotal,
            long taskRuns, long taskMillis
    ) {}

    /** Wrap a plugin scheduled task so its CPU cost is attributed and reported. */
    public static Runnable measured(Runnable task) {
        return () -> {
            long start = System.nanoTime();
            try {
                task.run();
            } finally {
                taskNanos.addAndGet(System.nanoTime() - start);
                taskRuns.incrementAndGet();
            }
        };
    }

    public static void capture(NewGenTeamPlugin plugin, Consumer<Snapshot> done) {
        Schedulers.async(() -> done.accept(sample(plugin)));
    }

    private static Snapshot sample(NewGenTeamPlugin plugin) {
        int processors = Runtime.getRuntime().availableProcessors();
        double processCpu = -1, systemCpu = -1, loadAvg = -1;
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sun) {
            processCpu = sun.getProcessCpuLoad();
            systemCpu = sun.getSystemCpuLoad();
        }
        loadAvg = osBean.getSystemLoadAverage();

        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();

        int teams = 0, members = 0, online = 0;
        try {
            var all = plugin.teams().all();
            teams = all.size();
            for (var t : all) {
                members += t.size();
            }
            online = countOnline(plugin);
        } catch (Exception ignored) {
        }

        long[] disk = diskUsage(plugin.getDataFolder());
        File root = plugin.getDataFolder();
        long free = 0, total = 0;
        try {
            free = root.getFreeSpace();
            total = root.getTotalSpace();
        } catch (Exception ignored) {
        }

        return new Snapshot(
                processors, processCpu, systemCpu, loadAvg,
                heap.getUsed(), heap.getCommitted(), heap.getMax(), nonHeap.getUsed(),
                teams, members, online,
                disk[0], disk[1], free, total,
                taskRuns.get(), taskNanos.get() / 1_000_000L
        );
    }

    private static int countOnline(NewGenTeamPlugin plugin) {
        int online = 0;
        for (var p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (plugin.teams().byPlayer(p.getUniqueId()) != null) online++;
        }
        return online;
    }

    /** Returns {bytes, fileCount}, cached for DISK_TTL_MS. */
    private static long[] diskUsage(File folder) {
        long now = System.currentTimeMillis();
        if (now - diskCachedAt < DISK_TTL_MS) {
            return new long[]{diskBytes, diskFiles};
        }
        long bytes = 0, files = 0;
        Path root = folder.toPath();
        if (Files.exists(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    try {
                        if (Files.isRegularFile(p)) {
                            bytes += Files.size(p);
                            files++;
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
        diskBytes = bytes;
        diskFiles = files;
        diskCachedAt = now;
        return new long[]{bytes, files};
    }

    public static String mb(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f", bytes / 1048576.0);
    }

    public static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", mb);
        return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    public static String percent(double load) {
        if (load < 0) return "N/A";
        return String.format(java.util.Locale.ROOT, "%.1f%%", load * 100.0);
    }
}
