package me.newgen.team.model;

import me.newgen.team.permission.TeamPermission;
import java.util.EnumSet;
import java.util.Set;

public enum TeamRole {
    OWNER(100, "&#8fd6a0&lCh\u1ee7 Team"),
    CO_OWNER(80, "&#b7f0c0Ph\u00f3 Ch\u1ee7"),
    ADMIN(60, "&#d9f7e0Qu\u1ea3n tr\u1ecb"),
    MOD(40, "&#d9f7e0\u0110i\u1ec1u h\u00e0nh"),
    MEMBER(20, "&#a3b8a9Th\u00e0nh vi\u00ean"),
    RECRUIT(10, "&#a3b8a9T\u00e2n binh");

    private final int weight;
    private final String display;

    private TeamRole(int weight, String display) {
        this.weight = weight;
        this.display = display;
    }

    public int weight() {
        return this.weight;
    }

    public String display() {
        return this.display;
    }

    public boolean isAtLeast(TeamRole other) {
        return this.weight >= other.weight;
    }

    public boolean canManage(TeamRole target) {
        return this.weight > target.weight;
    }

    public Set<TeamPermission> defaultPermissions() {
        return switch (this.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> EnumSet.allOf(TeamPermission.class);
            case 1 -> {
                EnumSet<TeamPermission> s = EnumSet.allOf(TeamPermission.class);
                s.remove((Object)TeamPermission.DISBAND_TEAM);
                s.remove((Object)TeamPermission.TRANSFER_LEADER);
                yield s;
            }
            case 2 -> EnumSet.of(TeamPermission.INVITE_MEMBER, new TeamPermission[]{TeamPermission.KICK_MEMBER, TeamPermission.REVIEW_INVITES, TeamPermission.REVIEW_JOIN_REQUESTS, TeamPermission.EDIT_SETTINGS, TeamPermission.CHEST_OPEN, TeamPermission.CHEST_DEPOSIT, TeamPermission.CHEST_WITHDRAW, TeamPermission.CHEST_UPGRADE, TeamPermission.SET_HOME, TeamPermission.TELEPORT_HOME, TeamPermission.MANAGE_RELATIONS, TeamPermission.TOGGLE_TEAM_CHAT, TeamPermission.MANAGE_MEMBERS, TeamPermission.WITHDRAW_BALANCE});
            case 3 -> EnumSet.of(TeamPermission.INVITE_MEMBER, new TeamPermission[]{TeamPermission.KICK_MEMBER, TeamPermission.REVIEW_INVITES, TeamPermission.REVIEW_JOIN_REQUESTS, TeamPermission.CHEST_OPEN, TeamPermission.CHEST_DEPOSIT, TeamPermission.CHEST_WITHDRAW, TeamPermission.TELEPORT_HOME, TeamPermission.TOGGLE_TEAM_CHAT, TeamPermission.WITHDRAW_BALANCE});
            case 4 -> EnumSet.of(TeamPermission.CHEST_OPEN, TeamPermission.CHEST_DEPOSIT, TeamPermission.CHEST_WITHDRAW, TeamPermission.TELEPORT_HOME, TeamPermission.TOGGLE_TEAM_CHAT);
            case 5 -> EnumSet.of(TeamPermission.CHEST_OPEN, TeamPermission.CHEST_DEPOSIT, TeamPermission.TELEPORT_HOME);
        };
    }
}
