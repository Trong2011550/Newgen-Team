package me.newgen.team.service;

import me.newgen.team.config.MessageManager;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatService {

    private final TeamService teams;
    private final MessageManager messages;

    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    private volatile String format = "&d[%tag%] &7%player%&8: &f%message%";
    private volatile boolean logToConsole = true;

    public ChatService(TeamService teams, MessageManager messages) {
        this.teams = teams;
        this.messages = messages;
    }

    public void setFormat(String format) {
        if (format != null && !format.isEmpty()) this.format = format;
    }

    public void setLogToConsole(boolean logToConsole) {
        this.logToConsole = logToConsole;
    }

    public boolean isToggled(UUID player) {
        return toggled.contains(player);
    }

    public boolean toggle(UUID player) {
        if (toggled.contains(player)) {
            toggled.remove(player);
            return false;
        }
        toggled.add(player);
        return true;
    }

    public void clear(UUID player) {
        toggled.remove(player);
    }

    public boolean sendTeamMessage(Player sender, String rawMessage) {
        Team team = teams.byPlayer(sender.getUniqueId());
        if (team == null) return false;
        if (!team.settings().teamChat) {
            messages.send(sender, "chat.team-disabled");
            return true;
        }
        Component out = Text.legacy(format
                .replace("%tag%", team.tag())
                .replace("%team%", team.name())
                .replace("%player%", sender.getName())
                // Player input must not inject '&' colour codes into the template.
                .replace("%message%", stripFormatting(rawMessage)));
        for (TeamMember m : team.members()) {
            Player p = Bukkit.getPlayer(m.uuid());
            if (p != null && p.isOnline()) {
                p.sendMessage(out);
            }
        }
        if (logToConsole) {
            Bukkit.getConsoleSender().sendMessage(out);
        }
        return true;
    }

    /** Removes legacy '&' and '§' colour codes so chat input cannot restyle the line. */
    private static String stripFormatting(String message) {
        if (message == null || message.isEmpty()) return message;
        StringBuilder sb = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '\u00A7') {
                i++; // skip the code char after §
                continue;
            }
            if (c == '&' && i + 1 < message.length()
                    && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(message.charAt(i + 1)) >= 0) {
                i++; // skip the code char after &
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public void notifyTeam(Team team, String messageKey, Object... placeholders) {
        if (team == null) return;
        for (TeamMember m : team.members()) {
            Player p = Bukkit.getPlayer(m.uuid());
            if (p != null && p.isOnline()) {
                messages.send(p, messageKey, placeholders);
            }
        }
    }
}
