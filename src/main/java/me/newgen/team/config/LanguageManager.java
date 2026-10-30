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
    // Volatile immutable snapshots: load() runs on the main thread while raw()/has()
    // are read from AsyncChatEvent (netty) threads. Swapping whole maps avoids the
    // ConcurrentModificationException / torn reads a live HashMap would cause on reload.
    private volatile Map<String, String> messages = java.util.Collections.emptyMap();
    private volatile Map<String, String> fallback = java.util.Collections.emptyMap();
    private volatile String language = FALLBACK;

    public LanguageManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(String languageCode) {
        String code = languageCode == null || languageCode.isBlank() ? FALLBACK : languageCode.trim();

        // Ship bundled language files so admins can edit them.
        for (String bundled : BUNDLED) {
            File f = new File(plugin.getDataFolder(), "lang/" + bundled + ".yml");
            if (!f.exists()) {
                plugin.saveResource("lang/" + bundled + ".yml", false);
            }
        }

        Map<String, String> fb = new HashMap<>();
        loadInto(fb, FALLBACK);
        Map<String, String> msg;
        if (code.equals(FALLBACK)) {
            msg = new HashMap<>(fb);
        } else {
            msg = new HashMap<>();
            loadInto(msg, code);
            if (msg.isEmpty()) {
                plugin.getLogger().warning("Language file lang/" + code + ".yml not found or empty - falling back to " + FALLBACK + ".");
            }
        }
        // Publish immutable snapshots atomically.
        this.fallback = java.util.Collections.unmodifiableMap(fb);
        this.messages = java.util.Collections.unmodifiableMap(msg);
        this.language = code;
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
