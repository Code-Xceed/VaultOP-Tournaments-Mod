package com.vaultop.mod.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class HomeScreen extends Screen {
    private final Screen parent;

    public HomeScreen(Screen parent) {
        super(Text.literal("VaultOP Home"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        
        // Stacked buttons in the bottom-left corner with proper padding and margin
        int x = 20;
        int startY = this.height - 80;
        
        this.addDrawableChild(new PremiumButtonWidget(x, startY, buttonWidth, buttonHeight, Text.literal("Tournaments"), button -> {
            this.client.setScreen(new TournamentListScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        this.addDrawableChild(new PremiumButtonWidget(x, startY + 24, buttonWidth, buttonHeight, Text.literal("Profile"), button -> {
            this.client.setScreen(new ProfileScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        this.addDrawableChild(new PremiumButtonWidget(x, startY + 48, buttonWidth, buttonHeight, Text.literal("Leaderboard"), button -> {
            this.client.setScreen(new LeaderboardScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        // Back button in top-left
        this.addDrawableChild(new PremiumButtonWidget(10, 10, 50, 20, Text.literal("Back"), button -> {
            this.client.setScreen(this.parent);
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier bgTex = Identifier.of("vaultop", "textures/gui/mod_bg_image.png");
        context.drawTexture(RenderLayer::getGuiTextured, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.fill(0, 0, this.width, this.height, 0x80050505);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // Title banner
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("VaultOP Tournament Dashboard"), this.width / 2, 20, 0xFFFFFF);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
