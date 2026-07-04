package me.newgen.team.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads menu layout overrides from plugins/NewGenTeam/menus/*.yml.
 * Menus keep working with their built-in defaults when a file or key is absent,
 * so server owners only override what they want to change.
 */
public final class MenuLayoutManager {

    /** Layout view for one menu. All getters fall back to the given default. */
    public static final class MenuLayout {
        private final ConfigurationSection root;

        private MenuLayout(ConfigurationSection root) {
            this.root = root;
        }

        public String title(String def) {
            return root == null ? def : root.getString("title", def);
        }

        public int size(int def) {
            if (root == null) return def;
            int rows = root.getInt("rows", def / 9);
            return Math.max(1, Math.min(6, rows)) * 9;
        }

        private ConfigurationSection item(String key) {
            return root == null ? null : root.getConfigurationSection("items." + key);
        }

        public int slot(String key, int def) {
            ConfigurationSection s = item(key);
            return s == null ? def : s.getInt("slot", def);
        }

        public Material material(String key, Material def) {
            ConfigurationSection s = item(key);
            if (s == null) return def;
            Material m = Material.matchMaterial(s.getString("material", ""));
            return m == null ? def : m;
        }

        public boolean glow(String key, boolean def) {
            ConfigurationSection s = item(key);
            return s == null ? def : s.getBoolean("glow", def);
        }

        public int modelData(String key, int def) {
            ConfigurationSection s = item(key);
            return s == null ? def : s.getInt("custom-model-data", def);
        }

        /** true when the item is hidden entirely via enabled: false. */
        public boolean enabled(String key) {
            ConfigurationSection s = item(key);
            return s == null || s.getBoolean("enabled", true);
        }
    }

    private static final MenuLayout EMPTY = new MenuLayout(null);

    private final Plugin plugin;
    private final Map<String, MenuLayout> layouts = new HashMap<>();

    public MenuLayoutManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)loads every menus/*.yml, copying bundled defaults on first run. */
    public void load() {
        layouts.clear();
        File dir = new File(plugin.getDataFolder(), "menus");
        // Copy bundled defaults so admins have a documented template to edit.
        for (String bundled : new String[]{"main"}) {
            if (!new File(dir, bundled + ".yml").exists()) {
                plugin.saveResource("menus/" + bundled + ".yml", false);
            }
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            String name = f.getName().substring(0, f.getName().length() - 4);
            layouts.put(name, new MenuLayout(YamlConfiguration.loadConfiguration(f)));
        }
    }

    /** Layout for the given menu name (e.g. "main"). Never null. */
    public MenuLayout layout(String name) {
        return layouts.getOrDefault(name, EMPTY);
    }
}
