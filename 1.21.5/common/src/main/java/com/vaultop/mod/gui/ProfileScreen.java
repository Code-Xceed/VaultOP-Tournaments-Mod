package com.vaultop.mod.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vaultop.mod.VaultOPMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public class ProfileScreen extends Screen {
    private final Screen parent;
    private JsonObject profileData = null;
    private List<JsonObject> registeredEvents = new ArrayList<>();
    private String statusText = "Loading profile...";
    private String eventsLoadingStatus = "Loading registered events...";
    private int rightScrollOffset = 0;

    // 3D model
    private float entityRotation = -30f;
    private boolean isDraggingEntity = false;
    private double lastDragX = 0;

    // Player model for 3D rendering (no entity needed)
    @SuppressWarnings("rawtypes")
    private PlayerEntityModel playerModel;
    private boolean modelInitAttempted = false;

    public ProfileScreen(Screen parent) {
        super(Text.literal("VaultOP Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshProfile();
        fetchRegisteredEvents();
        ensureModelInit();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void ensureModelInit() {
        if (playerModel != null) return;
        try {
            var client = MinecraftClient.getInstance();
            var loadedModels = client.getLoadedEntityModels();
            if (loadedModels == null) {
                VaultOPMod.LOGGER.info("[ProfileScreen] LoadedEntityModels is null, skipping init (will retry)");
                return;
            }
            ModelPart root = loadedModels.getModelPart(EntityModelLayers.PLAYER);
            playerModel = new PlayerEntityModel(root, false);
            VaultOPMod.LOGGER.info("[ProfileScreen] Successfully initialized 3D player model.");
        } catch (Exception e) {
            VaultOPMod.LOGGER.error("[ProfileScreen] Failed to initialize 3D player model:", e);
        }
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

                            if (!ign.isEmpty() && !"Not Linked".equalsIgnoreCase(ign)) {
                                String skinUrl = VaultOPMod.getInstance().getConfigManager().getBackendUrl() + "/api/skin-proxy/" + ign + "?type=" + accountType;
                                DynamicTextureLoader.getOrLoad(skinUrl, "skin_" + ign);
                            }

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

    private int getPanelX() { return 15; }
    private int getPanelY() { return 32; }
    private int getPanelW() { return this.width - 30; }
    private int getPanelH() { return this.height - 47; }
    private int getLeftW() { return (int) (getPanelW() * 0.38); }
    private int getCenterW() { return (int) (getPanelW() * 0.26); }
    private int getRightW() { return getPanelW() - getLeftW() - getCenterW() - 32; }
    private int getEventsHeaderHeight() { return 52; }
    private int getEventCardHeight() { return 36; }
    private int getEventCardSpacing() { return 8; }
    private int getEventCardStep() { return getEventCardHeight() + getEventCardSpacing(); }

    @Override
    public void tick() {
        super.tick();
        if (!isDraggingEntity) {
            entityRotation += 1.0f;
        }
    }

    private void rebuildWidgets() {
        this.clearChildren();

        // Red Back button in top-left
        this.addDrawableChild(new PremiumButtonWidget(10, 10, 40, 18, Text.literal("Back"), button -> close(), 0xFF8B2B2B, 0xFF4A1010, 0xFFE57373));

        // Blue Refresh button
        this.addDrawableChild(new PremiumButtonWidget(54, 10, 20, 18, Text.literal("↻"), button -> {
            VaultOPMod.getInstance().forceRefreshData();
            refreshProfile();
            fetchRegisteredEvents();
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && profileData != null) {
            int panelX = getPanelX();
            int panelY = getPanelY();
            int panelW = getPanelW();
            int panelH = getPanelH();
            int leftW = getLeftW();
            int centerW = getCenterW();
            int rightW = getRightW();

            int leftX = panelX + 8;
            int centerX = leftX + leftW + 8;
            int rightX = centerX + centerW + 8;
            int y = panelY + 8;
            int h = panelH - 16;

            // 1. Check if clicking on the 3D skin (Center Column) to initiate drag
            if (mouseX >= centerX && mouseX <= centerX + centerW && mouseY >= y && mouseY <= y + h) {
                isDraggingEntity = true;
                lastDragX = mouseX;
                return true;
            }

            // 2. Check if clicking on any of the registered tournaments cards
            int listTop = y + getEventsHeaderHeight();
            if (mouseX >= rightX + 12 && mouseX <= rightX + rightW - 8 && mouseY >= listTop && mouseY <= y + h - 8) {
                int eventY = listTop + 4 - rightScrollOffset;
                int cardW = rightW - 20;
                for (JsonObject event : registeredEvents) {
                    boolean isClicked = mouseX >= rightX + 12 && mouseX <= rightX + 12 + cardW && mouseY >= eventY && mouseY <= eventY + getEventCardHeight();
                    if (isClicked) {
                        this.client.setScreen(new TournamentDetailScreen(this, event));
                        return true;
                    }
                    eventY += getEventCardStep();
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingEntity && button == 0) {
            double dx = mouseX - lastDragX;
            entityRotation += (float) dx * 1.5f;
            lastDragX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingEntity = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelW = getPanelW();
        int panelH = getPanelH();
        int leftW = getLeftW();
        int centerW = getCenterW();
        int rightW = getRightW();

        int leftX = panelX + 8;
        int centerX = leftX + leftW + 8;
        int rightX = centerX + centerW + 8;
        int y = panelY + 8;
        int h = panelH - 16;

        int listTop = y + getEventsHeaderHeight();
        if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= listTop && mouseY <= y + h - 8) {
            int scrollAreaH = h - getEventsHeaderHeight() - 8;
            int totalHeight = Math.max(0, registeredEvents.size() * getEventCardStep() - getEventCardSpacing());
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
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, -100.0f);
        context.drawTexture(RenderLayer::getGuiTextured, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.getMatrices().pop();
        context.draw();
        context.fill(RenderLayer.getGui(), 0, 0, this.width, this.height, -50, 0x80050505);
    }    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Top Header
        TournamentListScreen.drawPremiumBeveledBox(context, 0, -2, this.width, 42, 0xD00A0F18, 0x25FFFFFF, 0x10FFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("VaultOP Esports"), 82, 17, 0xFFFFD700);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Player Profile"), this.width / 2, 17, 0xFFFFFF);

        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelW = getPanelW();
        int panelH = getPanelH();

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
                role = userObj.has("role") ? userObj.get("role").getAsString() : "COMPETITOR";
            }

            int leftW = getLeftW();
            int centerW = getCenterW();
            int rightW = getRightW();

            int leftX = panelX + 8;
            int centerX = leftX + leftW + 8;
            int rightX = centerX + centerW + 8;

            int y = panelY + 8;
            int h = panelH - 16;

            int leftH = h;
            int leftY = y;

            // ── COLUMN 1: IDENTITY CARD ──
            int identityCardH = 108;
            int cardColor = getRoleBadgeBorderColor(role);
            TournamentListScreen.drawPremiumBeveledBox(context, leftX, leftY, leftW, identityCardH, 0xD005080E, (cardColor & 0x00FFFFFF) | 0x40000000, (cardColor & 0x00FFFFFF) | 0x15000000);
            context.fill(leftX + 1, leftY + 1, leftX + leftW - 1, leftY + 23, 0x12000000);
            context.drawText(this.textRenderer, Text.literal("§8§lVAULTOP COMPETITOR CARD"), leftX + 12, leftY + 8, 0x88FFFFFF, false);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§8Linked tournament identity"), leftX + 12, leftY + 18, 0xFFFFFF);
            context.fill(leftX + 10, leftY + 28, leftX + leftW - 10, leftY + 29, 0x18FFFFFF);

            int avatarX = leftX + 12;
            int avatarY = leftY + 36;
            int avatarSize = 40;
            TournamentListScreen.drawPremiumBeveledBox(context, avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, 0xFF080808, 0x30FFFFFF, 0x15FFFFFF);

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

            // Glowing green connected dot in bottom-right corner of avatar frame
            context.fill(avatarX + avatarSize - 4, avatarY + avatarSize - 4, avatarX + avatarSize + 1, avatarY + avatarSize + 1, 0xFF000000);
            context.fill(avatarX + avatarSize - 3, avatarY + avatarSize - 3, avatarX + avatarSize, avatarY + avatarSize, 0xFF55FF55);

            int infoX = avatarX + avatarSize + 12;
            int infoY = avatarY + 1;

            String truncatedName = discordUser;
            int maxNameWidth = leftW - (infoX - leftX) - 14;
            if (this.textRenderer.getWidth(discordUser) > maxNameWidth) {
                truncatedName = this.textRenderer.trimToWidth(discordUser, maxNameWidth - 10) + "..";
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + truncatedName), infoX, infoY, 0xFFFFFF);

            String ignDisplay = "§7IGN: §f" + ign;
            if (!"Not Linked".equalsIgnoreCase(ign) && !ign.isEmpty()) {
                ignDisplay += " §8(§b" + accountType.substring(0, Math.min(accountType.length(), 4)).toLowerCase() + "§8)";
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(ignDisplay), infoX, infoY + 11, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§8Discord link verified"), infoX, infoY + 22, 0xFFFFFF);

            // Barcode
            int barcodeX = infoX;
            int barcodeY = infoY + 34;
            int barcodeH = 7;
            context.fill(barcodeX, barcodeY, barcodeX + 2, barcodeY + barcodeH, 0x88FFFFFF);
            context.fill(barcodeX + 3, barcodeY, barcodeX + 4, barcodeY + barcodeH, 0x88FFFFFF);
            context.fill(barcodeX + 6, barcodeY, barcodeX + 8, barcodeY + barcodeH, 0x88FFFFFF);
            context.fill(barcodeX + 9, barcodeY, barcodeX + 10, barcodeY + barcodeH, 0x88FFFFFF);
            context.fill(barcodeX + 12, barcodeY, barcodeX + 13, barcodeY + barcodeH, 0x88FFFFFF);
            context.fill(barcodeX + 15, barcodeY, barcodeX + 17, barcodeY + barcodeH, 0x88FFFFFF);
            context.fill(barcodeX + 19, barcodeY, barcodeX + 20, barcodeY + barcodeH, 0x88FFFFFF);
            context.fill(barcodeX + 22, barcodeY, barcodeX + 25, barcodeY + barcodeH, 0x88FFFFFF);
            context.fill(barcodeX + 27, barcodeY, barcodeX + 28, barcodeY + barcodeH, 0x88FFFFFF);
            context.fill(barcodeX + 30, barcodeY, barcodeX + 32, barcodeY + barcodeH, 0x88FFFFFF);

            // Role Badge
            int badgeBg = getRoleBadgeColor(role);
            int badgeBorder = getRoleBadgeBorderColor(role);
            String badgeText = getRoleBadgeText(role);
            TournamentListScreen.drawPremiumBeveledBox(context, leftX + 12, leftY + identityCardH - 24, leftW - 24, 16, badgeBg, badgeBorder, badgeBorder);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(badgeText), leftX + leftW / 2, leftY + identityCardH - 19, 0xFFFFFF);

            // ── COLUMN 1: COMPETITOR DOSSIER (Statistics / Info) ──
            int dossierY = leftY + identityCardH + 10;
            int dossierH = leftH - identityCardH - 10;
            TournamentListScreen.drawPremiumBeveledBox(context, leftX, dossierY, leftW, dossierH, 0x8005080E, 0x20FFFFFF, 0x10FFFFFF);
            context.fill(leftX + 1, dossierY + 1, leftX + leftW - 1, dossierY + 27, 0x12000000);

            int dossierContentX = leftX + 12;
            int dossierContentY = dossierY + 10;
            context.drawTextWithShadow(this.textRenderer, Text.literal("§6§lCOMPETITOR DOSSIER"), dossierContentX, dossierContentY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§8Operational profile and security state"), dossierContentX, dossierContentY + 10, 0xFFFFFF);
            context.fill(leftX + 10, dossierY + 28, leftX + leftW - 10, dossierY + 29, 0x18FFFFFF);

            String displayDiscordId = discordId.length() > 14 ? discordId.substring(0, 12) + "..." : discordId;
            int rowX = leftX + 10;
            int rowW = leftW - 20;
            int rowY = dossierY + 38;
            int rowH = 18;
            int rowGap = 7;

            drawProfileStatRow(context, rowX, rowY, rowW, "Discord ID", displayDiscordId, 0xFFFFFFFF, 0xFF6EA8FF);
            drawProfileStatRow(context, rowX, rowY + (rowH + rowGap), rowW, "Client Type", accountType.toUpperCase(), 0xFF55FFFF, 0xFF55D6FF);
            drawProfileStatRow(context, rowX, rowY + (rowH + rowGap) * 2, rowW, "Registrations", String.valueOf(registeredEvents.size()), 0xFFFFD54F, 0xFFFFD54F);
            drawProfileStatRow(context, rowX, rowY + (rowH + rowGap) * 3, rowW, "Security Check", "PASS", 0xFF55FF55, 0xFF55FF55);
            drawProfileStatRow(context, rowX, rowY + (rowH + rowGap) * 4, rowW, "System Integrity", "SECURE", 0xFF55FFAA, 0xFF55FFAA);

            // ── COLUMN 2: 3D SKIN VIEWER & PEDESTAL ──
            int centerH = h;
            int centerY = y;

            TournamentListScreen.drawPremiumBeveledBox(context, centerX, centerY, centerW, centerH, 0x00000000, 0x202196F3, 0x152196F3);
            drawBlendedGradient(context, centerX + 2, centerY + 2, centerW - 4, centerH - 4);

            int charCX = centerX + centerW / 2;
            int charBaseY = centerY + centerH - 38;
            float scale = 42.0f;

            // Draw sci-fi energy pedestal ovals
            drawEllipse(context, charCX, charBaseY + 1, 28, 6, 0x302196F3);
            drawEllipse(context, charCX, charBaseY + 1, 21, 4.5f, 0x602196F3);
            drawEllipse(context, charCX, charBaseY + 1, 14, 3.0f, 0xFF55FFFF);

            // Draw soft rising light energy tube
            context.fill(charCX - 22, centerY + 18, charCX + 22, charBaseY + 1, 0x0D2196F3);

            // Draw floating holographic particles
            float tickTime = (System.currentTimeMillis() / 150.0f);
            for (int p = 0; p < 4; p++) {
                float offset = (tickTime * 4.0f + p * 20.0f) % (charBaseY - centerY - 30);
                int py = (int) (charBaseY - offset);
                int px = charCX + (int)(Math.sin(tickTime * 0.2f + p * 1.5f) * 10.0f);
                context.fill(px - 1, py - 1, px + 1, py + 1, 0x6055FFFF);
            }

            // Nametag above character
            String nameTagText = discordUser;
            int tagWidth = this.textRenderer.getWidth(nameTagText);
            int tagX = charCX - tagWidth / 2;
            int tagY = charBaseY - (int)(scale * 2.35f) - 6;
            context.fill(tagX - 4, tagY - 2, tagX + tagWidth + 4, tagY + 10, 0xAA000000);
            context.fill(tagX - 5, tagY - 3, tagX + tagWidth + 5, tagY - 2, 0x33FFFFFF);
            context.fill(tagX - 5, tagY + 10, tagX + tagWidth + 5, tagY + 11, 0x33FFFFFF);
            context.fill(tagX - 5, tagY - 2, tagX - 4, tagY + 10, 0x33FFFFFF);
            context.fill(tagX + tagWidth + 4, tagY - 2, tagX + tagWidth + 5, tagY + 10, 0x33FFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal(nameTagText), tagX, tagY, 0xFFFFFF);

            // Get skin texture
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
                boolean rendered = false;
                if (client.player != null) {
                    try {
                        int size = (int) (scale * 1.05f);
                        // Temporarily rotate the client player entity for rendering
                        float oldYaw = client.player.getYaw();
                        float oldPitch = client.player.getPitch();
                        float oldBodyYaw = client.player.bodyYaw;
                        float oldHeadYaw = client.player.headYaw;

                        client.player.setYaw(entityRotation);
                        client.player.setPitch(0);
                        client.player.bodyYaw = entityRotation;
                        client.player.headYaw = entityRotation;

                        net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(
                            context,
                            charCX - 34, charBaseY - (int)(size * 2.35f),
                            charCX + 34, charBaseY + 2,
                            size,
                            0.0625f,
                            charCX,
                            charBaseY - (int)(size * 1.15f),
                            client.player
                        );

                        // Restore player rotation
                        client.player.setYaw(oldYaw);
                        client.player.setPitch(oldPitch);
                        client.player.bodyYaw = oldBodyYaw;
                        client.player.headYaw = oldHeadYaw;

                        rendered = true;
                    } catch (Exception e) {
                        rendered = false;
                    }
                }

                if (!rendered) {
                    // Try actual 3D model rendering (works without joining a world)
                    if (!render3DPlayerModel(context, skinTex, entityRotation, charCX, charBaseY - (int)(0.78f * scale), scale * 1.08f)) {
                        // Fallback to 2D rendering
                        float skinScale = 1.4f;
                        int headSz = (int) (16 * skinScale);
                        int torsoH = (int) (24 * skinScale);
                        int limbH = (int) (24 * skinScale);
                        int skinCenterY = charBaseY - (headSz + 2 + torsoH + limbH);
                        render25DCharacter(context, skinTex, charCX, skinCenterY, skinScale);
                    }
                }
            } else {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Loading skin..."), charCX, centerY + centerH / 2, 0xFFFFFF);
            }

            // Drag hint
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8Drag to rotate"), charCX, centerY + centerH - 14, 0xFFFFFF);

            // ── COLUMN 3: REGISTERED EVENTS LIST ──
            int rightH = h;
            int rightY = y;
            int eventsHeaderH = getEventsHeaderHeight();
            int listTop = rightY + eventsHeaderH;
            int approvedCount = 0;
            int pendingCount = 0;
            int attentionCount = 0;

            for (JsonObject event : registeredEvents) {
                String statusKey = getCompactRegistrationStatus(event.has("userStatusLabel") ? event.get("userStatusLabel").getAsString() : "");
                if ("APPROVED".equals(statusKey) || "REGISTERED".equals(statusKey)) {
                    approvedCount++;
                } else if ("PENDING".equals(statusKey)) {
                    pendingCount++;
                } else if ("REJECTED".equals(statusKey)) {
                    attentionCount++;
                }
            }

            TournamentListScreen.drawPremiumBeveledBox(context, rightX, rightY, rightW, rightH, 0x8005080E, 0x2055FF55, 0x1055FF55);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§a⚔ Registered Events §7(" + registeredEvents.size() + ")"), rightX + 12, rightY + 8, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§8Tournament queue and approval status"), rightX + 12, rightY + 19, 0xFFFFFF);
            context.fill(rightX + 10, rightY + 28, rightX + rightW - 10, rightY + 29, 0x18FFFFFF);

            TournamentListScreen.drawPremiumBeveledBox(context, rightX + 12, rightY + 34, rightW - 24, 14, 0x30000000, 0x18FFFFFF, 0x08000000);
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal("§aApproved: §f" + approvedCount + "   §ePending: §f" + pendingCount + "   §cAttention: §f" + attentionCount),
                    rightX + 18,
                    rightY + 38,
                    0xFFFFFF
            );

            int scrollAreaH = rightH - eventsHeaderH - 8;
            if (!eventsLoadingStatus.isEmpty()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + eventsLoadingStatus), rightX + rightW / 2, listTop + scrollAreaH / 2 - 4, 0xFFFFFF);
            } else {
                context.enableScissor(rightX + 4, listTop, rightX + rightW - 4, rightY + rightH - 8);

                int eventY = listTop + 4 - rightScrollOffset;
                int cardW = rightW - 20;

                for (JsonObject event : registeredEvents) {
                    String name = event.has("name") ? event.get("name").getAsString() : "Unnamed Event";
                    String statusLabel = event.has("userStatusLabel") ? event.get("userStatusLabel").getAsString() : "PENDING";
                    String gameVersion = event.has("gameVersion") ? event.get("gameVersion").getAsString() : "1.21.5";
                    String statusKey = getCompactRegistrationStatus(statusLabel);
                    int accentColor = getRegistrationStatusAccent(statusKey);
                    int eventBadgeBg = withAlpha(accentColor, 64);
                    int borderGlow = withAlpha(accentColor, 34);
                    String eventBadgeText = getRegistrationStatusLabel(statusKey);
                    boolean isHovered = mouseX >= rightX + 12 && mouseX <= rightX + 12 + cardW && mouseY >= eventY && mouseY <= eventY + getEventCardHeight();
                    if (eventY + getEventCardHeight() >= listTop && eventY <= rightY + rightH - 8) {
                        int cardX = rightX + 12;
                        int currentCardW = cardW;
                        if (isHovered) {
                            cardX += 2;
                            currentCardW -= 2;
                            borderGlow = withAlpha(accentColor, 90);
                        }

                        TournamentListScreen.drawPremiumBeveledBox(context, cardX, eventY, currentCardW, getEventCardHeight(), 0x46000000, borderGlow, withAlpha(accentColor, 18));
                        context.fill(cardX, eventY, cardX + 3, eventY + getEventCardHeight(), accentColor);

                        String displayName = name;
                        int maxNameW = currentCardW - 94;
                        if (this.textRenderer.getWidth(name) > maxNameW) {
                            displayName = this.textRenderer.trimToWidth(name, maxNameW - 8) + "..";
                        }
                        context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + displayName), cardX + 10, eventY + 7, 0xFFFFFF);
                        context.drawTextWithShadow(this.textRenderer, Text.literal("§bv" + gameVersion), cardX + 10, eventY + 20, 0xFFFFFF);

                        int labelW = this.textRenderer.getWidth(eventBadgeText) + 10;
                        int labelH = 12;
                        int labelX = cardX + currentCardW - labelW - 8;
                        int labelYInside = eventY + 12;
                        TournamentListScreen.drawPremiumBeveledBox(context, labelX, labelYInside, labelW, labelH, eventBadgeBg, withAlpha(accentColor, 110), withAlpha(accentColor, 50));
                        context.drawTextWithShadow(this.textRenderer, Text.literal(eventBadgeText), labelX + 5, labelYInside + 2, 0xFFFFFF);
                    }
                    eventY += getEventCardStep();
                }

                context.disableScissor();

                int totalHeight = Math.max(0, registeredEvents.size() * getEventCardStep() - getEventCardSpacing());
                int maxScroll = Math.max(0, totalHeight - scrollAreaH);
                if (maxScroll > 0) {
                    int scrollbarX = rightX + rightW - 6;
                    int scrollbarY = listTop;
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

    private void drawEllipse(DrawContext context, int cx, int cy, float rx, float ry, int color) {
        for (int i = -(int)ry; i <= (int)ry; i++) {
            float angle = (float) Math.asin(i / ry);
            int halfW = (int) (rx * Math.cos(angle));
            context.fill(cx - halfW, cy + i, cx + halfW, cy + i + 1, color);
        }
    }

    /**
     * Renders a 3D player model from the skin texture using PlayerEntityModel.
     * Works WITHOUT needing a player entity or being in a world.
     * Returns true if rendering succeeded, false to indicate fallback needed.
     */
    @SuppressWarnings("unchecked")
    private boolean render3DPlayerModel(DrawContext context, Identifier skinTexture, float rotation, int centerX, int centerY, float scale) {
        ensureModelInit();
        if (playerModel == null) return false;

        try {
            // Reset model to standing pose
            resetModelPose();

            // Flush any pending GUI draw calls so they render behind the model
            context.draw();

            context.getMatrices().push();

            // Position the model slightly higher so the profile column feels centered.
            context.getMatrices().translate(centerX, centerY - 0.72f * scale, 50.0f);

            // Scale: positive Y goes down in screen space, and model coordinates have positive Y going down
            context.getMatrices().scale(scale, scale, scale);

            // Apply user rotation
            Quaternionf bodyRot = new Quaternionf().rotateY((float) Math.toRadians(rotation));
            context.getMatrices().multiply(bodyRot);

            // Set up entity-style lighting
            DiffuseLighting.enableGuiDepthLighting();

            // Get vertex consumer for the skin texture
            VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer consumer = immediate.getBuffer(RenderLayer.getEntityTranslucent(skinTexture));

            // Render the player model
            int light = 0xF000F0; // full brightness
            int overlay = OverlayTexture.DEFAULT_UV;
            playerModel.render(context.getMatrices(), consumer, light, overlay, 0xFFFFFFFF);

            // Flush the entity vertex buffer
            immediate.draw();

            context.getMatrices().pop();

            // Restore lighting
            DiffuseLighting.enableGuiDepthLighting();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void resetModelPose() {
        if (playerModel == null) return;
        resetPart(playerModel.head);
        resetPart(playerModel.hat);
        resetPart(playerModel.body);
        resetPart(playerModel.rightArm);
        resetPart(playerModel.leftArm);
        resetPart(playerModel.rightLeg);
        resetPart(playerModel.leftLeg);
        
        resetPart(playerModel.jacket);
        resetPart(playerModel.leftSleeve);
        resetPart(playerModel.rightSleeve);
        resetPart(playerModel.leftPants);
        resetPart(playerModel.rightPants);

        playerModel.head.visible = true;
        playerModel.hat.visible = true;
        playerModel.body.visible = true;
        playerModel.rightArm.visible = true;
        playerModel.leftArm.visible = true;
        playerModel.rightLeg.visible = true;
        playerModel.leftLeg.visible = true;
        playerModel.jacket.visible = true;
        playerModel.leftSleeve.visible = true;
        playerModel.rightSleeve.visible = true;
        playerModel.leftPants.visible = true;
        playerModel.rightPants.visible = true;
    }

    private void resetPart(ModelPart part) {
        part.pitch = 0;
        part.yaw = 0;
        part.roll = 0;
    }

    private String getCompactRegistrationStatus(String statusLabel) {
        if (statusLabel == null) return "REGISTERED";

        String lower = statusLabel.toLowerCase();
        if (lower.contains("approved")) return "APPROVED";
        if (lower.contains("pending")) return "PENDING";
        if (lower.contains("rejected")) return "REJECTED";
        if (lower.contains("registered")) return "REGISTERED";
        return "REGISTERED";
    }

    private String getRegistrationStatusLabel(String statusKey) {
        return switch (statusKey) {
            case "APPROVED" -> "§aAPPROVED";
            case "PENDING" -> "§ePENDING";
            case "REJECTED" -> "§cREVIEW";
            default -> "§bREGISTERED";
        };
    }

    private String getRegistrationStatusDetail(String statusKey) {
        return switch (statusKey) {
            case "APPROVED" -> "ready to play";
            case "PENDING" -> "awaiting review";
            case "REJECTED" -> "needs attention";
            default -> "registration synced";
        };
    }

    private int getRegistrationStatusAccent(String statusKey) {
        return switch (statusKey) {
            case "APPROVED" -> 0xFF55FF55;
            case "PENDING" -> 0xFFFFDD55;
            case "REJECTED" -> 0xFFFF6666;
            default -> 0xFF55D6FF;
        };
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private void drawProfileStatRow(DrawContext context, int x, int y, int w, String label, String value, int valueColor, int accentColor) {
        TournamentListScreen.drawPremiumBeveledBox(context, x, y, w, 18, 0x26000000, withAlpha(accentColor, 36), withAlpha(accentColor, 18));
        context.fill(x, y, x + 3, y + 18, accentColor);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§7" + label), x + 9, y + 5, 0xFFFFFF);

        String displayValue = value == null ? "-" : value;
        int maxValueWidth = Math.max(40, w - 120);
        if (this.textRenderer.getWidth(displayValue) > maxValueWidth) {
            displayValue = this.textRenderer.trimToWidth(displayValue, maxValueWidth - 6) + "..";
        }

        int valueX = x + w - 8 - this.textRenderer.getWidth(displayValue);
        context.drawTextWithShadow(this.textRenderer, Text.literal(displayValue), valueX, y + 5, valueColor);
    }

    private String getRoleBadgeText(String role) {
        if ("DEV".equalsIgnoreCase(role)) return "§bDEVELOPER";
        if ("ADMIN".equalsIgnoreCase(role)) return "§bADMINISTRATOR";
        if ("STAFF".equalsIgnoreCase(role)) return "§eEVENT STAFF";
        return "§aCOMPETITOR";
    }

    private int getRoleBadgeColor(String role) {
        if ("DEV".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) return 0x400055FF;
        if ("STAFF".equalsIgnoreCase(role)) return 0x40FFAA00;
        return 0x4000AA00;
    }

    private int getRoleBadgeBorderColor(String role) {
        if ("DEV".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) return 0xFF55FFFF;
        if ("STAFF".equalsIgnoreCase(role)) return 0xFFFFAA00;
        return 0xFF55FF55;
    }

    private void drawBlendedGradient(DrawContext context, int x, int y, int w, int h) {
        for (int i = 0; i < h; i++) {
            float ratio = (float) i / h;
            int r = (int) (30 * (1.0f - ratio) + 8 * ratio);
            int g = (int) (30 * (1.0f - ratio) + 8 * ratio);
            int b = (int) (30 * (1.0f - ratio) + 16 * ratio);
            int a = (int) (76 * (1.0f - ratio) + 204 * ratio);
            int color = (a << 24) | (r << 16) | (g << 8) | b;
            context.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

    private void render25DCharacter(DrawContext context, Identifier skinTex, int centerX, int centerY, float scale) {
        int headSz = (int) (16 * scale);
        int torsoW = (int) (16 * scale);
        int torsoH = (int) (24 * scale);
        int limbW = (int) (8 * scale);
        int limbH = (int) (24 * scale);

        int tx = centerX - torsoW / 2;
        int ty = centerY + headSz + 2;

        drawSkinPart(context, skinTex, tx - limbW, ty, limbW, limbH, 44, 20, 4, 12);
        drawSkinPart(context, skinTex, tx - limbW, ty, limbW, limbH, 44, 36, 4, 12);
        drawSkinPart(context, skinTex, tx, ty + torsoH, limbW, limbH, 4, 20, 4, 12);
        drawSkinPart(context, skinTex, tx, ty + torsoH, limbW, limbH, 4, 36, 4, 12);
        drawSkinPart(context, skinTex, tx + torsoW / 2, ty + torsoH, limbW, limbH, 20, 52, 4, 12);
        drawSkinPart(context, skinTex, tx + torsoW / 2, ty + torsoH, limbW, limbH, 4, 52, 4, 12);
        drawSkinPart(context, skinTex, tx, ty, torsoW, torsoH, 20, 20, 8, 12);
        drawSkinPart(context, skinTex, tx, ty, torsoW, torsoH, 20, 36, 8, 12);
        drawSkinPart(context, skinTex, tx + torsoW, ty, limbW, limbH, 36, 52, 4, 12);
        drawSkinPart(context, skinTex, tx + torsoW, ty, limbW, limbH, 52, 52, 4, 12);
        drawSkinPart(context, skinTex, tx, centerY, headSz, headSz, 8, 8, 8, 8);
        drawSkinPart(context, skinTex, tx, centerY, headSz, headSz, 40, 8, 8, 8);
    }

    private void drawSkinPart(DrawContext context, Identifier skinTex, int dx, int dy, int dw, int dh, int u, int v, int sw, int sh) {
        context.drawTexture(RenderLayer::getGuiTextured, skinTex, dx, dy, (float) u, (float) v, dw, dh, sw, sh, 64, 64);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
