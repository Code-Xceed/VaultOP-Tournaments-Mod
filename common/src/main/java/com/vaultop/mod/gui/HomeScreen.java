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
        
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Tournaments"), button -> {
            this.client.setScreen(new TournamentListScreen(this));
        }).dimensions(x, startY, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Profile"), button -> {
            this.client.setScreen(new ProfileScreen(this));
        }).dimensions(x, startY + 24, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Leaderboard"), button -> {
            this.client.setScreen(new LeaderboardScreen(this));
        }).dimensions(x, startY + 48, buttonWidth, buttonHeight).build());

        // Back button in top-left
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(10, 10, 50, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier bgTex = Identifier.of("vaultop", "textures/gui/mod_bg_image.png");
        context.drawTexture(RenderLayer::getGuiTextured, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.fill(0, 0, this.width, this.height, 0xCC050505);
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
