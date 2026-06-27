package com.codex.vaultop_tournaments.mixins;

import com.vaultop.mod.VaultOPMod;
import com.vaultop.mod.gui.AuthScreen;
import com.vaultop.mod.gui.HomeScreen;
import com.vaultop.mod.gui.TrophyButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        ButtonWidget singleplayerButton = null;

        // Iterate through children to find the Singleplayer button
        for (Object child : this.children()) {
            if (child instanceof ButtonWidget button) {
                if (button.getMessage() != null && 
                    button.getMessage().getString().equals(Text.translatable("menu.singleplayer").getString())) {
                    singleplayerButton = button;
                    break;
                }
            }
        }

        int x, y;
        if (singleplayerButton != null) {
            // Position on the right side attached to the singleplayer button with 4px padding
            x = singleplayerButton.getX() + singleplayerButton.getWidth() + 4;
            y = singleplayerButton.getY();
        } else {
            // Fallback to center-aligned coordinates
            x = this.width / 2 + 104;
            y = this.height / 4 + 48;
        }

        TrophyButtonWidget vaultopButton = new TrophyButtonWidget(x, y, button -> {
            if (VaultOPMod.getInstance().getSessionManager().isAuthenticated()) {
                this.client.setScreen(new HomeScreen(this));
            } else {
                this.client.setScreen(new AuthScreen(this));
            }
        });

        this.addDrawableChild(vaultopButton);
    }
}
