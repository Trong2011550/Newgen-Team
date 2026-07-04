package me.newgen.team.permission;

import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.model.TeamRole;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PermissionManager {

    private final Map<TeamRole, Set<TeamPermission>> rolePermissions = new EnumMap<>(TeamRole.class);

    public PermissionManager() {
        for (TeamRole role : TeamRole.values()) {
            rolePermissions.put(role, role.defaultPermissions());
        }
    }

    public void setRolePermissions(TeamRole role, Set<TeamPermission> perms) {
        rolePermissions.put(role, perms);
    }

    public Set<TeamPermission> permissionsOf(TeamRole role) {
        return rolePermissions.getOrDefault(role, role.defaultPermissions());
    }

    public boolean has(Team team, UUID player, TeamPermission permission) {
        if (team == null) return false;
        if (team.leader().equals(player)) return true;
        TeamMember member = team.member(player);
        if (member == null) return false;
        return permissionsOf(member.role()).contains(permission);
    }

    public boolean has(Team team, TeamMember member, TeamPermission permission) {
        if (team == null || member == null) return false;
        if (member.role() == TeamRole.OWNER || team.leader().equals(member.uuid())) return true;
        return permissionsOf(member.role()).contains(permission);
    }
}
