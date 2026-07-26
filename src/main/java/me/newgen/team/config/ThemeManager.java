package me.newgen.team.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads theme.yml and resolves theme tokens (<primary>, <danger>, ...)
 * into real MiniMessage tags before parsing. Changing a color in theme.yml
 * re-skins every message/GUI text that uses the tokens.
 */
public final class ThemeManager {

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("primary", "<#b7f0c0>");
        DEFAULTS.put("secondary", "<#8fd6a0>");
        DEFAULTS.put("accent", "<#d9f7e0>");
        DEFAULTS.put("muted", "<#a3b8a9>");
        DEFAULTS.put("danger", "<red>");
        DEFAULTS.put("success", "<green>");
        DEFAULTS.put("warning", "<yellow>");
        DEFAULTS.put("info", "<aqua>");
    }

    private final Plugin plugin;
    private final Map<String, String> tokens = new LinkedHashMap<>(DEFAULTS);

    public ThemeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "theme.yml");
        if (!file.exists()) {
            plugin.saveResource("theme.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : DEFAULTS.keySet()) {
            String value = cfg.getString("theme." + key, DEFAULTS.get(key));
            tokens.put(key, value == null || value.isBlank() ? DEFAULTS.get(key) : value.trim());
        }
    }

    /** Replaces theme tokens (e.g. {@code <primary>}) with configured MiniMessage tags. */
    public String apply(String input) {
        if (input == null || input.isEmpty()) return input;
        String out = input;
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            out = out.replace("<" + e.getKey() + ">", e.getValue());
        }
        return out;
    }

    /** Raw MiniMessage tag configured for a theme key (e.g. "primary"). */
    public String color(String key) {
        return tokens.getOrDefault(key, "");
    }
}
