package com.znewk.kcd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.serialization.Lifecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;

/**
 * Без предупреждения «Используются экспериментальные возможности» при создании мира: Minecraft показывает его для
 * любого мира со своим генератором (наш мир KCD), а пугать игроков незачем.
 */
@Mixin(WorldOpenFlows.class)
public abstract class ExperimentalWarningMixin {
    @Inject(method = "confirmWorldCreation", at = @At("HEAD"), cancellable = true)
    private static void kcd$skipWarning(Minecraft minecraft, CreateWorldScreen screen, Lifecycle lifecycle, Runnable loadWorld,
                                        boolean skipWarnings, CallbackInfo ci) {
        loadWorld.run();
        ci.cancel();
    }
}
