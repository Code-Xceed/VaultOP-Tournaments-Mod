package com.vaultop.mod.gui;

import com.vaultop.mod.protectedmode.ProtectedModeManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public class ProtectedModeScreen extends Screen {
    private final Screen parent;
    private final String tournamentId;
    private final List<ProtectedModeManager.ModInfo> violatingMods;
    private String statusText = "";

    public ProtectedModeScreen(Screen parent, String tournamentId, List<ProtectedModeManager.ModInfo> violatingMods) {
        super(Text.literal("Protected Mode Violation"));
        this.parent = parent;
        this.tournamentId = tournamentId;
        this.violatingMods = violatingMods;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height - 40;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Re-Verify"), button -> reVerify())
                .dimensions(x, y, 98, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(x + 102, y, 98, 20)
                .build());
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
                    this.client.setScreen(new ProtectedModeScreen(this.parent, tournamentId, result.violatingMods));
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Red tinted dark background
        context.fill(0, 0, this.width, this.height, 0x99440000);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("PROTECTED MODE VIOLATION"), this.width / 2, 20, 0xFF5555);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Unapproved mod environment detected. Tournament server access locked."), this.width / 2, 40, 0xAAAAAA);

        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), this.width / 2, 55, 0xFFFF55);
        }

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Unapproved Mods Found:"), this.width / 2, 80, 0xFFFFFF);

        int startY = 100;
        for (int i = 0; i < Math.min(violatingMods.size(), 8); i++) {
            ProtectedModeManager.ModInfo mod = violatingMods.get(i);
            String display = mod.id + " (" + mod.filename + ")";
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(display), this.width / 2, startY + (i * 12), 0xFF5555);
        }

        if (violatingMods.size() > 8) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("... and " + (violatingMods.size() - 8) + " more mods"), this.width / 2, startY + 96, 0xAAAAAA);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
