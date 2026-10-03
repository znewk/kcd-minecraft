package com.znewk.kcd.party;

/** Роль игрока в отряде. Индржих — один на прохождение, остальные — его братья. */
public enum Role {
    HENRY,
    BROTHER;

    public static Role byName(String name) {
        for (Role r : values()) if (r.name().equalsIgnoreCase(name)) return r;
        return BROTHER;
    }
}
