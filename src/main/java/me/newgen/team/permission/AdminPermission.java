package me.newgen.team.permission;

import org.bukkit.permissions.Permissible;

public enum AdminPermission {

    ADMIN("newgenteam.admin"),

    VIEW("newgenteam.admin.view"),

    SEARCH("newgenteam.admin.search"),

    EDIT("newgenteam.admin.edit"),

    DELETE("newgenteam.admin.delete"),

    LOGS("newgenteam.admin.logs"),

    BANK("newgenteam.admin.bank"),

    HOME("newgenteam.admin.home"),

    CHEST("newgenteam.admin.chest"),

    RELATION("newgenteam.admin.relation");

    private final String node;

    AdminPermission(String node) {
        this.node = node;
    }

    public String node() {
        return node;
    }

    public boolean held(Permissible who) {
        if (who == null) return false;
        if (this == ADMIN) return who.hasPermission(node);
        return who.hasPermission(node) || who.hasPermission(ADMIN.node);
    }
}
