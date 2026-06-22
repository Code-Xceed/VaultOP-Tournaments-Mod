package com.vaultop.mod.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        refreshProfile();
        fetchRegisteredEvents();
    }

    private void refreshProfile() {
        String token = VaultOPMod.getInstance().getSessionManager().getSessionToken();
        VaultOPMod.getInstance().getRestClient().fetchProfile(token)
                .thenAccept(data -> {
                    this.client.execute(() -> {
                        this.profileData = data;
                        this.statusText = "";
                        
                        com.google.gson.JsonObject userObj = profileData.has("user") ? profileData.getAsJsonObject("user") : null;
                        if (userObj != null) {
                            String ign = userObj.has("ign") && !userObj.get("ign").isJsonNull() ? userObj.get("ign").getAsString() : "";
                            String accountType = userObj.has("accountType") && !userObj.get("accountType").isJsonNull() 
                                    ? userObj.get("accountType").getAsString() : "PREMIUM";

                            // Start downloading skin if IGN is linked
                            if (!ign.isEmpty() && !"Not Linked".equalsIgnoreCase(ign)) {
                                String skinUrl = VaultOPMod.getInstance().getConfigManager().getBackendUrl() + "/api/skin-proxy/" + ign + "?type=" + accountType;
                                DynamicTextureLoader.getOrLoad(skinUrl, "skin_" + ign);
                            }

                            // Start downloading avatar if Discord ID exists
                            if (userObj.has("discordId") && !userObj.get("discordId").isJsonNull()
                                    && userObj.has("avatar") && !userObj.get("avatar").isJsonNull()) {
                                String discordId = userObj.get("discordId").getAsString();
                                String avatarHash = userObj.get("avatar").getAsString();
                                if (!avatarHash.isEmpty()) {
                                    String avatarUrl = "https://cdn.discordapp.com/avatars/" + discordId + "/" + avatarHash + ".png";
                                    DynamicTextureLoader.getOrLoad(avatarUrl, "avatar_" + discordId);
                                }
                            }
                        }

                        rebuildWidgets();
                    });
                })
                .exceptionally(ex -> {
                    this.client.execute(() -> {
                        this.statusText = "Failed to load profile.";
                        rebuildWidgets();
                    });
                    return null;
                });
    }

    private void fetchRegisteredEvents() {
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
                        rebuildWidgets();
                    });
                })
                .exceptionally(ex -> {
                    this.client.execute(() -> {
                        this.eventsLoadingStatus = "Failed to load registered events.";
                        rebuildWidgets();
                    });
                    return null;
                });
    }

    private void rebuildWidgets() {
        this.clearChildren();

        // 1. Back button centered at the bottom of the screen
        this.addDrawableChild(new PremiumButtonWidget(this.width / 2 - 40, this.height - 24, 80, 18, Text.literal("Back"), button -> close(), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        if (profileData != null) {
            int panelX = 15;
            int panelY = 48;
            int panelW = this.width - 30;
            int panelH = this.height - 78;

            int leftX = panelX + 8;
            int leftY = panelY + 8;
            int leftW = (int) (panelW * 0.50) - 10;
            int leftH = panelH - 16;

            int leftTopH = 68;
            int leftBotY = leftY + leftTopH + 8;
            int leftBotH = leftH - leftTopH - 8;

            // Generate redirection view buttons for each registered event in the viewport
            int eventY = leftBotY + 18 - rightScrollOffset;
            int cardW = leftW - 16;

            for (JsonObject event : registeredEvents) {
                int cardY = eventY;
                boolean isBtnVisible = (cardY >= leftBotY + 14 && cardY + 28 <= leftBotY + leftBotH - 4);
                
                if (isBtnVisible) {
                    int viewBtnX = leftX + 8 + cardW - 36;
                    int viewBtnY = cardY + 6;
                    
                    this.addDrawableChild(new PremiumButtonWidget(viewBtnX, viewBtnY, 30, 16, Text.literal("View"), button -> {
                        this.client.setScreen(new TournamentDetailScreen(this, event));
                    }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));
                }
                eventY += 34;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelX = 15;
        int panelY = 48;
        int panelW = this.width - 30;
        int panelH = this.height - 78;

        int leftX = panelX + 8;
        int leftY = panelY + 8;
        int leftW = (int) (panelW * 0.50) - 10;
        int leftH = panelH - 16;

        int leftTopH = 68;
        int leftBotY = leftY + leftTopH + 8;
        int leftBotH = leftH - leftTopH - 8;

        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= leftBotY && mouseY <= leftBotY + leftBotH) {
            int scrollAreaH = leftBotH - 22;
            int totalHeight = registeredEvents.size() * 34;
            int maxScroll = Math.max(0, totalHeight - scrollAreaH);
            if (verticalAmount != 0) {
                rightScrollOffset -= (int) Math.signum(verticalAmount) * 15;
                if (rightScrollOffset < 0) rightScrollOffset = 0;
                if (rightScrollOffset > maxScroll) rightScrollOffset = maxScroll;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier bgTex = Identifier.of("vaultop", "textures/gui/mod_bg_image.png");
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, -100.0f);
        context.drawTexture(RenderLayer::getGuiTextured, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.getMatrices().pop();
        context.draw();
        context.fill(RenderLayer.getGui(), 0, 0, this.width, this.height, -50, 0x80050505);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // 1. Top Header panel
        TournamentListScreen.drawPremiumBeveledBox(context, 0, -2, this.width, 42, 0xD00A0F18, 0x25FFFFFF, 0x10FFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("VaultOP Esports"), 15, 16, 0xFFFFD700);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Player Profile"), this.width / 2, 16, 0xFFFFFF);

        int panelX = 15;
        int panelY = 48;
        int panelW = this.width - 30;
        int panelH = this.height - 78;

        // Draw outer premium container layout box
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

            // Left Column layout: User Info & Registered Tournaments
            int leftX = panelX + 8;
            int leftY = panelY + 8;
            int leftW = (int) (panelW * 0.50) - 10;
            int leftH = panelH - 16;

            int leftTopH = 68;
            int leftBotY = leftY + leftTopH + 8;
            int leftBotH = leftH - leftTopH - 8;

            // 1. Identity Panel (Left Top)
            TournamentListScreen.drawPremiumBeveledBox(context, leftX, leftY, leftW, leftTopH, 0x8005080E, 0x20FFFFFF, 0x10FFFFFF);

            // Discord avatar rendering
            int avatarX = leftX + 10;
            int avatarY = leftY + 8;
            int avatarSize = 24;
            TournamentListScreen.drawPremiumBeveledBox(context, avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, 0xFF050505, 0x40FFFFFF, 0x20FFFFFF);

            Identifier avatarTex = null;
            if (userObj != null && userObj.has("discordId") && !userObj.get("discordId").isJsonNull()
                    && userObj.has("avatar") && !userObj.get("avatar").isJsonNull()) {
                String dId = userObj.get("discordId").getAsString();
                String avatarHash = userObj.get("avatar").getAsString();
                if (!avatarHash.isEmpty()) {
                    avatarTex = DynamicTextureLoader.getOrLoad("https://cdn.discordapp.com/avatars/" + dId + "/" + avatarHash + ".png", "avatar_" + dId);
                }
            }

            if (avatarTex != null) {
                context.drawTexture(RenderLayer::getGuiTextured, avatarTex, avatarX, avatarY, 0f, 0f, avatarSize, avatarSize, avatarSize, avatarSize);
            } else {
                context.fill(avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize, 0xFF3C464F);
                String firstChar = discordUser.isEmpty() ? "?" : discordUser.substring(0, 1).toUpperCase();
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(firstChar), avatarX + avatarSize / 2, avatarY + avatarSize / 2 - 4, 0xFFFFFF);
            }

            // Username & Role badge
            String truncatedName = discordUser;
            if (this.textRenderer.getWidth(discordUser) > leftW - 100) {
                truncatedName = this.textRenderer.trimToWidth(discordUser, leftW - 110) + "..";
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + truncatedName), leftX + 38, leftY + 8, 0xFFFFFF);

            String formattedRole = getRoleBadgeText(role);
            int badgeW = this.textRenderer.getWidth(formattedRole) + 6;
            int badgeH = 10;
            int badgeX = leftX + 38;
            int badgeY = leftY + 18;
            TournamentListScreen.drawPremiumBeveledBox(context, badgeX, badgeY, badgeW, badgeH, getRoleBadgeColor(role), getRoleBadgeBorderColor(role), getRoleBadgeBorderColor(role));
            context.drawTextWithShadow(this.textRenderer, Text.literal(formattedRole), badgeX + 3, badgeY + 1, 0xFFFFFF);

            // Double Column/Stats rows
            int row1Y = leftY + 38;
            int row2Y = leftY + 49;
            int rightAlignX = leftX + leftW - 10;

            // Row 1: Left = Discord tag, Right = Whitelist IGN (Mode)
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7Discord: §f" + truncatedName), leftX + 10, row1Y, 0xFFFFFF);
            
            String ignStr = "§7IGN: §f" + ign;
            if (!"Not Linked".equalsIgnoreCase(ign) && !ign.isEmpty()) {
                ignStr += " §8(§b" + accountType.toLowerCase() + "§8)";
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(ignStr), rightAlignX - this.textRenderer.getWidth(ignStr), row1Y, 0xFFFFFF);

            // Row 2: Left = Discord ID, Right = Status (CONNECTED)
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7ID: §9" + discordId), leftX + 10, row2Y, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7Status: §aCONNECTED"), rightAlignX - this.textRenderer.getWidth("Status: CONNECTED"), row2Y, 0xFFFFFF);


            // 2. Registered Tournaments Panel (Left Bottom)
            TournamentListScreen.drawPremiumBeveledBox(context, leftX, leftBotY, leftW, leftBotH, 0x8005080E, 0x2055FF55, 0x1055FF55);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§a🏆 REGISTERED EVENTS (" + registeredEvents.size() + ")"), leftX + leftW / 2, leftBotY + 5, 0xFFFFFF);
            context.fill(leftX + 8, leftBotY + 14, leftX + leftW - 8, leftBotY + 15, 0x20FFFFFF);

            int scrollAreaH = leftBotH - 22;
            if (!eventsLoadingStatus.isEmpty()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + eventsLoadingStatus), leftX + leftW / 2, leftBotY + 15 + scrollAreaH / 2 - 4, 0xFFFFFF);
            } else {
                context.enableScissor(leftX + 2, leftBotY + 15, leftX + leftW - 2, leftBotY + leftBotH - 4);

                int eventY = leftBotY + 18 - rightScrollOffset;
                int cardW = leftW - 16;

                for (JsonObject event : registeredEvents) {
                    String name = event.has("name") ? event.get("name").getAsString() : "Unnamed Event";
                    String statusLabel = event.has("userStatusLabel") ? event.get("userStatusLabel").getAsString() : "PENDING";
                    String gameVersion = event.has("gameVersion") ? event.get("gameVersion").getAsString() : "1.21.5";

                    int badgeBg = 0x40808080;
                    String formattedBadge = "§7" + statusLabel.toUpperCase();
                    if (statusLabel.toLowerCase().contains("approved") || statusLabel.toLowerCase().contains("registered")) {
                        badgeBg = 0x3355FF55;
                        formattedBadge = "§a" + statusLabel.toUpperCase();
                    } else if (statusLabel.toLowerCase().contains("pending")) {
                        badgeBg = 0x33FFFF55;
                        formattedBadge = "§e" + statusLabel.toUpperCase();
                    } else if (statusLabel.toLowerCase().contains("rejected")) {
                        badgeBg = 0x33FF5555;
                        formattedBadge = "§c" + statusLabel.toUpperCase();
                    }

                    // Card background
                    TournamentListScreen.drawPremiumBeveledBox(context, leftX + 8, eventY, cardW, 28, 0x40000000, 0x15FFFFFF, 0x0AFFFFFF);

                    // Name of Event
                    String displayName = name;
                    int maxNameW = cardW - 130;
                    if (this.textRenderer.getWidth(name) > maxNameW) {
                        displayName = this.textRenderer.trimToWidth(name, maxNameW - 6) + "..";
                    }
                    context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + displayName), leftX + 12, eventY + 3, 0xFFFFFF);
                    context.drawTextWithShadow(this.textRenderer, Text.literal("§bVer: " + gameVersion), leftX + 12, eventY + 13, 0xFFFFFF);

                    // Draw status badge
                    int labelW = this.textRenderer.getWidth(formattedBadge) + 6;
                    int labelH = 10;
                    int labelX = leftX + 8 + cardW - labelW - 40; // shift left to leave room for view button
                    int labelYInside = eventY + 9;
                    TournamentListScreen.drawPremiumBeveledBox(context, labelX, labelYInside, labelW, labelH, badgeBg, badgeBg * 2, badgeBg * 2);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(formattedBadge), labelX + 3, labelYInside + 1, 0xFFFFFF);

                    eventY += 34;
                }

                context.disableScissor();

                // Scrollbar
                int totalHeight = registeredEvents.size() * 34;
                int maxScroll = Math.max(0, totalHeight - scrollAreaH);
                if (maxScroll > 0) {
                    int scrollbarX = leftX + leftW - 6;
                    int scrollbarY = leftBotY + 16;
                    context.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollAreaH, 0x22FFFFFF);

                    int thumbHeight = (scrollAreaH * scrollAreaH) / (scrollAreaH + maxScroll);
                    if (thumbHeight < 10) thumbHeight = 10;
                    int thumbY = scrollbarY + (rightScrollOffset * (scrollAreaH - thumbHeight)) / maxScroll;
                    context.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, 0x88FFFFFF);
                }
            }


            // Right Column layout: Standalone Large Skin Viewer
            int rightX = leftX + leftW + 8;
            int rightY = panelY + 8;
            int rightW = panelW - leftW - 24;
            int rightH = panelH - 16;

            // Draw large standalone skin container & gradient
            TournamentListScreen.drawPremiumBeveledBox(context, rightX, rightY, rightW, rightH, 0x00000000, 0x202196F3, 0x152196F3);
            drawBlendedGradient(context, rightX + 2, rightY + 2, rightW - 4, rightH - 4);

            // Draw Minecraft nametag centered above skin card
            String nameTagText = discordUser;
            int tagWidth = this.textRenderer.getWidth(nameTagText);
            int tagX = rightX + (rightW - tagWidth) / 2;
            int tagY = rightY + 10;
            
            context.fill(tagX - 3, tagY - 1, tagX + tagWidth + 3, tagY + 8, 0x99000000);
            context.fill(tagX - 4, tagY - 2, tagX + tagWidth + 4, tagY - 1, 0x33FFFFFF);
            context.fill(tagX - 4, tagY + 8, tagX + tagWidth + 4, tagY + 9, 0x33FFFFFF);
            context.fill(tagX - 4, tagY - 1, tagX - 3, tagY + 8, 0x33FFFFFF);
            context.fill(tagX + tagWidth + 3, tagY - 1, tagX + tagWidth + 4, tagY + 8, 0x33FFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal(nameTagText), tagX, tagY, 0xFFFFFF);

            // Resolve and render 2.5D skin at large scale (1.4x)
            Identifier skinTex = null;
            if (!ign.isEmpty() && !"Not Linked".equalsIgnoreCase(ign)) {
                String skinUrl = VaultOPMod.getInstance().getConfigManager().getBackendUrl() + "/api/skin-proxy/" + ign + "?type=" + accountType;
                skinTex = DynamicTextureLoader.getOrLoad(skinUrl, "skin_" + ign);
            }
            if (skinTex == null) {
                String fallbackUrl = VaultOPMod.getInstance().getConfigManager().getBackendUrl() + "/api/skin-proxy/Steeeve?type=PREMIUM";
                skinTex = DynamicTextureLoader.getOrLoad(fallbackUrl, "skin_steeeve");
            }

            if (skinTex != null) {
                float skinScale = 1.4f;
                int skinH = (int) (64 * skinScale);
                int centerY = rightY + (rightH - skinH) / 2 + 10;
                render25DCharacter(context, skinTex, rightX + rightW / 2, centerY, mouseX, mouseY, skinScale);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String getRoleBadgeText(String role) {
        if ("DEV".equalsIgnoreCase(role)) return "§bDEVELOPER";
        if ("ADMIN".equalsIgnoreCase(role)) return "§bADMINISTRATOR";
        if ("STAFF".equalsIgnoreCase(role)) return "§eEVENT STAFF";
        return "§aCOMPETITOR";
    }

    private int getRoleBadgeColor(String role) {
        if ("DEV".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) return 0x400055FF; // blue
        if ("STAFF".equalsIgnoreCase(role)) return 0x40FFAA00; // gold
        return 0x4000AA00; // green
    }

    private int getRoleBadgeBorderColor(String role) {
        if ("DEV".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) return 0xFF55FFFF; // cyan
        if ("STAFF".equalsIgnoreCase(role)) return 0xFFFFAA00; // gold
        return 0xFF55FF55; // green
    }

    private void drawBlendedGradient(DrawContext context, int x, int y, int w, int h) {
        for (int i = 0; i < h; i++) {
            float ratio = (float) i / h;
            int r = (int) (30 * (1.0f - ratio) + 8 * ratio);
            int g = (int) (30 * (1.0f - ratio) + 8 * ratio);
            int b = (int) (30 * (1.0f - ratio) + 16 * ratio);
            int a = (int) (76 * (1.0f - ratio) + 204 * ratio); // 0.3 * 255 = 76, 0.8 * 255 = 204
            int color = (a << 24) | (r << 16) | (g << 8) | b;
            context.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

    private void render25DCharacter(DrawContext context, Identifier skinTex, int centerX, int centerY, int mouseX, int mouseY, float scale) {
        float tick = System.currentTimeMillis() / 50.0f;

        // Dimensions based on scale
        int headSz = (int) (16 * scale);
        int torsoW = (int) (16 * scale);
        int torsoH = (int) (24 * scale);
        int limbW = (int) (8 * scale);
        int limbH = (int) (24 * scale);

        // Base coordinate shifts to make it fit inside our skin card
        int tx = centerX - torsoW / 2;
        int ty = centerY + headSz + 2;

        // Walking cycle calculations (limbs swing)
        float cycle = (float) Math.sin(tick * 0.15f);
        int armLegSwingY = (int) (cycle * 3.0f * scale);

        // Head looking tracking calculations
        int headCX = centerX;
        int headCY = centerY + headSz / 2;

        double dx = mouseX - headCX;
        double dy = mouseY - headCY;
        double len = Math.sqrt(dx * dx + dy * dy);
        float hox = 0;
        float hoy = 0;
        if (len > 0.1) {
            hox = (float) (dx / len) * Math.min(2.5f * scale, (float) len * 0.03f * scale);
            hoy = (float) (dy / len) * Math.min(1.8f * scale, (float) len * 0.03f * scale);
        }

        // Draw parts step by step (back to front order)

        // 1. Right Leg & Pants Overlay
        int rly = ty + torsoH + Math.abs(armLegSwingY) / 2;
        int rlx = tx - armLegSwingY / 2;
        drawSkinPart(context, skinTex, rlx, rly, limbW, limbH, 4, 20);
        drawSkinPart(context, skinTex, rlx, rly, limbW, limbH, 4, 36);

        // 2. Left Leg & Pants Overlay
        int lly = ty + torsoH + Math.abs(armLegSwingY) / 2;
        int llx = tx + torsoW / 2 + armLegSwingY / 2;
        drawSkinPart(context, skinTex, llx, lly, limbW, limbH, 20, 52);
        drawSkinPart(context, skinTex, llx, lly, limbW, limbH, 4, 52);

        // 3. Torso & Jacket Overlay
        drawSkinPart(context, skinTex, tx, ty, torsoW, torsoH, 20, 20);
        drawSkinPart(context, skinTex, tx, ty, torsoW, torsoH, 20, 36);

        // 4. Right Arm & Sleeve Overlay
        int ray = ty + Math.abs(armLegSwingY) / 2;
        int rax = tx - limbW + armLegSwingY / 2;
        drawSkinPart(context, skinTex, rax, ray, limbW, limbH, 44, 20);
        drawSkinPart(context, skinTex, rax, ray, limbW, limbH, 44, 36);

        // 5. Left Arm & Sleeve Overlay
        int lay = ty + Math.abs(armLegSwingY) / 2;
        int lax = tx + torsoW - armLegSwingY / 2;
        drawSkinPart(context, skinTex, lax, lay, limbW, limbH, 36, 52);
        drawSkinPart(context, skinTex, lax, lay, limbW, limbH, 52, 52);

        // 6. Head & Hat Overlay
        int hx = tx + (int) hox;
        int hy = centerY + (int) hoy;
        drawSkinPart(context, skinTex, hx, hy, headSz, headSz, 8, 8);
        drawSkinPart(context, skinTex, hx, hy, headSz, headSz, 40, 8);
    }

    private void drawSkinPart(DrawContext context, Identifier skinTex, int dx, int dy, int dw, int dh, int u, int v) {
        context.drawTexture(RenderLayer::getGuiTextured, skinTex, dx, dy, u, v, dw, dh, 64, 64);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
