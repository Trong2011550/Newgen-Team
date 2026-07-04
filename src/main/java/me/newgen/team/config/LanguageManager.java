package me.newgen.team.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads lang/&lt;code&gt;.yml selected via {@code language} in config.yml.
 * Missing keys fall back to en_US (bundled default). All values are raw
 * MiniMessage strings with theme tokens; coloring is applied later by
 * MessageManager via ThemeManager.
 */
public final class LanguageManager {

    public static final String FALLBACK = "en_US";
    private static final String[] BUNDLED = {"vi_VN", "en_US"};

    private final Plugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, String> fallback = new HashMap<>();
    private String language = FALLBACK;

    public LanguageManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(String languageCode) {
        this.language = languageCode == null || languageCode.isBlank() ? FALLBACK : languageCode.trim();
        messages.clear();
        fallback.clear();

        // Ship bundled language files so admins can edit them.
        for (String code : BUNDLED) {
            File f = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
            if (!f.exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }

        loadInto(fallback, FALLBACK);
        if (language.equals(FALLBACK)) {
            messages.putAll(fallback);
        } else {
            loadInto(messages, language);
            if (messages.isEmpty()) {
                plugin.getLogger().warning("Language file lang/" + language + ".yml not found or empty - falling back to " + FALLBACK + ".");
            }
        }
    }

    private void loadInto(Map<String, String> target, String code) {
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        FileConfiguration cfg;
        if (file.exists()) {
            cfg = YamlConfiguration.loadConfiguration(file);
        } else {
            InputStream in = plugin.getResource("lang/" + code + ".yml");
            if (in == null) return;
            cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        // Merge bundled defaults so newly added keys appear without wiping admin edits.
        InputStream def = plugin.getResource("lang/" + code + ".yml");
        if (def != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
            cfg.options().copyDefaults(true);
        }
        for (String key : cfg.getKeys(true)) {
            if (cfg.isString(key)) {
                target.put(key, cfg.getString(key));
            }
        }
    }

    /** Raw string for a key: selected language first, then en_US fallback. */
    public String raw(String key) {
        String value = messages.get(key);
        if (value == null) value = fallback.get(key);
        return value != null ? value : "<red>missing: " + key;
    }

    public boolean has(String key) {
        return messages.containsKey(key) || fallback.containsKey(key);
    }

    public String language() {
        return language;
    }
}
