package me.newgen.team.config;

import me.newgen.team.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Facade over LanguageManager (what to say) and ThemeManager (how it looks).
 * All strings are MiniMessage; theme tokens (<primary>, <danger>, ...) are
 * resolved via ThemeManager before parsing. No legacy & / § codes.
 */
public final class MessageManager {

    private final ThemeManager theme;
    private final LanguageManager language;
    private String prefix = "";

    public MessageManager(ThemeManager theme, LanguageManager language) {
        this.theme = theme;
        this.language = language;
    }

    public void load() {
        this.prefix = language.raw("prefix");
    }

    /** Raw MiniMessage string with theme tokens resolved (unparsed). */
    public String raw(String key) {
        return theme.apply(language.raw(key));
    }

    /** True if the key exists in the active language or the en_US fallback. */
    public boolean has(String key) {
        return language.has(key);
    }

    public String format(String key, Object... pairs) {
        String msg = raw(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            msg = msg.replace("%" + pairs[i] + "%", String.valueOf(pairs[i + 1]));
        }
        return msg;
    }

    public Component component(String key, Object... pairs) {
        return Text.mini(format(key, pairs));
    }

    public void send(CommandSender to, String key, Object... pairs) {
        to.sendMessage(Text.mini(theme.apply(prefix) + format(key, pairs)));
    }

    public void sendRaw(CommandSender to, String key, Object... pairs) {
        to.sendMessage(Text.mini(format(key, pairs)));
    }

    public void actionBar(Player to, String key, Object... pairs) {
        to.sendActionBar(Text.mini(format(key, pairs)));
    }

    /** Prefix with theme tokens resolved (unparsed MiniMessage). */
    public String prefix() {
        return theme.apply(prefix);
    }
}
