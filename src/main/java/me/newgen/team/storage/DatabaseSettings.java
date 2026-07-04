package me.newgen.team.storage;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Parsed, validated view of database.yml.
 * Unknown or invalid values fall back to safe SQLite defaults.
 */
public final class DatabaseSettings {

    public enum Type { SQLITE, MYSQL, MARIADB, POSTGRESQL }

    private final Type type;
    private final String file;
    private final String host;
    private final int port;
    private final String name;
    private final String username;
    private final String password;
    private final int poolSize;
    private final long connectionTimeoutMs;

    public DatabaseSettings(FileConfiguration c) {
        Type parsed;
        try {
            parsed = Type.valueOf(c.getString("database.type", "SQLITE").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            parsed = Type.SQLITE;
        }
        this.type = parsed;
        this.file = c.getString("database.file", "newgenteam.db");
        this.host = c.getString("database.host", "localhost");
        this.port = c.getInt("database.port", parsed == Type.POSTGRESQL ? 5432 : 3306);
        this.name = c.getString("database.name", "newgenteam");
        this.username = c.getString("database.username", "root");
        this.password = c.getString("database.password", "");
        this.poolSize = Math.max(1, c.getInt("database.pool.size", 10));
        this.connectionTimeoutMs = Math.max(250, c.getLong("database.pool.connection-timeout", 5000));
    }

    public Type type() { return type; }
    public String file() { return file; }
    public String host() { return host; }
    public int port() { return port; }
    public String name() { return name; }
    public String username() { return username; }
    public String password() { return password; }
    public int poolSize() { return poolSize; }
    public long connectionTimeoutMs() { return connectionTimeoutMs; }
}
