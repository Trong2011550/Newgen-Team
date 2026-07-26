package me.newgen.team.service;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.model.AuditAction;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.model.TeamRole;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AdminService {

    private final NewGenTeamPlugin plugin;

    public AdminService(NewGenTeamPlugin plugin) {
        this.plugin = plugin;
    }

    public record AdminResult(boolean ok, String message) {
        public static AdminResult ok(String msg) { return new AdminResult(true, msg); }
        public static AdminResult fail(String msg) { return new AdminResult(false, msg); }
    }

    private AuditLogService logs() { return plugin.auditLogs(); }

    private static String name(UUID uuid) {
        if (uuid == null) return null;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName();
    }

    public AdminResult rename(Team team, UUID staff, String staffName, String newName) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        String old = team.name();
        TeamService.Result r = plugin.teams().renameTeam(team, newName);
        return switch (r) {
            case SUCCESS -> {
                logs().record(team, staff, staffName, AuditAction.RENAME_TEAM,
                        null, null, old + " → " + team.name());
                yield AdminResult.ok("Đã đổi tên team thành " + team.name() + ".");
            }
            case NAME_TAKEN -> AdminResult.fail("Tên team này đã có người dùng.");
            default -> AdminResult.fail("Tên không hợp lệ (3-16 ký tự).");
        };
    }

    public AdminResult changeTag(Team team, UUID staff, String staffName, String newTag) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        if (newTag == null || newTag.isBlank()) return AdminResult.fail("Tag không hợp lệ.");
        newTag = newTag.trim();
        if (newTag.length() > plugin.config().maxTagLength()) {
            return AdminResult.fail("Tag quá dài (tối đa " + plugin.config().maxTagLength() + " ký tự).");
        }
        for (int i = 0; i < newTag.length(); i++) {
            char c = newTag.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '\u00A7') return AdminResult.fail("Tag chứa ký tự không hợp lệ.");
        }
        String old = team.tag();
        team.tag(newTag);
        plugin.teams().persist(team);
        logs().record(team, staff, staffName, AuditAction.CHANGE_TAG, null, null, old + " → " + newTag);
        return AdminResult.ok("Đã đổi tag team thành " + newTag + ".");
    }

    public AdminResult forceLeader(Team team, UUID staff, String staffName, UUID newLeader) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        TeamMember target = team.member(newLeader);
        if (target == null) return AdminResult.fail("Người chơi đó không thuộc team này.");
        if (team.leader().equals(newLeader)) return AdminResult.fail("Người chơi đó đã là chủ team.");
        TeamMember old = team.member(team.leader());
        if (old != null) old.role(TeamRole.CO_OWNER);
        target.role(TeamRole.OWNER);
        team.leader(newLeader);
        plugin.teams().persist(team);
        logs().record(team, staff, staffName, AuditAction.CHANGE_LEADER,
                newLeader, target.name(), "Chuyển quyền (admin)");
        return AdminResult.ok("Đã đặt " + target.name() + " làm chủ team.");
    }

    public AdminResult setRole(Team team, UUID staff, String staffName, UUID target, TeamRole newRole) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        TeamMember m = team.member(target);
        if (m == null) return AdminResult.fail("Người chơi đó không thuộc team này.");
        if (newRole == TeamRole.OWNER) {
            return AdminResult.fail("Dùng nút Chuyển quyền chủ team để đặt OWNER.");
        }
        if (target.equals(team.leader())) {
            return AdminResult.fail("Không thể đổi chức vụ của chủ team. Hãy chuyển quyền trước.");
        }
        TeamRole old = m.role();
        if (old == newRole) return AdminResult.fail("Thành viên đã ở chức vụ này.");
        boolean promote = newRole.weight() > old.weight();
        m.role(newRole);
        plugin.teams().persist(team);
        logs().record(team, staff, staffName,
                promote ? AuditAction.PROMOTE : AuditAction.DEMOTE,
                target, m.name(), old.name() + " → " + newRole.name());
        return AdminResult.ok("Đã đặt chức vụ của " + m.name() + " thành " + newRole.name() + ".");
    }

    public AdminResult kick(Team team, UUID staff, String staffName, UUID target) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        TeamMember m = team.member(target);
        if (m == null) return AdminResult.fail("Người chơi đó không thuộc team này.");
        if (target.equals(team.leader())) {
            return AdminResult.fail("Không thể đuổi chủ team. Hãy chuyển quyền hoặc giải tán.");
        }
        String memberName = m.name();
        team.removeMember(target);
        plugin.teams().removeMemberIndex(target);
        plugin.chat().clear(target);
        plugin.teams().persist(team);
        logs().record(team, staff, staffName, AuditAction.KICK, target, memberName, "Đuổi (admin)");
        return AdminResult.ok("Đã đuổi " + memberName + " khỏi team.");
    }

    public AdminResult addMember(Team team, UUID staff, String staffName, UUID target, String targetName) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        if (target == null) return AdminResult.fail("Không tìm thấy người chơi.");
        if (team.isMember(target)) return AdminResult.fail("Người chơi đó đã ở trong team.");
        if (plugin.teams().hasTeam(target)) return AdminResult.fail("Người chơi đó đã ở trong một team khác.");
        if (team.size() >= plugin.teams().memberLimit(team)) return AdminResult.fail("Team đã đầy thành viên.");
        team.addMember(new TeamMember(target, targetName, TeamRole.RECRUIT, System.currentTimeMillis()));
        plugin.teams().putMemberIndex(target, team.id());
        plugin.teams().persist(team);
        logs().record(team, staff, staffName, AuditAction.ADD_MEMBER, target, targetName, "Thêm (admin)");
        return AdminResult.ok("Đã thêm " + targetName + " vào team.");
    }

    public AdminResult removeAllMembers(Team team, UUID staff, String staffName) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        List<UUID> toRemove = new ArrayList<>();
        for (TeamMember m : team.members()) {
            if (!m.uuid().equals(team.leader())) toRemove.add(m.uuid());
        }
        if (toRemove.isEmpty()) return AdminResult.fail("Team không có thành viên nào ngoài chủ team.");
        for (UUID id : toRemove) {
            team.removeMember(id);
            plugin.teams().removeMemberIndex(id);
            plugin.chat().clear(id);
        }
        plugin.teams().persist(team);
        logs().record(team, staff, staffName, AuditAction.REMOVE_MEMBER, null, null,
                "Xóa " + toRemove.size() + " thành viên (admin)");
        return AdminResult.ok("Đã xóa " + toRemove.size() + " thành viên khỏi team.");
    }

    public AdminResult disband(Team team, UUID staff, String staffName) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        String teamName = team.name();
        UUID teamId = team.id();

        logs().record(team, staff, staffName, AuditAction.DISBAND_TEAM, null, null, "Giải tán (admin)");
        plugin.chat().notifyTeam(team, "notify.disbanded", "player", staffName == null ? "Admin" : staffName);
        plugin.teams().disbandTeam(team);
        return AdminResult.ok("Đã giải tán team " + teamName + ".");
    }

    public AdminResult adjustBank(Team team, UUID staff, String staffName, long delta) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        if (delta == 0) return AdminResult.fail("Số tiền phải khác 0.");
        long current;
        long updated;
        // Same lock as BankMenu's deposit/withdraw path.
        team.chestLock.lock();
        try {
            current = team.balance();
            updated = current + delta;
            if (delta > 0 && updated < current) {
                return AdminResult.fail("Số tiền vượt giới hạn số dư.");
            }
            if (updated < 0) return AdminResult.fail("Quỹ team không đủ để trừ (hiện có $" + current + ").");
            team.balance(updated);
        } finally {
            team.chestLock.unlock();
        }
        plugin.teams().persist(team);
        logs().record(team, staff, staffName,
                delta > 0 ? AuditAction.BANK_DEPOSIT : AuditAction.BANK_WITHDRAW,
                null, null, (delta > 0 ? "+" : "") + delta + " → $" + updated + " (admin)");
        return AdminResult.ok("Quỹ team: $" + current + " → $" + updated + ".");
    }

    public AdminResult setBank(Team team, UUID staff, String staffName, long amount) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        if (amount < 0) return AdminResult.fail("Số dư không thể âm.");
        long old;
        team.chestLock.lock();
        try {
            old = team.balance();
            team.balance(amount);
        } finally {
            team.chestLock.unlock();
        }
        plugin.teams().persist(team);
        logs().record(team, staff, staffName, AuditAction.BANK_SET, null, null,
                "$" + old + " → $" + amount + " (admin)");
        return AdminResult.ok("Đã đặt quỹ team thành $" + amount + ".");
    }

    public AdminResult resetChest(Team team, UUID staff, String staffName) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        plugin.chests().resetChest(team);
        logs().record(team, staff, staffName, AuditAction.CHEST_RESET, null, null, "Reset rương (admin)");
        return AdminResult.ok("Đã reset rương team " + team.name() + ".");
    }

    public AdminResult setHome(Team team, UUID staff, String staffName, Location loc) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        if (loc == null || loc.getWorld() == null) return AdminResult.fail("Vị trí không hợp lệ.");
        // Admin override: not subject to the tier home limit.
        plugin.homes().setHome(team, loc, Integer.MAX_VALUE);
        logs().record(team, staff, staffName, AuditAction.HOME_SET, null, null,
                "Đặt nhà tại " + loc.getWorld().getName() + " (admin)");
        return AdminResult.ok("Đã đặt nhà team tại vị trí của bạn.");
    }

    public AdminResult clearHome(Team team, UUID staff, String staffName) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        if (team.home() == null && team.namedHomes().isEmpty()) {
            return AdminResult.fail("Team chưa đặt nhà nào.");
        }
        team.home(null);
        team.namedHomes().clear();
        plugin.teams().persist(team);
        logs().record(team, staff, staffName, AuditAction.HOME_DELETE, null, null, "Xóa toàn bộ nhà (admin)");
        return AdminResult.ok("Đã xóa nhà team.");
    }

    public AdminResult setAlly(Team a, Team b, UUID staff, String staffName) {
        if (a == null || b == null) return AdminResult.fail("Team không tồn tại.");
        if (a.id().equals(b.id())) return AdminResult.fail("Không thể đặt quan hệ với chính team đó.");
        a.relations().put(b.id(), Team.RelationType.ALLY);
        b.relations().put(a.id(), Team.RelationType.ALLY);
        plugin.teams().persist(a);
        plugin.teams().persist(b);
        logs().record(a, staff, staffName, AuditAction.RELATION_ALLY, b.id(), b.name(), "Liên minh (admin)");
        return AdminResult.ok("Đã đặt " + a.name() + " và " + b.name() + " thành liên minh.");
    }

    public AdminResult setEnemy(Team a, Team b, UUID staff, String staffName) {
        if (a == null || b == null) return AdminResult.fail("Team không tồn tại.");
        if (a.id().equals(b.id())) return AdminResult.fail("Không thể đặt quan hệ với chính team đó.");

        b.relations().remove(a.id());
        a.relations().put(b.id(), Team.RelationType.ENEMY);
        plugin.teams().persist(a);
        plugin.teams().persist(b);
        logs().record(a, staff, staffName, AuditAction.RELATION_ENEMY, b.id(), b.name(), "Kẻ thù (admin)");
        return AdminResult.ok("Đã đặt " + b.name() + " là kẻ thù của " + a.name() + ".");
    }

    public AdminResult setNeutral(Team a, Team b, UUID staff, String staffName) {
        if (a == null || b == null) return AdminResult.fail("Team không tồn tại.");
        a.relations().remove(b.id());
        b.relations().remove(a.id());
        plugin.teams().persist(a);
        plugin.teams().persist(b);
        logs().record(a, staff, staffName, AuditAction.RELATION_NEUTRAL, b.id(), b.name(), "Trung lập (admin)");
        return AdminResult.ok("Đã đặt quan hệ giữa " + a.name() + " và " + b.name() + " thành trung lập.");
    }

    public AdminResult setTier(Team team, UUID staff, String staffName, int newTier) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        int maxTier = plugin.config().tierConfig().maxLevel();
        if (newTier < 1 || newTier > maxTier) {
            return AdminResult.fail("Cấp phải từ 1 đến " + maxTier + ".");
        }
        int old = team.tier();
        if (old == newTier) return AdminResult.fail("Team đã ở cấp này.");
        team.tier(newTier);
        plugin.teams().persist(team);

        plugin.chests().invalidate(team);
        logs().record(team, staff, staffName, AuditAction.TIER_CHANGE, null, null,
                "Cấp " + old + " → " + newTier + " (admin)");
        return AdminResult.ok("Đã đặt cấp team thành " + newTier + ".");
    }

    public AdminResult toggleSetting(Team team, UUID staff, String staffName, String settingKey, boolean value) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        var s = team.settings();
        switch (settingKey) {
            case "open" -> s.open = value;
            case "friendlyFire" -> s.friendlyFire = value;
            case "teamChat" -> s.teamChat = value;
            case "autoAcceptInvite" -> s.autoAcceptInvite = value;
            case "publicJoin" -> s.publicJoin = value;
            case "notifyJoinLeave" -> s.notifyJoinLeave = value;
            case "allyRequests" -> s.allyRequests = value;
            default -> { return AdminResult.fail("Cài đặt không xác định: " + settingKey); }
        }
        plugin.teams().persist(team);
        logs().record(team, staff, staffName, AuditAction.EDIT_SETTINGS, null, null,
                settingKey + " = " + value + " (admin)");
        return AdminResult.ok("Đã cập nhật cài đặt " + settingKey + ".");
    }

    public AdminResult resetData(Team team, UUID staff, String staffName) {
        if (team == null) return AdminResult.fail("Team không tồn tại.");
        team.kills(0);
        team.deaths(0);
        team.balance(0);
        team.home(null);
        team.namedHomes().clear();

        for (UUID otherId : new ArrayList<>(team.relations().keySet())) {
            Team other = plugin.teams().byId(otherId);
            if (other != null) {
                other.relations().remove(team.id());
                plugin.teams().persist(other);
            }
        }
        team.relations().clear();
        plugin.chests().resetChest(team);
        plugin.teams().persist(team);

        logs().purge(team.id()).whenComplete((v, ex) ->
                logs().record(team, staff, staffName, AuditAction.RESET_DATA, null, null,
                        "Reset toàn bộ dữ liệu (admin)"));
        return AdminResult.ok("Đã reset dữ liệu team " + team.name() + " (giữ lại thành viên).");
    }
}
