package com.codex.vaultop_tournaments.mixins;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void onTick(boolean bl, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.world != null && client.player != null) {
            if (com.vaultop.mod.protectedmode.ProtectedModeManager.enforceResourcePacks) {
                java.util.List<String> violatingPacks = com.vaultop.mod.protectedmode.ProtectedModeManager.getViolatingResourcePacks();
                if (!violatingPacks.isEmpty()) {
                    net.minecraft.client.gui.screen.Screen current = client.currentScreen;
                    if (current == null || (
                        !(current instanceof com.vaultop.mod.gui.ResourcePackViolationScreen) &&
                        !(current instanceof net.minecraft.client.gui.screen.option.OptionsScreen) &&
                        !(current instanceof net.minecraft.client.gui.screen.pack.PackScreen)
                    )) {
                        client.execute(() -> {
                            client.setScreen(new com.vaultop.mod.gui.ResourcePackViolationScreen(violatingPacks));
                        });
                    }
                }
            }
        } else {
            // Automatically clear flag when not connected to a server/world
            com.vaultop.mod.protectedmode.ProtectedModeManager.enforceResourcePacks = false;
        }
    }
}
