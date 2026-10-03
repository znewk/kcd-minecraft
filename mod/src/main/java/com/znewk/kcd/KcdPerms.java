package com.znewk.kcd;

import net.minecraft.commands.CommandSourceStack;

/** Кто может пользоваться командами хоста: оператор или владелец мира (хост отряда), даже без включённых читов. */
public final class KcdPerms {
    private KcdPerms() {}

    public static boolean host(CommandSourceStack s) {
        return s.hasPermission(2) || s.getPlayer() != null && s.getServer().isSingleplayerOwner(s.getPlayer().getGameProfile());
    }
}
