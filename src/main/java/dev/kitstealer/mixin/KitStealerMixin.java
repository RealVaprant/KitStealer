package dev.kitstealer.mixin;

import dev.kitstealer.KitStealerClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public final class KitStealerMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void kitStealer$handleTick(CallbackInfo callbackInfo) {
        KitStealerClient.tick((Minecraft) (Object) this);
    }
}
