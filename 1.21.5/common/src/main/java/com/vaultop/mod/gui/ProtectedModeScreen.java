package com.vaultop.mod.gui;

import com.vaultop.mod.protectedmode.ProtectedModeManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class ProtectedModeScreen extends Screen {
    private final Screen parent;
    private final String tournamentId;
    private final List<ProtectedModeManager.ModInfo> violatingMods;
    private final List<String> violatingPacks;
    private String statusText = "";

    public ProtectedModeScreen(Screen parent, String tournamentId, List<ProtectedModeManager.ModInfo> violatingMods, List<String> violatingPacks) {
        super(Text.literal("Protected Mode Violation"));
        this.parent = parent;
        this.tournamentId = tournamentId;
        this.violatingMods = violatingMods;
        this.violatingPacks = violatingPacks;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height - 40;

        this.addDrawableChild(new PremiumButtonWidget(x, y, 98, 20, Text.literal("Re-Verify"), button -> reVerify(), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        this.addDrawableChild(new PremiumButtonWidget(x + 102, y, 98, 20, Text.literal("Cancel"), button -> close(), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));
    }

    private void reVerify() {
        statusText = "Re-verifying environment...";
        ProtectedModeManager.verify(tournamentId).thenAccept(result -> {
            this.client.execute(() -> {
                if (result.compliant) {
                    statusText = "Environment compliant! Returning...";
                    this.client.setScreen(this.parent);
                } else {
                    statusText = "Still non-compliant.";
                    this.client.setScreen(new ProtectedModeScreen(this.parent, tournamentId, result.violatingMods, result.violatingPacks));
                }
            });
        }).exceptionally(ex -> {
            this.client.execute(() -> {
                statusText = "Verification failed: " + ex.getMessage();
            });
            return null;
        });
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier bgTex = Identifier.of("vaultop", "textures/gui/mod_bg_image.png");
        context.drawTexture(RenderLayer::getGuiTextured, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.draw();
        context.fill(0, 0, this.width, this.height, 0x77440000);
        context.draw();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("PROTECTED MODE VIOLATION"), this.width / 2, 15, 0xFF5555);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Unapproved mods or custom resource packs detected."), this.width / 2, 30, 0xFFAAAAAA);

        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), this.width / 2, 45, 0xFFFF55);
        }

        int currentY = 65;

        if (!violatingMods.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Unapproved Mods Found:"), this.width / 2, currentY, 0xFFFFFF);
            currentY += 12;
            for (int i = 0; i < Math.min(violatingMods.size(), 5); i++) {
                ProtectedModeManager.ModInfo mod = violatingMods.get(i);
                String display = mod.id + " (" + mod.filename + ")";
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(display), this.width / 2, currentY, 0xFF5555);
                currentY += 12;
            }
            if (violatingMods.size() > 5) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("... and " + (violatingMods.size() - 5) + " more mods"), this.width / 2, currentY, 0xFFAAAAAA);
                currentY += 12;
            }
            currentY += 5;
        }

        if (!violatingPacks.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Custom Resource Packs Not Allowed:"), this.width / 2, currentY, 0xFFFFFF);
            currentY += 12;
            for (int i = 0; i < Math.min(violatingPacks.size(), 5); i++) {
                String packName = violatingPacks.get(i);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(packName), this.width / 2, currentY, 0xFF5555);
                currentY += 12;
            }
            if (violatingPacks.size() > 5) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("... and " + (violatingPacks.size() - 5) + " more packs"), this.width / 2, currentY, 0xFFAAAAAA);
                currentY += 12;
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
