package com.znewk.kcd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.storage.PrimaryLevelData;

/** То же при загрузке: мир KCD не спрашивает «сделать резервную копию — экспериментальные настройки?». */
@Mixin(PrimaryLevelData.class)
public abstract class ConfirmedWarningMixin {
    @Inject(method = "hasConfirmedExperimentalWarning", at = @At("HEAD"), cancellable = true)
    private void kcd$confirmed(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
