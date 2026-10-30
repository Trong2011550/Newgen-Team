package me.newgen.team.command;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.model.Team;
import me.newgen.team.util.ResourceMetrics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Root plugin command: /newgenteam (aliases /newgen, /ngen, /ngt).
 *
 * - status : OP-only live resource report (CPU / RAM / disk) - see {@link ResourceMetrics}
 * - info   : public server-wide team statistics
 * - reload : admin config reload
 * - version: build info
 */
public final class NewGenCommand implements CommandExecutor, TabCompleter {

    private final NewGenTeamPlugin plugin;

    private static final List<String> SUBS = Arrays.asList(
            "status", "info", "reload", "version", "help");

    public NewGenCommand(NewGenTeamPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "status" -> handleStatus(sender);
            case "info" -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            case "version" -> handleVersion(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.isOp() && !sender.hasPermission("newgenteam.status")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        // Disk walk + MXBean reads happen off-thread; the report is sent when ready.
        ResourceMetrics.capture(plugin, s -> {
            plugin.messages().sendRaw(sender, "command.status.header");
            plugin.messages().sendRaw(sender, "command.status.cpu",
                    "proc", ResourceMetrics.percent(s.processCpuLoad()),
                    "sys", ResourceMetrics.percent(s.systemCpuLoad()),
                    "cores", s.processors(),
                    "load", s.loadAverage() < 0 ? "N/A"
                            : String.format(java.util.Locale.ROOT, "%.2f", s.loadAverage()));
            plugin.messages().sendRaw(sender, "command.status.memory",
                    "used", ResourceMetrics.mb(s.heapUsed()),
                    "committed", ResourceMetrics.mb(s.heapCommitted()),
                    "max", s.heapMax() <= 0 ? "N/A" : ResourceMetrics.mb(s.heapMax()),
                    "nonheap", ResourceMetrics.mb(s.nonHeapUsed()));
            plugin.messages().sendRaw(sender, "command.status.disk",
                    "plugindisk", ResourceMetrics.humanBytes(s.dataFolderBytes()),
                    "files", s.fileCount(),
                    "free", ResourceMetrics.humanBytes(s.diskFree()),
                    "total", ResourceMetrics.humanBytes(s.diskTotal()));
            plugin.messages().sendRaw(sender, "command.status.objects",
                    "teams", s.teams(), "members", s.members(), "online", s.onlineMembers());
            plugin.messages().sendRaw(sender, "command.status.tasks",
                    "runs", s.taskRuns(), "millis", s.taskMillis());
            plugin.messages().sendRaw(sender, "command.status.note");
        });
    }

    private void handleInfo(CommandSender sender) {
        var all = plugin.teams().all();
        int teams = all.size();
        int members = 0;
        Team largest = null;
        for (Team t : all) {
            int size = t.size();
            members += size;
            if (largest == null || size > largest.size()) largest = t;
        }
        int online = 0;
        for (var p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (plugin.teams().byPlayer(p.getUniqueId()) != null) online++;
        }
        String avg = teams == 0 ? "0"
                : String.format(java.util.Locale.ROOT, "%.1f", (double) members / teams);

        plugin.messages().sendRaw(sender, "command.info.header");
        plugin.messages().sendRaw(sender, "command.info.teams", "teams", teams);
        plugin.messages().sendRaw(sender, "command.info.members",
                "members", members, "online", online);
        plugin.messages().sendRaw(sender, "command.info.avg", "avg", avg);
        if (largest != null) {
            plugin.messages().sendRaw(sender, "command.info.largest",
                    "name", largest.name(), "size", largest.size());
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("newgenteam.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        plugin.reload();
        plugin.logs().admin("RELOAD by " + sender.getName());
        plugin.messages().send(sender, "general.reloaded");
    }

    private void handleVersion(CommandSender sender) {
        plugin.messages().sendRaw(sender, "command.version",
                "version", plugin.getPluginMeta().getVersion(),
                "api", plugin.getServer().getMinecraftVersion());
    }

    private void sendHelp(CommandSender sender) {
        plugin.messages().sendRaw(sender, "command.newgen-help");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String p = args[0].toLowerCase();
            for (String s : SUBS) {
                if (s.startsWith(p)) {
                    if (s.equals("status") && !sender.isOp()
                            && !sender.hasPermission("newgenteam.status")) continue;
                    if (s.equals("reload") && !sender.hasPermission("newgenteam.admin")) continue;
                    out.add(s);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }
}
