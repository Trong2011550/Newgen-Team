package me.newgen.team.command;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.model.Team;
import me.newgen.team.service.TeamService;
import me.newgen.team.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class TeamCommand implements CommandExecutor, TabCompleter {

    private final NewGenTeamPlugin plugin;

    private static final List<String> SUBS = Arrays.asList(
            "create", "disband", "rename", "invite", "accept", "deny",
            "leave", "kick", "info", "list", "chat", "home", "sethome",
            "top", "admin", "help", "reload");

    public TeamCommand(NewGenTeamPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            plugin.menus().openMain(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!sender.hasPermission("newgenteam.admin")) {
                plugin.messages().send(sender, "general.no-permission");
                return true;
            }
            plugin.reload();
            plugin.logs().admin("RELOAD by " + sender.getName());
            plugin.messages().send(sender, "general.reloaded");
            return true;
        }

        if (sub.equals("list")) {
            handleList(sender);
            return true;
        }

        if (sub.equals("admin")) {
            handleAdmin(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "disband" -> handleDisband(player);
            case "rename" -> handleRename(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player, args);
            case "deny" -> handleDeny(player, args);
            case "leave" -> handleLeave(player);
            case "kick" -> handleKick(player, args);
            case "info" -> handleInfo(player);
            case "chat" -> handleChat(player, args);
            case "home" -> plugin.menus().openHome(player);
            case "sethome" -> handleSetHome(player);
            case "top" -> plugin.menus().openTop(player);
            case "help" -> sendHelp(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleCreate(Player p, String[] args) {
        if (args.length < 2) { plugin.messages().send(p, "command.create-usage"); return; }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        TeamService.Result r = plugin.teams().createTeam(p.getUniqueId(), p.getName(), name, null);
        switch (r) {
            case SUCCESS -> { Sounds.success(p); plugin.messages().send(p, "team.created", "name", name);
                plugin.menus().openMain(p); }
            case ALREADY_IN_TEAM -> plugin.messages().send(p, "team.already-in");
            case NAME_TAKEN -> plugin.messages().send(p, "team.name-taken");
            case NAME_INVALID -> plugin.messages().send(p, "team.name-invalid");
            default -> plugin.messages().send(p, "general.error");
        }
    }

    private void handleDisband(Player p) {
        Team team = plugin.teams().byPlayer(p.getUniqueId());
        if (team == null) { plugin.messages().send(p, "team.not-in"); return; }
        if (!team.leader().equals(p.getUniqueId())) {
            plugin.messages().send(p, "general.no-permission"); return;
        }
        plugin.chat().notifyTeam(team, "notify.disbanded", "player", p.getName());
        plugin.teams().disbandTeam(team);
        plugin.messages().send(p, "team.disbanded");
    }

    private void handleRename(Player p, String[] args) {
        if (args.length < 2) { plugin.messages().send(p, "command.rename-usage"); return; }
        Team team = plugin.teams().byPlayer(p.getUniqueId());
        if (team == null) { plugin.messages().send(p, "team.not-in"); return; }
        if (!plugin.permissions().has(team, p.getUniqueId(),
                me.newgen.team.permission.TeamPermission.RENAME_TEAM)) {
            plugin.messages().send(p, "general.no-permission"); return;
        }
        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        TeamService.Result r = plugin.teams().renameTeam(team, newName);
        if (r == TeamService.Result.SUCCESS) {
            plugin.messages().send(p, "team.renamed", "name", newName);
            plugin.chat().notifyTeam(team, "notify.renamed", "player", p.getName(), "name", newName);
        }
        else if (r == TeamService.Result.NAME_TAKEN) plugin.messages().send(p, "team.name-taken");
        else plugin.messages().send(p, "team.name-invalid");
    }

    private void handleInvite(Player p, String[] args) {
        if (args.length < 2) { plugin.messages().send(p, "command.invite-usage"); return; }
        Team team = plugin.teams().byPlayer(p.getUniqueId());
        if (team == null) { plugin.messages().send(p, "team.not-in"); return; }
        if (!plugin.permissions().has(team, p.getUniqueId(),
                me.newgen.team.permission.TeamPermission.INVITE_MEMBER)) {
            plugin.messages().send(p, "general.no-permission"); return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { plugin.messages().send(p, "general.player-not-found"); return; }
        TeamService.Result r = plugin.teams().invite(team, target.getUniqueId());
        switch (r) {
            case SUCCESS -> { plugin.messages().send(p, "invite.sent", "player", target.getName());
                plugin.messages().send(target, "invite.received", "team", team.name());
                plugin.chat().notifyTeam(team, "notify.invited", "player", p.getName(), "target", target.getName()); }
            case TEAM_FULL -> plugin.messages().send(p, "invite.team-full");
            case TARGET_IN_TEAM -> plugin.messages().send(p, "invite.already-in-team");
            default -> plugin.messages().send(p, "general.error");
        }
    }

    private void handleAccept(Player p, String[] args) {
        var invites = plugin.teams().invitesFor(p.getUniqueId());
        if (invites.isEmpty()) { plugin.messages().send(p, "invite.none"); return; }
        UUID teamId = resolveInviteTarget(args, invites.keySet());
        if (teamId == null) { plugin.messages().send(p, "invite.specify"); return; }
        TeamService.Result r = plugin.teams().acceptInvite(p.getUniqueId(), p.getName(), teamId);
        if (r == TeamService.Result.SUCCESS) {
            Team t = plugin.teams().byId(teamId);
            plugin.messages().send(p, "invite.accepted", "team", t == null ? "?" : t.name());
            if (t != null) plugin.chat().notifyTeam(t, "notify.joined", "player", p.getName());
        } else {
            plugin.messages().send(p, "invite.expired");
        }
    }

    private void handleDeny(Player p, String[] args) {
        var invites = plugin.teams().invitesFor(p.getUniqueId());
        if (invites.isEmpty()) { plugin.messages().send(p, "invite.none"); return; }
        UUID teamId = resolveInviteTarget(args, invites.keySet());
        if (teamId == null) { plugin.messages().send(p, "invite.specify"); return; }
        plugin.teams().declineInvite(p.getUniqueId(), teamId);
        plugin.messages().send(p, "invite.denied");
    }

    private UUID resolveInviteTarget(String[] args, java.util.Set<UUID> options) {
        if (args.length >= 2) {
            // Joined so multi-word team names resolve.
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Team t = plugin.teams().byName(name);
            return (t != null && options.contains(t.id())) ? t.id() : null;
        }
        return options.size() == 1 ? options.iterator().next() : null;
    }

    private void handleLeave(Player p) {
        Team team = plugin.teams().byPlayer(p.getUniqueId());
        if (team == null) { plugin.messages().send(p, "team.not-in"); return; }
        TeamService.Result r = plugin.teams().leave(team, p.getUniqueId());
        if (r == TeamService.Result.SUCCESS) {
            plugin.chat().clear(p.getUniqueId());
            plugin.messages().send(p, "team.left");
            plugin.chat().notifyTeam(team, "notify.left", "player", p.getName());
        } else if (r == TeamService.Result.NOT_LEADER) {
            plugin.messages().send(p, "team.leader-must-transfer");
        }
    }

    private void handleKick(Player p, String[] args) {
        if (args.length < 2) { plugin.messages().send(p, "command.kick-usage"); return; }
        Team team = plugin.teams().byPlayer(p.getUniqueId());
        if (team == null) { plugin.messages().send(p, "team.not-in"); return; }
        if (!plugin.permissions().has(team, p.getUniqueId(),
                me.newgen.team.permission.TeamPermission.KICK_MEMBER)) {
            plugin.messages().send(p, "general.no-permission"); return;
        }
        // Resolved from the roster; getOfflinePlayer(name) can block on a profile lookup.
        java.util.UUID targetId = null;
        for (me.newgen.team.model.TeamMember m : team.members()) {
            if (m.name() != null && m.name().equalsIgnoreCase(args[1])) { targetId = m.uuid(); break; }
        }
        if (targetId == null) { plugin.messages().send(p, "general.player-not-found"); return; }
        final java.util.UUID target = targetId;
        TeamService.Result r = plugin.teams().kick(team, p.getUniqueId(), target);
        switch (r) {
            case SUCCESS -> { plugin.messages().send(p, "member.kicked", "player", args[1]);
                plugin.chat().clear(target);
                plugin.chat().notifyTeam(team, "notify.kicked", "player", p.getName(), "target", args[1]); }
            case NO_PERMISSION -> plugin.messages().send(p, "general.no-permission");
            case CANNOT_TARGET_SELF -> plugin.messages().send(p, "member.cannot-target-self");
            default -> plugin.messages().send(p, "general.player-not-found");
        }
    }

    private void handleInfo(Player p) {
        Team team = plugin.teams().byPlayer(p.getUniqueId());
        if (team == null) { plugin.messages().send(p, "team.not-in"); return; }
        plugin.menus().openStats(p);
    }

    private void handleAdmin(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return;
        }
        if (!me.newgen.team.permission.AdminPermission.VIEW.held(p)) {
            plugin.messages().send(p, "admin.no-permission");
            return;
        }
        new me.newgen.team.gui.menu.admin.AdminMainMenu(plugin, p).open();
    }

    private void handleList(CommandSender sender) {
        var teams = plugin.teams().all();
        plugin.messages().send(sender, "team.list-header", "count", teams.size());
        for (Team t : teams) {
            plugin.messages().sendRaw(sender, "team.list-entry",
                    "name", t.name(), "tag", t.tag(), "members", t.size());
        }
    }

    private void handleChat(Player p, String[] args) {
        Team team = plugin.teams().byPlayer(p.getUniqueId());
        if (team == null) { plugin.messages().send(p, "team.not-in"); return; }
        if (!plugin.permissions().has(team, p.getUniqueId(),
                me.newgen.team.permission.TeamPermission.TOGGLE_TEAM_CHAT)) {
            plugin.messages().send(p, "general.no-permission"); return;
        }
        if (args.length >= 2) {
            String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (!plugin.chat().sendTeamMessage(p, msg)) plugin.messages().send(p, "team.not-in");
            return;
        }
        boolean on = plugin.chat().toggle(p.getUniqueId());
        plugin.messages().send(p, on ? "chat.enabled" : "chat.disabled");
    }

    private void handleSetHome(Player p) {
        Team team = plugin.teams().byPlayer(p.getUniqueId());
        if (team == null) { plugin.messages().send(p, "team.not-in"); return; }
        if (!plugin.permissions().has(team, p.getUniqueId(),
                me.newgen.team.permission.TeamPermission.SET_HOME)) {
            plugin.messages().send(p, "general.no-permission"); return;
        }
        var r = plugin.homes().setHome(team, p.getLocation(), plugin.tiers().homeLimit(team));
        if (r == me.newgen.team.service.HomeService.SetResult.LIMIT_REACHED) {
            plugin.messages().send(p, "home.limit-reached");
            return;
        }
        plugin.messages().send(p, "home.set");
    }

    private void sendHelp(Player p) {
        plugin.messages().sendRaw(p, "command.help");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("kick")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) names.add(p.getName());
                }
                return names;
            }
        }
        return new ArrayList<>();
    }
}
