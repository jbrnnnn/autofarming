package com.example.autofarm.mixin;

import com.example.autofarm.AutoFarmClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mencegah singleplayer ter-pause saat ESC / menu terbuka selama auto farm aktif. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "shouldPause", at = @At("HEAD"), cancellable = true)
    private void autofarm$keepWorldRunning(CallbackInfoReturnable<Boolean> cir) {
        if (AutoFarmClient.keepRunning()) {
            cir.setReturnValue(false);
        }
    }
}
