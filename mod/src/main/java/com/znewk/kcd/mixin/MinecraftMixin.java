package com.znewk.kcd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.znewk.kcd.KcdMod;

import net.minecraft.client.Minecraft;

/** Заголовок окна игры: «KCD by znewk · v…» вместо «Minecraft NeoForge* 1.21.1». */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void kcd$createTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(KcdMod.windowTitle());
    }
}
