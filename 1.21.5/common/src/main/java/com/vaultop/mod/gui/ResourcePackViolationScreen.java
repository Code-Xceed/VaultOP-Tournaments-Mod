package com.vaultop.mod.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class ResourcePackViolationScreen extends Screen {
    private final List<String> violatingPacks;

    public ResourcePackViolationScreen(List<String> violatingPacks) {
        super(Text.literal("Resource Pack Restriction"));
        this.violatingPacks = violatingPacks;
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int spacing = 10;
        int totalWidth = (buttonWidth * 2) + spacing;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height - 50;

        // "Options..." button to let them remove packs
        this.addDrawableChild(new PremiumButtonWidget(
            startX, y, buttonWidth, buttonHeight, 
            Text.literal("Options..."), 
            button -> this.client.setScreen(new OptionsScreen(this, this.client.options)), 
            0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3
        ));

        // "Disconnect" button
        this.addDrawableChild(new PremiumButtonWidget(
            startX + buttonWidth + spacing, y, buttonWidth, buttonHeight, 
            Text.literal("Disconnect"), 
            button -> {
                if (this.client.world != null) {
                    this.client.world.disconnect();
                }
                this.client.disconnect();
                this.client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
            }, 
            0xFF3C464F, 0xFF0C0C0C, 0xFFF44336
        ));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Red tinted dark overlay to show warning
        Identifier bgTex = Identifier.of("vaultop", "textures/gui/mod_bg_image.png");
        context.drawTexture(RenderLayer::getGuiTextured, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.draw();
        context.fill(0, 0, this.width, this.height, 0x88550000); // 50% opacity red color overlay
        context.draw();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("UNAUTHORIZED RESOURCE PACKS DETECTED"), this.width / 2, 20, 0xFF5555);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Please remove all custom resource packs (e.g. Xray, cheats) to play."), this.width / 2, 40, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Only vanilla/built-in resource packs are permitted on this server."), this.width / 2, 52, 0xFFAAAAAA);

        int currentY = 85;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Violating Resource Packs:"), this.width / 2, currentY, 0xFFFFFF);
        currentY += 15;

        for (int i = 0; i < Math.min(violatingPacks.size(), 8); i++) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("- " + violatingPacks.get(i)), this.width / 2, currentY, 0xFF5555);
            currentY += 12;
        }

        if (violatingPacks.size() > 8) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("... and " + (violatingPacks.size() - 8) + " more packs"), this.width / 2, currentY, 0xFFAAAAAA);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        // Pressing ESC goes to the Options Screen directly so they can disable the pack
        this.client.setScreen(new OptionsScreen(this, this.client.options));
    }
}
