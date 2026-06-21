package com.vaultop.mod.gui;

import com.google.gson.JsonObject;
import com.vaultop.mod.VaultOPMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ProfileScreen extends Screen {
    private final Screen parent;
    private JsonObject profileData = null;
    private String statusText = "Loading profile...";

    public ProfileScreen(Screen parent) {
        super(Text.literal("VaultOP Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(10, 10, 50, 20)
                .build());

        // Fetch user profile
        String token = VaultOPMod.getInstance().getSessionManager().getSessionToken();
        VaultOPMod.getInstance().getRestClient().fetchProfile(token)
                .thenAccept(data -> {
                    this.client.execute(() -> {
                        this.profileData = data;
                        this.statusText = "";
                    });
                })
                .exceptionally(ex -> {
                    this.client.execute(() -> {
                        this.statusText = "Failed to load profile: " + ex.getMessage();
                    });
                    return null;
                });
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

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("VaultOP User Profile"), this.width / 2, 20, 0xFFFFFF);

        if (profileData == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), this.width / 2, this.height / 2, 0xAAAAAA);
        } else {
            // Extract nested user object
            com.google.gson.JsonObject userObj = profileData.has("user") ? profileData.getAsJsonObject("user") : null;
            String discordUser = "Unknown";
            String ign = "Not Linked";
            String accountType = "PREMIUM";

            if (userObj != null) {
                discordUser = userObj.has("username") ? userObj.get("username").getAsString() : "Unknown";
                ign = userObj.has("ign") ? userObj.get("ign").getAsString() : "Not Linked";
                accountType = userObj.has("accountType") ? userObj.get("accountType").getAsString() : "PREMIUM";
            }

            int x = this.width / 2 - 100;
            int startY = 60;

            context.drawTextWithShadow(this.textRenderer, Text.literal("Discord Username: "), x, startY, 0xAAAAAA);
            context.drawTextWithShadow(this.textRenderer, Text.literal(discordUser), x + 110, startY, 0xFFFFFF);

            context.drawTextWithShadow(this.textRenderer, Text.literal("Minecraft IGN: "), x, startY + 20, 0xAAAAAA);
            context.drawTextWithShadow(this.textRenderer, Text.literal(ign), x + 110, startY + 20, 0x55FF55);

            context.drawTextWithShadow(this.textRenderer, Text.literal("Account Type: "), x, startY + 40, 0xAAAAAA);
            context.drawTextWithShadow(this.textRenderer, Text.literal(accountType.toUpperCase()), x + 110, startY + 40, 0xFFFF55);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
