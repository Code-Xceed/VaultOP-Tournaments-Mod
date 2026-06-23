package com.vaultop.mod.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.nio.file.Path;
import java.util.List;

public class ResourcePackBlockScreen extends Screen {
    private final List<String> violatingPacks;

    public ResourcePackBlockScreen(List<String> violatingPacks) {
        super(Text.literal("Resource Packs Enforced"));
        this.violatingPacks = violatingPacks;
    }

    public List<String> getViolatingPacks() {
        return violatingPacks;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height - 40;

        // Button to open resource pack screen
        this.addDrawableChild(new PremiumButtonWidget(x, y, 98, 20, Text.literal("Open Packs"), button -> {
            Path packPath = this.client.runDirectory.toPath().resolve("resourcepacks");
            this.client.setScreen(new PackScreen(
                this.client.getResourcePackManager(),
                manager -> this.client.setScreen(this),
                packPath,
                Text.translatable("resourcePack.title")
            ));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        // Disconnect button
        this.addDrawableChild(new PremiumButtonWidget(x + 102, y, 98, 20, Text.literal("Disconnect"), button -> {
            if (this.client.world != null) {
                this.client.world.disconnect();
            }
            this.client.disconnect();
        }, 0xFFD32F2F, 0xFF5C0E0E, 0xFFFF7961));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier bgTex = Identifier.of("vaultop", "textures/gui/mod_bg_image.png");
        context.drawTexture(RenderLayer::getGuiTextured, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.draw();
        context.fill(0, 0, this.width, this.height, 0x88330000);
        context.draw();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("RESOURCE PACKS RESTRICTED"), this.width / 2, 25, 0xFF5555);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("This tournament restricts resource packs to Vanilla only."), this.width / 2, 45, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Please disable all custom resource packs to continue playing."), this.width / 2, 60, 0xAAAAAA);

        int currentY = 90;
        if (!violatingPacks.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Active Custom Resource Packs:"), this.width / 2, currentY, 0xFFFFFF);
            currentY += 15;
            for (int i = 0; i < Math.min(violatingPacks.size(), 8); i++) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("- " + violatingPacks.get(i)), this.width / 2, currentY, 0xFF5555);
                currentY += 12;
            }
            if (violatingPacks.size() > 8) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("... and " + (violatingPacks.size() - 8) + " more packs"), this.width / 2, currentY, 0xAAAAAA);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }
}
