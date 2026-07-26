package me.newgen.team.storage;

import me.newgen.team.model.AuditAction;
import me.newgen.team.model.AuditLog;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.model.TeamRole;
import me.newgen.team.model.TeamSettings;
import me.newgen.team.util.Json;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC storage backed by HikariCP.
 * Supports SQLITE, MYSQL, MARIADB and POSTGRESQL, selected via database.yml.
 * MySQL and MariaDB both use the shaded MariaDB driver (jdbc:mariadb://).
 */
public final class JdbcStorage implements StorageProvider {

    private final Logger log;
    private final File dataFolder;
    private final DatabaseSettings settings;
    private HikariDataSource ds;

    /**
     * Dedicated storage thread: usable during onDisable (unlike the Bukkit
     * scheduler), keeps writes ordered, and is drained before the pool closes.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NewGenTeam-Storage");
        t.setDaemon(false);
        return t;
    });

    public JdbcStorage(Logger log, File dataFolder, DatabaseSettings settings) {
        this.log = log;
        this.dataFolder = dataFolder;
        this.settings = settings;
    }

    private void submit(CompletableFuture<?> future, Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
    }

    private boolean isMySqlFamily() {
        return settings.type() == DatabaseSettings.Type.MYSQL
                || settings.type() == DatabaseSettings.Type.MARIADB;
    }

    @Override
    public CompletableFuture<Void> init() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        submit(future, () -> {
            try {
                HikariConfig cfg = new HikariConfig();
                cfg.setPoolName("NewGenTeam-" + settings.type().name());
                cfg.setConnectionTimeout(settings.connectionTimeoutMs());
                switch (settings.type()) {
                    case SQLITE -> {
                        File dbFile = new File(dataFolder, settings.file());
                        if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();
                        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                        // SQLite allows a single writer; a bigger pool only causes lock contention.
                        cfg.setMaximumPoolSize(1);
                        cfg.addDataSourceProperty("journal_mode", "WAL");
                        cfg.addDataSourceProperty("synchronous", "NORMAL");
                    }
                    case MYSQL, MARIADB -> {
                        cfg.setJdbcUrl("jdbc:mariadb://" + settings.host() + ":" + settings.port()
                                + "/" + settings.name());
                        cfg.setUsername(settings.username());
                        cfg.setPassword(settings.password());
                        cfg.setMaximumPoolSize(settings.poolSize());
                        cfg.addDataSourceProperty("useUnicode", "true");
                        cfg.addDataSourceProperty("characterEncoding", "utf8");
                    }
                    case POSTGRESQL -> {
                        cfg.setJdbcUrl("jdbc:postgresql://" + settings.host() + ":" + settings.port()
                                + "/" + settings.name());
                        cfg.setUsername(settings.username());
                        cfg.setPassword(settings.password());
                        cfg.setMaximumPoolSize(settings.poolSize());
                    }
                }
                this.ds = new HikariDataSource(cfg);
                createTables();
                future.complete(null);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to init " + settings.type() + " storage", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void createTables() throws SQLException {
        String blob = isMySqlFamily() ? "LONGBLOB"
                : settings.type() == DatabaseSettings.Type.POSTGRESQL ? "BYTEA" : "BLOB";
        String logId = isMySqlFamily() ? "BIGINT AUTO_INCREMENT PRIMARY KEY"
                : settings.type() == DatabaseSettings.Type.POSTGRESQL ? "BIGSERIAL PRIMARY KEY"
                : "INTEGER PRIMARY KEY AUTOINCREMENT";

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS teams ("
                    + "id VARCHAR(36) PRIMARY KEY,"
                    + "name VARCHAR(64) NOT NULL,"
                    + "tag VARCHAR(64),"
                    + "leader_uuid VARCHAR(36) NOT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "tier INTEGER NOT NULL DEFAULT 1,"
                    + "settings_json TEXT,"
                    + "home_json TEXT,"
                    + "named_homes_json TEXT,"
                    + "kills INTEGER NOT NULL DEFAULT 0,"
                    + "deaths INTEGER NOT NULL DEFAULT 0,"
                    + "balance BIGINT NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS team_members ("
                    + "team_id VARCHAR(36) NOT NULL,"
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "player_name VARCHAR(16),"
                    + "role VARCHAR(32) NOT NULL,"
                    + "joined_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (team_id, player_uuid))");
            st.execute("CREATE TABLE IF NOT EXISTS team_chests ("
                    + "team_id VARCHAR(36) PRIMARY KEY,"
                    + "contents " + blob + ","
                    + "updated_at BIGINT)");
            st.execute("CREATE TABLE IF NOT EXISTS team_relations ("
                    + "team_id VARCHAR(36) NOT NULL,"
                    + "other_team_id VARCHAR(36) NOT NULL,"
                    + "type VARCHAR(16) NOT NULL,"
                    + "PRIMARY KEY (team_id, other_team_id))");
            st.execute("CREATE TABLE IF NOT EXISTS team_logs ("
                    + "id " + logId + ","
                    + "ts BIGINT NOT NULL,"
                    + "team_id VARCHAR(36) NOT NULL,"
                    + "team_name VARCHAR(64),"
                    + "actor_uuid VARCHAR(36),"
                    + "actor_name VARCHAR(16),"
                    + "action VARCHAR(32) NOT NULL,"
                    + "target_uuid VARCHAR(36),"
                    + "target_name VARCHAR(16),"
                    + "extra TEXT)");
            createIndex(st, "idx_member_player", "team_members", "player_uuid");
            createIndex(st, "idx_logs_team", "team_logs", "team_id, id DESC");
        }
    }

    /** MySQL/MariaDB lack CREATE INDEX IF NOT EXISTS, so duplicates are swallowed. */
    private void createIndex(Statement st, String name, String table, String cols) {
        try {
            if (isMySqlFamily()) {
                st.execute("CREATE INDEX " + name + " ON " + table + "(" + cols + ")");
            } else {
                st.execute("CREATE INDEX IF NOT EXISTS " + name + " ON " + table + "(" + cols + ")");
            }
        } catch (SQLException e) {
            // 1061 = duplicate index name, expected on restart.
            if (!isMySqlFamily() || e.getErrorCode() != 1061) {
                log.log(Level.WARNING, "Could not create index " + name + " on " + table, e);
            }
        }
    }

    private String upsertTeamSql() {
        String insert = "INSERT INTO teams (id,name,tag,leader_uuid,created_at,tier,"
                + "settings_json,home_json,named_homes_json,kills,deaths,balance) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ";
        if (isMySqlFamily()) {
            return insert + "ON DUPLICATE KEY UPDATE name=VALUES(name), tag=VALUES(tag), "
                    + "leader_uuid=VALUES(leader_uuid), tier=VALUES(tier), "
                    + "settings_json=VALUES(settings_json), home_json=VALUES(home_json), "
                    + "named_homes_json=VALUES(named_homes_json), kills=VALUES(kills), "
                    + "deaths=VALUES(deaths), balance=VALUES(balance)";
        }
        return insert + "ON CONFLICT(id) DO UPDATE SET name=excluded.name, tag=excluded.tag, "
                + "leader_uuid=excluded.leader_uuid, tier=excluded.tier, "
                + "settings_json=excluded.settings_json, home_json=excluded.home_json, "
                + "named_homes_json=excluded.named_homes_json, kills=excluded.kills, "
                + "deaths=excluded.deaths, balance=excluded.balance";
    }

    private String upsertChestSql() {
        String insert = "INSERT INTO team_chests (team_id,contents,updated_at) VALUES (?,?,?) ";
        if (isMySqlFamily()) {
            return insert + "ON DUPLICATE KEY UPDATE contents=VALUES(contents), "
                    + "updated_at=VALUES(updated_at)";
        }
        return insert + "ON CONFLICT(team_id) DO UPDATE SET contents=excluded.contents, "
                + "updated_at=excluded.updated_at";
    }

    @Override
    public CompletableFuture<Collection<Team>> loadAllTeams() {
        CompletableFuture<Collection<Team>> future = new CompletableFuture<>();
        submit(future, () -> {
            try (Connection c = ds.getConnection()) {
                Map<UUID, Team> teams = new HashMap<>();
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM teams")) {
                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString("id"));
                        TeamSettings settings = Json.readSettings(rs.getString("settings_json"));
                        Team t = new Team(
                                id,
                                rs.getString("name"),
                                rs.getString("tag"),
                                UUID.fromString(rs.getString("leader_uuid")),
                                rs.getLong("created_at"),
                                settings,
                                Math.max(1, rs.getInt("tier")));
                        t.kills(rs.getInt("kills"));
                        t.deaths(rs.getInt("deaths"));
                        t.balance(rs.getLong("balance"));
                        String homeJson = rs.getString("home_json");
                        if (homeJson != null && !homeJson.isEmpty()) {
                            t.home(Json.readHome(homeJson));
                        }
                        String namedJson = rs.getString("named_homes_json");
                        if (namedJson != null && !namedJson.isEmpty()) {
                            t.namedHomes().putAll(Json.readNamedHomes(namedJson));
                        }
                        if (t.home() != null && t.namedHomes().isEmpty()) {
                            t.namedHomes().put(me.newgen.team.service.HomeService.DEFAULT_HOME_KEY, t.home());
                        }
                        teams.put(id, t);
                    }
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM team_members")) {
                    while (rs.next()) {
                        UUID tid = UUID.fromString(rs.getString("team_id"));
                        Team t = teams.get(tid);
                        if (t == null) continue;
                        t.addMember(new TeamMember(
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("player_name"),
                                parseRole(rs.getString("role")),
                                rs.getLong("joined_at")));
                    }
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM team_relations")) {
                    while (rs.next()) {
                        UUID tid = UUID.fromString(rs.getString("team_id"));
                        Team t = teams.get(tid);
                        if (t == null) continue;
                        Team.RelationType rel = parseRelation(rs.getString("type"));
                        if (rel == null) continue;
                        t.relations().put(
                                UUID.fromString(rs.getString("other_team_id")), rel);
                    }
                }
                future.complete(teams.values());
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to load teams", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> saveTeam(Team team) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        final String name = team.name();
        final String tag = team.tag();
        final String leader = team.leader().toString();
        final long createdAt = team.createdAt();
        final int tier = team.tier();
        final String settingsJson = Json.writeSettings(team.settings());
        final String homeJson = team.home() == null ? null : Json.writeHome(team.home());
        final String namedHomesJson = Json.writeNamedHomes(team.namedHomes());
        final int kills = team.kills();
        final int deaths = team.deaths();
        final long balance = team.balance();
        final String teamId = team.id().toString();
        final List<TeamMember> memberSnapshot = new ArrayList<>(team.members());
        final Map<UUID, Team.RelationType> relSnapshot = new HashMap<>(team.relations());

        submit(future, () -> {
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = c.prepareStatement(upsertTeamSql())) {
                        ps.setString(1, teamId);
                        ps.setString(2, name);
                        ps.setString(3, tag);
                        ps.setString(4, leader);
                        ps.setLong(5, createdAt);
                        ps.setInt(6, tier);
                        ps.setString(7, settingsJson);
                        ps.setString(8, homeJson);
                        ps.setString(9, namedHomesJson);
                        ps.setInt(10, kills);
                        ps.setInt(11, deaths);
                        ps.setLong(12, balance);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement del = c.prepareStatement(
                            "DELETE FROM team_members WHERE team_id=?")) {
                        del.setString(1, teamId);
                        del.executeUpdate();
                    }
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO team_members (team_id,player_uuid,player_name,role,joined_at) "
                                    + "VALUES (?,?,?,?,?)")) {
                        for (TeamMember m : memberSnapshot) {
                            ins.setString(1, teamId);
                            ins.setString(2, m.uuid().toString());
                            ins.setString(3, m.name());
                            ins.setString(4, m.role().name());
                            ins.setLong(5, m.joinedAt());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }

                    try (PreparedStatement del = c.prepareStatement(
                            "DELETE FROM team_relations WHERE team_id=?")) {
                        del.setString(1, teamId);
                        del.executeUpdate();
                    }
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO team_relations (team_id,other_team_id,type) VALUES (?,?,?)")) {
                        for (Map.Entry<UUID, Team.RelationType> e : relSnapshot.entrySet()) {
                            ins.setString(1, teamId);
                            ins.setString(2, e.getKey().toString());
                            ins.setString(3, e.getValue().name());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                    c.commit();
                    future.complete(null);
                } catch (Exception e) {
                    // setAutoCommit(true) would commit an open transaction.
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to save team " + teamId, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteTeam(UUID teamId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        final String id = teamId.toString();
        submit(future, () -> {
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                try {
                    for (String table : new String[]{"team_members", "team_chests", "team_relations"}) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "DELETE FROM " + table + " WHERE team_id=?")) {
                            ps.setString(1, id);
                            ps.executeUpdate();
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM teams WHERE id=?")) {
                        ps.setString(1, id);
                        ps.executeUpdate();
                    }
                    c.commit();
                    future.complete(null);
                } catch (Exception e) {
                    // setAutoCommit(true) would commit an open transaction.
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to delete team " + id, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<byte[]> loadChest(UUID teamId) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        final String id = teamId.toString();
        submit(future, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT contents FROM team_chests WHERE team_id=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    future.complete(rs.next() ? rs.getBytes("contents") : null);
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to load chest " + id, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> saveChest(UUID teamId, byte[] contents) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        final String id = teamId.toString();
        submit(future, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(upsertChestSql())) {
                ps.setString(1, id);
                ps.setBytes(2, contents);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
                future.complete(null);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to save chest " + id, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> saveLog(AuditLog entry) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        final long ts = entry.timestamp();
        final String teamId = entry.teamId() == null ? null : entry.teamId().toString();
        final String teamName = entry.teamName();
        final String actor = entry.actor() == null ? null : entry.actor().toString();
        final String actorName = entry.actorName();
        final String action = entry.action() == null
                ? AuditAction.UNKNOWN.name() : entry.action().name();
        final String target = entry.target() == null ? null : entry.target().toString();
        final String targetName = entry.targetName();
        final String extra = entry.extra();
        submit(future, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO team_logs "
                                 + "(ts,team_id,team_name,actor_uuid,actor_name,action,target_uuid,target_name,extra) "
                                 + "VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, ts);
                ps.setString(2, teamId);
                ps.setString(3, teamName);
                ps.setString(4, actor);
                ps.setString(5, actorName);
                ps.setString(6, action);
                ps.setString(7, target);
                ps.setString(8, targetName);
                ps.setString(9, extra);
                ps.executeUpdate();
                future.complete(null);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to save audit log for team " + teamId, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<List<AuditLog>> loadLogs(UUID teamId, int limit) {
        CompletableFuture<List<AuditLog>> future = new CompletableFuture<>();
        final String id = teamId.toString();
        final int cap = Math.max(1, Math.min(limit, 5000));
        submit(future, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT * FROM team_logs WHERE team_id=? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, id);
                ps.setInt(2, cap);
                List<AuditLog> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new AuditLog(
                                rs.getLong("id"),
                                rs.getLong("ts"),
                                parseUuid(rs.getString("team_id")),
                                rs.getString("team_name"),
                                parseUuid(rs.getString("actor_uuid")),
                                rs.getString("actor_name"),
                                AuditAction.parse(rs.getString("action")),
                                parseUuid(rs.getString("target_uuid")),
                                rs.getString("target_name"),
                                rs.getString("extra")));
                    }
                }
                future.complete(out);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to load audit logs for team " + id, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteLogs(UUID teamId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        final String id = teamId.toString();
        submit(future, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM team_logs WHERE team_id=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
                future.complete(null);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to delete audit logs for team " + id, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Unknown role values (downgrade, manual edit) fall back to MEMBER instead of failing the whole load. */
    private TeamRole parseRole(String s) {
        try {
            return TeamRole.valueOf(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warning("Unknown team role '" + s + "' in team_members; defaulting to MEMBER.");
            return TeamRole.MEMBER;
        }
    }

    /** Unknown relation types are skipped instead of failing the whole load. */
    private Team.RelationType parseRelation(String s) {
        try {
            return Team.RelationType.valueOf(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warning("Unknown relation type '" + s + "' in team_relations; skipping row.");
            return null;
        }
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warning("Storage executor did not drain within 30s; forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (ds != null && !ds.isClosed()) ds.close();
    }
}
