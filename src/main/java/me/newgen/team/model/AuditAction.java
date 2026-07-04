/*
 * Decompiled with CFR 0.152.
 */
package me.newgen.team.model;

public enum AuditAction {
    CREATE_TEAM("T\u1ea1o team"),
    DISBAND_TEAM("Gi\u1ea3i t\u00e1n team"),
    RENAME_TEAM("\u0110\u1ed5i t\u00ean team"),
    CHANGE_TAG("\u0110\u1ed5i tag team"),
    INVITE("M\u1eddi th\u00e0nh vi\u00ean"),
    JOIN("Gia nh\u1eadp team"),
    LEAVE("R\u1eddi team"),
    KICK("\u0110u\u1ed5i th\u00e0nh vi\u00ean"),
    ADD_MEMBER("Th\u00eam th\u00e0nh vi\u00ean"),
    REMOVE_MEMBER("X\u00f3a th\u00e0nh vi\u00ean"),
    CHANGE_LEADER("Chuy\u1ec3n quy\u1ec1n ch\u1ee7 team"),
    PROMOTE("Th\u0103ng ch\u1ee9c"),
    DEMOTE("Gi\u00e1ng ch\u1ee9c"),
    SET_ROLE("\u0110\u1ed5i ch\u1ee9c v\u1ee5"),
    BANK_DEPOSIT("N\u1ea1p qu\u1ef9 team"),
    BANK_WITHDRAW("R\u00fat qu\u1ef9 team"),
    BANK_SET("\u0110\u1eb7t s\u1ed1 d\u01b0 qu\u1ef9"),
    CHEST_RESET("Reset r\u01b0\u01a1ng team"),
    CHEST_UPGRADE("N\u00e2ng c\u1ea5p r\u01b0\u01a1ng"),
    HOME_SET("\u0110\u1eb7t nh\u00e0 team"),
    HOME_DELETE("X\u00f3a nh\u00e0 team"),
    HOME_TELEPORT("D\u1ecbch chuy\u1ec3n v\u1ec1 nh\u00e0"),
    RELATION_ALLY("Li\u00ean minh"),
    RELATION_ENEMY("\u0110\u00e1nh d\u1ea5u k\u1ebb th\u00f9"),
    RELATION_NEUTRAL("Trung l\u1eadp"),
    EDIT_SETTINGS("Ch\u1ec9nh c\u00e0i \u0111\u1eb7t team"),
    TIER_CHANGE("\u0110\u1ed5i c\u1ea5p team"),
    ADMIN_ACTION("Thao t\u00e1c qu\u1ea3n tr\u1ecb"),
    RESET_DATA("Reset d\u1eef li\u1ec7u team"),
    UNKNOWN("Kh\u00f4ng x\u00e1c \u0111\u1ecbnh");

    private final String display;

    private AuditAction(String display) {
        this.display = display;
    }

    public String display() {
        return this.display;
    }

    public boolean isAdmin() {
        return this == ADMIN_ACTION || this == RESET_DATA;
    }

    public static AuditAction parse(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        try {
            return AuditAction.valueOf(name);
        }
        catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
