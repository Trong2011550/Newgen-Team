package me.newgen.team.model;

import me.newgen.team.permission.TeamPermission;
import java.util.Collections;
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
        // Switch on the enum constant (not ordinal): adding or reordering roles can no
        // longer silently fall through to a thrown MatchException on every permission check.
        EnumSet<TeamPermission> s = switch (this) {
            case OWNER -> EnumSet.allOf(TeamPermission.class);
            case CO_OWNER -> {
                EnumSet<TeamPermission> set = EnumSet.allOf(TeamPermission.class);
                set.remove(TeamPermission.DISBAND_TEAM);
                set.remove(TeamPermission.TRANSFER_LEADER);
                yield set;
            }
            case ADMIN -> EnumSet.of(TeamPermission.INVITE_MEMBER, TeamPermission.KICK_MEMBER,
                    TeamPermission.REVIEW_INVITES, TeamPermission.REVIEW_JOIN_REQUESTS,
                    TeamPermission.EDIT_SETTINGS, TeamPermission.CHEST_OPEN, TeamPermission.CHEST_DEPOSIT,
                    TeamPermission.CHEST_WITHDRAW, TeamPermission.CHEST_UPGRADE, TeamPermission.SET_HOME,
                    TeamPermission.TELEPORT_HOME, TeamPermission.MANAGE_RELATIONS,
                    TeamPermission.TOGGLE_TEAM_CHAT, TeamPermission.MANAGE_MEMBERS,
                    TeamPermission.WITHDRAW_BALANCE);
            case MOD -> EnumSet.of(TeamPermission.INVITE_MEMBER, TeamPermission.KICK_MEMBER,
                    TeamPermission.REVIEW_INVITES, TeamPermission.REVIEW_JOIN_REQUESTS,
                    TeamPermission.CHEST_OPEN, TeamPermission.CHEST_DEPOSIT, TeamPermission.CHEST_WITHDRAW,
                    TeamPermission.TELEPORT_HOME, TeamPermission.TOGGLE_TEAM_CHAT,
                    TeamPermission.WITHDRAW_BALANCE);
            case MEMBER -> EnumSet.of(TeamPermission.CHEST_OPEN, TeamPermission.CHEST_DEPOSIT,
                    TeamPermission.CHEST_WITHDRAW, TeamPermission.TELEPORT_HOME,
                    TeamPermission.TOGGLE_TEAM_CHAT);
            case RECRUIT -> EnumSet.of(TeamPermission.CHEST_OPEN, TeamPermission.CHEST_DEPOSIT,
                    TeamPermission.TELEPORT_HOME);
        };
        // Unmodifiable: PermissionManager caches these sets, so a caller mutating the
        // returned EnumSet would corrupt the role defaults for the whole session.
        return Collections.unmodifiableSet(s);
    }
}
