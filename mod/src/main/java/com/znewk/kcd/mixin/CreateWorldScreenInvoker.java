package com.znewk.kcd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

/** Доступ к созданию мира без нажатия кнопки — для «Нового прохождения» KCD. */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenInvoker {
    @Invoker("onCreate")
    void kcd$onCreate();
}
