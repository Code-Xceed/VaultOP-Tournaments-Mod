package com.vaultop.mod.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vaultop.mod.VaultOPMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ProfileScreen extends Screen {
    private final Screen parent;
    private JsonObject profileData = null;
    private List<JsonObject> registeredEvents = new ArrayList<>();
    private String statusText = "Loading profile...";
    private String eventsLoadingStatus = "Loading registered events...";
    private int rightScrollOffset = 0;

    public ProfileScreen(Screen parent) {
        super(Text.literal("VaultOP Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Back button at top-left
        this.addDrawableChild(new PremiumButtonWidget(15, 12, 50, 20, Text.literal("Back"), button -> close(), 0xFF8B2B2B, 0xFF4A1010, 0xFFE57373));

        String token = VaultOPMod.getInstance().getSessionManager().getSessionToken();
        
        // Fetch profile
        VaultOPMod.getInstance().getRestClient().fetchProfile(token)
                .thenAccept(data -> {
                    this.client.execute(() -> {
                        this.profileData = data;
                        this.statusText = "";
                        
                        // Start downloading skin if IGN is linked
                        com.google.gson.JsonObject userObj = profileData.has("user") ? profileData.getAsJsonObject("user") : null;
                        if (userObj != null && userObj.has("ign") && !userObj.get("ign").isJsonNull()) {
                            String ign = userObj.get("ign").getAsString();
                            String accountType = userObj.has("accountType") && !userObj.get("accountType").isJsonNull() 
                                    ? userObj.get("accountType").getAsString() : "PREMIUM";
                            if (!ign.isEmpty() && !"Not Linked".equalsIgnoreCase(ign)) {
                                String skinUrl = "CRACKED".equalsIgnoreCase(accountType) 
                                        ? "https://skins.ely.by/skins/" + ign + ".png" 
                                        : "https://mc-heads.net/skin/" + ign;
                                DynamicTextureLoader.getOrLoad(skinUrl, "skin_" + ign);
                            }
                        }
                    });
                })
                .exceptionally(ex -> {
                    this.client.execute(() -> {
                        this.statusText = "Failed to load profile.";
                    });
                    return null;
                });

        // Fetch tournaments to filter registered events
        VaultOPMod.getInstance().getRestClient().fetchTournaments()
                .thenAccept(tourneys -> {
                    this.client.execute(() -> {
                        this.registeredEvents.clear();
                        for (int i = 0; i < tourneys.size(); i++) {
                            JsonObject t = tourneys.get(i).getAsJsonObject();
                            if (t.has("userStatusLabel") && !t.get("userStatusLabel").isJsonNull()) {
                                String label = t.get("userStatusLabel").getAsString();
                                if (!"Not Registered".equalsIgnoreCase(label) && !"Ended".equalsIgnoreCase(label)) {
                                    this.registeredEvents.add(t);
                                }
                            }
                        }
                        this.eventsLoadingStatus = this.registeredEvents.isEmpty() ? "No registered events." : "";
                    });
                })
                .exceptionally(ex -> {
                    this.client.execute(() -> {
                        this.eventsLoadingStatus = "Failed to load registered events.";
                    });
                    return null;
                });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelX = 15;
        int panelY = 35;
        int panelW = this.width - 30;
        int panelH = this.height - 50;

        int leftW = (int) (panelW * 0.45) - 5;
        int rightX = panelX + leftW + 20;
        int rightY = panelY + 10;
        int rightW = panelW - leftW - 30;
        int rightH = panelH - 20;

        if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= rightY && mouseY <= rightY + rightH) {
            int scrollAreaH = rightH - 25;
            int totalHeight = registeredEvents.size() * 42;
            int maxScroll = Math.max(0, totalHeight - scrollAreaH);
            if (verticalAmount != 0) {
                rightScrollOffset -= (int) Math.signum(verticalAmount) * 15;
                if (rightScrollOffset < 0) rightScrollOffset = 0;
                if (rightScrollOffset > maxScroll) rightScrollOffset = maxScroll;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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

        int panelX = 15;
        int panelY = 35;
        int panelW = this.width - 30;
        int panelH = this.height - 50;

        // Draw outer premium layout box
        TournamentListScreen.drawPremiumBeveledBox(context, panelX, panelY, panelW, panelH, 0xD00A0E17, 0x302196F3, 0x152196F3);

        if (profileData == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + statusText), this.width / 2, this.height / 2, 0xFFFFFF);
        } else {
            com.google.gson.JsonObject userObj = profileData.has("user") ? profileData.getAsJsonObject("user") : null;
            String discordUser = "Unknown";
            String discordId = "N/A";
            String ign = "Not Linked";
            String accountType = "PREMIUM";
            String role = "COMPETITOR";

            if (userObj != null) {
                discordUser = userObj.has("username") ? userObj.get("username").getAsString() : "Unknown";
                discordId = userObj.has("discordId") ? userObj.get("discordId").getAsString() : "N/A";
                ign = userObj.has("ign") && !userObj.get("ign").isJsonNull() ? userObj.get("ign").getAsString() : "Not Linked";
                accountType = userObj.has("accountType") && !userObj.get("accountType").isJsonNull() ? userObj.get("accountType").getAsString() : "PREMIUM";
                role = userObj.has("role") && !userObj.get("role").isJsonNull() ? userObj.get("role").getAsString() : "COMPETITOR";
            }

            // Left Column layout: Profile Info & Skin Canvas
            int leftX = panelX + 10;
            int leftY = panelY + 10;
            int leftW = (int) (panelW * 0.45) - 5;
            int leftH = panelH - 20;

            TournamentListScreen.drawPremiumBeveledBox(context, leftX, leftY, leftW, leftH, 0x8005080E, 0x20FFFFFF, 0x10FFFFFF);

            // Left Column Title
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§b🏆 PLAYER PROFILE"), leftX + leftW / 2, leftY + 6, 0xFFFFFF);
            context.fill(leftX + 8, leftY + 16, leftX + leftW - 8, leftY + 17, 0x20FFFFFF);

            // Info rows
            int textY = leftY + 22;
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7User: §f" + discordUser), leftX + 12, textY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7Role: " + getRoleBadgeText(role)), leftX + 12, textY + 10, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7Linked IGN: §e" + ign), leftX + 12, textY + 20, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7Security: §7" + accountType.toUpperCase()), leftX + 12, textY + 30, 0xFFFFFF);

            // Skin card container in the lower half of the Left Column
            int skinCardW = 76;
            int skinCardH = 100;
            int skinCardX = leftX + (leftW - skinCardW) / 2;
            int skinCardY = leftY + leftH - skinCardH - 8;

            TournamentListScreen.drawPremiumBeveledBox(context, skinCardX, skinCardY, skinCardW, skinCardH, 0xE50B0C0E, 0x40D7A15C, 0x20D7A15C);

            // Asynchronously resolve and render the skin texture
            Identifier skinTex = null;
            if (!ign.isEmpty() && !"Not Linked".equalsIgnoreCase(ign)) {
                String skinUrl = "CRACKED".equalsIgnoreCase(accountType) 
                        ? "https://skins.ely.by/skins/" + ign + ".png" 
                        : "https://mc-heads.net/skin/" + ign;
                skinTex = DynamicTextureLoader.getOrLoad(skinUrl, "skin_" + ign);
            }
            if (skinTex == null) {
                skinTex = DynamicTextureLoader.getOrLoad("https://mc-heads.net/skin/Steeeve", "skin_steeeve");
            }

            if (skinTex != null) {
                render25DCharacter(context, skinTex, skinCardX + skinCardW / 2, skinCardY + 10, mouseX, mouseY);
            }

            // Right Column layout: Joined Events list
            int rightX = leftX + leftW + 10;
            int rightY = panelY + 10;
            int rightW = panelW - leftW - 30;
            int rightH = panelH - 20;

            TournamentListScreen.drawPremiumBeveledBox(context, rightX, rightY, rightW, rightH, 0x8005080E, 0x20FFFFFF, 0x10FFFFFF);

            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e📅 REGISTERED EVENTS"), rightX + rightW / 2, rightY + 6, 0xFFFFFF);
            context.fill(rightX + 8, rightY + 16, rightX + rightW - 8, rightY + 17, 0x20FFFFFF);

            int scrollAreaH = rightH - 25;
            if (!eventsLoadingStatus.isEmpty()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + eventsLoadingStatus), rightX + rightW / 2, rightY + 22 + scrollAreaH / 2 - 4, 0xFFFFFF);
            } else {
                context.enableScissor(rightX + 2, rightY + 18, rightX + rightW - 2, rightY + rightH - 4);

                int eventY = rightY + 22 - rightScrollOffset;
                int cardW = rightW - 16;

                for (JsonObject event : registeredEvents) {
                    String name = event.has("name") ? event.get("name").getAsString() : "Unnamed Event";
                    String statusLabel = event.has("userStatusLabel") ? event.get("userStatusLabel").getAsString() : "PENDING";
                    String gameVersion = event.has("gameVersion") ? event.get("gameVersion").getAsString() : "1.21.5";

                    int badgeBg = 0x40808080;
                    String formattedBadge = "§7" + statusLabel.toUpperCase();
                    if (statusLabel.toLowerCase().contains("approved")) {
                        badgeBg = 0x6055FF55; // green glow
                        formattedBadge = "§a" + statusLabel.toUpperCase();
                    } else if (statusLabel.toLowerCase().contains("pending")) {
                        badgeBg = 0x60FFFF55; // gold/yellow
                        formattedBadge = "§e" + statusLabel.toUpperCase();
                    } else if (statusLabel.toLowerCase().contains("rejected")) {
                        badgeBg = 0x60FF5555; // red
                        formattedBadge = "§c" + statusLabel.toUpperCase();
                    }

                    // Card background
                    TournamentListScreen.drawPremiumBeveledBox(context, rightX + 8, eventY, cardW, 36, 0x40000000, 0x20FFFFFF, 0x10FFFFFF);

                    // Details inside card
                    context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + name), rightX + 14, eventY + 4, 0xFFFFFF);
                    context.drawTextWithShadow(this.textRenderer, Text.literal("§7Ver: §b" + gameVersion), rightX + 14, eventY + 14, 0xFFFFFF);

                    // Draw status badge
                    int badgeW = this.textRenderer.getWidth(formattedBadge) + 8;
                    int badgeH = 12;
                    int badgeX = rightX + cardW - badgeW;
                    int badgeYInside = eventY + 12;
                    TournamentListScreen.drawPremiumBeveledBox(context, badgeX, badgeYInside, badgeW, badgeH, badgeBg, 0x20FFFFFF, 0x10FFFFFF);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(formattedBadge), badgeX + 4, badgeYInside + 2, 0xFFFFFF);

                    eventY += 42;
                }

                context.disableScissor();

                // Scrollbar
                int totalHeight = registeredEvents.size() * 42;
                int maxScroll = Math.max(0, totalHeight - scrollAreaH);
                if (maxScroll > 0) {
                    int scrollbarX = rightX + rightW - 6;
                    int scrollbarY = rightY + 22;
                    context.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollAreaH, 0x22FFFFFF);

                    int thumbHeight = (scrollAreaH * scrollAreaH) / (scrollAreaH + maxScroll);
                    if (thumbHeight < 10) thumbHeight = 10;
                    int thumbY = scrollbarY + (rightScrollOffset * (scrollAreaH - thumbHeight)) / maxScroll;
                    context.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, 0x88FFFFFF);
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String getRoleBadgeText(String role) {
        if ("DEV".equalsIgnoreCase(role)) return "§bDEVELOPER";
        if ("ADMIN".equalsIgnoreCase(role)) return "§bADMINISTRATOR";
        if ("STAFF".equalsIgnoreCase(role)) return "§eSTAFF";
        return "§aCOMPETITOR";
    }

    private void render25DCharacter(DrawContext context, Identifier skinTex, int centerX, int centerY, int mouseX, int mouseY) {
        float tick = System.currentTimeMillis() / 50.0f;

        // Base coordinate shifts to make it fit inside our skin card
        int tx = centerX - 8;
        int ty = centerY + 18;

        // Walking cycle calculations (limbs swing)
        float cycle = (float) Math.sin(tick * 0.15f);
        int armLegSwingY = (int) (cycle * 4.0f);

        // Head looking tracking calculations
        int headCX = centerX;
        int headCY = centerY + 10;

        double dx = mouseX - headCX;
        double dy = mouseY - headCY;
        double len = Math.sqrt(dx * dx + dy * dy);
        float hox = 0;
        float hoy = 0;
        if (len > 0.1) {
            hox = (float) (dx / len) * Math.min(3.0f, (float) len * 0.03f);
            hoy = (float) (dy / len) * Math.min(2.0f, (float) len * 0.03f);
        }

        // Draw parts step by step (back to front order)

        // 1. Right Leg & Pants Overlay (Base: u=4, v=20; Overlay: u=4, v=36)
        int rly = ty + 24 + Math.abs(armLegSwingY) / 2;
        int rlx = tx - armLegSwingY / 2;
        drawSkinPart(context, skinTex, rlx, rly, 8, 24, 4, 20, 4, 12);
        drawSkinPart(context, skinTex, rlx, rly, 8, 24, 4, 36, 4, 12);

        // 2. Left Leg & Pants Overlay (Base: u=20, v=52; Overlay: u=4, v=52)
        int lly = ty + 24 + Math.abs(armLegSwingY) / 2;
        int llx = tx + 8 + armLegSwingY / 2;
        drawSkinPart(context, skinTex, llx, lly, 8, 24, 20, 52, 4, 12);
        drawSkinPart(context, skinTex, llx, lly, 8, 24, 4, 52, 4, 12);

        // 3. Torso & Jacket Overlay (Base: u=20, v=20; Overlay: u=20, v=36)
        drawSkinPart(context, skinTex, tx, ty, 16, 24, 20, 20, 8, 12);
        drawSkinPart(context, skinTex, tx, ty, 16, 24, 20, 36, 8, 12);

        // 4. Right Arm & Sleeve Overlay (Base: u=44, v=20; Overlay: u=44, v=36)
        int ray = ty + Math.abs(armLegSwingY) / 2;
        int rax = tx - 8 + armLegSwingY / 2;
        drawSkinPart(context, skinTex, rax, ray, 8, 24, 44, 20, 4, 12);
        drawSkinPart(context, skinTex, rax, ray, 8, 24, 44, 36, 4, 12);

        // 5. Left Arm & Sleeve Overlay (Base: u=36, v=52; Overlay: u=52, v=52)
        int lay = ty + Math.abs(armLegSwingY) / 2;
        int lax = tx + 16 - armLegSwingY / 2;
        drawSkinPart(context, skinTex, lax, lay, 8, 24, 36, 52, 4, 12);
        drawSkinPart(context, skinTex, lax, lay, 8, 24, 52, 52, 4, 12);

        // 6. Head & Hat Overlay (Base: u=8, v=8; Overlay: u=40, v=8) with look tracking
        int hx = tx + (int) hox;
        int hy = centerY + 2 + (int) hoy;
        drawSkinPart(context, skinTex, hx, hy, 16, 16, 8, 8, 8, 8);
        drawSkinPart(context, skinTex, hx, hy, 16, 16, 40, 8, 8, 8);
    }

    private void drawSkinPart(DrawContext context, Identifier skinTex, int dx, int dy, int dw, int dh, int u, int v, int sw, int sh) {
        context.drawTexture(RenderLayer::getGuiTextured, skinTex, dx, dy, u, v, dw, dh, 64, 64);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
