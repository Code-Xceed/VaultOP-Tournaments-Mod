package com.codex.vaultop_tournaments.mixins;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void init(boolean bl, CallbackInfo ci) {
        System.out.println("runTick mixin!");
    }
}
