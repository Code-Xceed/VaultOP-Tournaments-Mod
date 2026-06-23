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

    private void rebuildWidgets() {
        this.clearChildren();

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

            int leftTopH = 70;
            int leftBotY = leftY + leftTopH + 10;
            int leftBotH = leftH - leftTopH - 10;

            int eventY = leftBotY + 26 - rightScrollOffset;
            int cardW = leftW - 20;

            for (JsonObject event : registeredEvents) {
                int cardY = eventY;
                boolean isBtnVisible = (cardY >= leftBotY + 22 && cardY + 46 <= leftBotY + leftBotH - 8);
                
                if (isBtnVisible) {
                    int viewBtnX = leftX + 12 + cardW - 40;
                    int viewBtnY = cardY + 14;
                    
                    this.addDrawableChild(new PremiumButtonWidget(viewBtnX, viewBtnY, 34, 16, Text.literal("View"), button -> {
                        this.client.setScreen(new TournamentDetailScreen(this, event));
                    }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));
                }
                eventY += 50;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && profileData != null) {
            int panelX = 15;
            int panelY = 48;
            int panelW = this.width - 30;
            int panelH = this.height - 78;
            int leftW = (int) (panelW * 0.50) - 10;
            int rightX = panelX + 8 + leftW + 8;
            int rightY = panelY + 8;
            int rightW = panelW - leftW - 24;
            int rightH = panelH - 16;

            if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= rightY && mouseY <= rightY + rightH) {
                isDraggingEntity = true;
                lastDragX = mouseX;
                return true;
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
        int panelX = 15;
        int panelY = 48;
        int panelW = this.width - 30;
        int panelH = this.height - 78;

        int leftX = panelX + 8;
        int leftY = panelY + 8;
        int leftW = (int) (panelW * 0.50) - 10;
        int leftH = panelH - 16;

        int leftTopH = 70;
        int leftBotY = leftY + leftTopH + 10;
        int leftBotH = leftH - leftTopH - 10;

        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= leftBotY && mouseY <= leftBotY + leftBotH) {
            int scrollAreaH = leftBotH - 28;
            int totalHeight = registeredEvents.size() * 50;
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

        // Top Header
        TournamentListScreen.drawPremiumBeveledBox(context, 0, -2, this.width, 42, 0xD00A0F18, 0x25FFFFFF, 0x10FFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("VaultOP Esports"), 15, 16, 0xFFFFD700);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Player Profile"), this.width / 2, 16, 0xFFFFFF);

        int panelX = 15;
        int panelY = 48;
        int panelW = this.width - 30;
        int panelH = this.height - 78;

        TournamentListScreen.drawPremiumBeveledBox(context, panelX, panelY, panelW, panelH, 0xD00A0E17, 0x302196F3, 0x152196F3);

        if (profileData == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + statusText), this.width / 2, this.height / 2, 0xFFFFFF);
        } else {
            com.google.gson.JsonObject userObj = profileData.has("user") ? profileData.getAsJsonObject("user") : null;
            String discordUser = "Unknown";
            String discordId = "N/A";
            String ign = "Not Linked";
            String accountType = "PREMIUM";

            if (userObj != null) {
                discordUser = userObj.has("username") ? userObj.get("username").getAsString() : "Unknown";
                discordId = userObj.has("discordId") ? userObj.get("discordId").getAsString() : "N/A";
                ign = userObj.has("ign") && !userObj.get("ign").isJsonNull() ? userObj.get("ign").getAsString() : "Not Linked";
                accountType = userObj.has("accountType") && !userObj.get("accountType").isJsonNull() ? userObj.get("accountType").getAsString() : "PREMIUM";
            }

            // Left Column
            int leftX = panelX + 8;
            int leftY = panelY + 8;
            int leftW = (int) (panelW * 0.50) - 10;
            int leftH = panelH - 16;
            int leftTopH = 70;
            int leftBotY = leftY + leftTopH + 10;
            int leftBotH = leftH - leftTopH - 10;

            // ── IDENTITY PANEL ──
            TournamentListScreen.drawPremiumBeveledBox(context, leftX, leftY, leftW, leftTopH, 0x8005080E, 0x20FFFFFF, 0x10FFFFFF);

            int avatarX = leftX + 16;
            int avatarY = leftY + 14;
            int avatarSize = 40;
            TournamentListScreen.drawPremiumBeveledBox(context, avatarX - 2, avatarY - 2, avatarSize + 4, avatarSize + 4, 0xFF080808, 0x40FFFFFF, 0x20FFFFFF);

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

            int infoX = avatarX + avatarSize + 14;
            int infoY = avatarY + 4;

            String truncatedName = discordUser;
            int maxNameWidth = leftW - (avatarSize + 50);
            if (this.textRenderer.getWidth(discordUser) > maxNameWidth) {
                truncatedName = this.textRenderer.trimToWidth(discordUser, maxNameWidth - 10) + "..";
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + truncatedName), infoX, infoY, 0xFFFFFF);

            String ignDisplay = "§7IGN: §f" + ign;
            if (!"Not Linked".equalsIgnoreCase(ign) && !ign.isEmpty()) {
                ignDisplay += " §8(§b" + accountType.toLowerCase() + "§8)";
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(ignDisplay), infoX, infoY + 14, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7Status: §aCONNECTED"), infoX, infoY + 26, 0xFFFFFF);

            // ── REGISTERED EVENTS ──
            TournamentListScreen.drawPremiumBeveledBox(context, leftX, leftBotY, leftW, leftBotH, 0x8005080E, 0x2055FF55, 0x1055FF55);
            context.drawTextWithShadow(this.textRenderer, Text.literal("§a⚔ Registered Events §7(" + registeredEvents.size() + ")"), leftX + 12, leftBotY + 8, 0xFFFFFF);
            context.fill(leftX + 10, leftBotY + 20, leftX + leftW - 10, leftBotY + 21, 0x18FFFFFF);

            int scrollAreaH = leftBotH - 28;
            if (!eventsLoadingStatus.isEmpty()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + eventsLoadingStatus), leftX + leftW / 2, leftBotY + 22 + scrollAreaH / 2 - 4, 0xFFFFFF);
            } else {
                context.enableScissor(leftX + 4, leftBotY + 22, leftX + leftW - 4, leftBotY + leftBotH - 8);

                int eventY = leftBotY + 26 - rightScrollOffset;
                int cardW = leftW - 20;

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

                    TournamentListScreen.drawPremiumBeveledBox(context, leftX + 12, eventY, cardW, 44, 0x40000000, 0x15FFFFFF, 0x0AFFFFFF);
                    String displayName = name;
                    int maxNameW = cardW - 140;
                    if (this.textRenderer.getWidth(name) > maxNameW) {
                        displayName = this.textRenderer.trimToWidth(name, maxNameW - 8) + "..";
                    }
                    context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + displayName), leftX + 20, eventY + 8, 0xFFFFFF);
                    context.drawTextWithShadow(this.textRenderer, Text.literal("§bv" + gameVersion), leftX + 20, eventY + 22, 0xFFFFFF);

                    int labelW = this.textRenderer.getWidth(formattedBadge) + 8;
                    int labelH = 12;
                    int labelX = leftX + 12 + cardW - labelW - 46;
                    int labelYInside = eventY + 16;
                    TournamentListScreen.drawPremiumBeveledBox(context, labelX, labelYInside, labelW, labelH, badgeBg, badgeBg * 2, badgeBg * 2);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(formattedBadge), labelX + 4, labelYInside + 2, 0xFFFFFF);

                    eventY += 50;
                }

                context.disableScissor();

                int totalHeight = registeredEvents.size() * 50;
                int maxScroll = Math.max(0, totalHeight - scrollAreaH);
                if (maxScroll > 0) {
                    int scrollbarX = leftX + leftW - 6;
                    int scrollbarY = leftBotY + 22;
                    context.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollAreaH, 0x22FFFFFF);
                    int thumbHeight = (scrollAreaH * scrollAreaH) / (scrollAreaH + maxScroll);
                    if (thumbHeight < 10) thumbHeight = 10;
                    int thumbY = scrollbarY + (rightScrollOffset * (scrollAreaH - thumbHeight)) / maxScroll;
                    context.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, 0x88FFFFFF);
                }
            }

            // ── RIGHT COLUMN: 3D SKIN VIEWER ──
            int rightX = leftX + leftW + 8;
            int rightY = panelY + 8;
            int rightW = panelW - leftW - 24;
            int rightH = panelH - 16;

            TournamentListScreen.drawPremiumBeveledBox(context, rightX, rightY, rightW, rightH, 0x00000000, 0x202196F3, 0x152196F3);
            drawBlendedGradient(context, rightX + 2, rightY + 2, rightW - 4, rightH - 4);

            // Nametag
            String nameTagText = discordUser;
            int tagWidth = this.textRenderer.getWidth(nameTagText);
            int tagX = rightX + (rightW - tagWidth) / 2;
            int tagY = rightY + 12;
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
                // Try actual 3D model rendering (works without joining a world)
                if (!render3DPlayerModel(context, skinTex, entityRotation, rightX + rightW / 2, rightY + rightH / 2 + 20, rightH)) {
                    // Fallback to 2D rendering
                    float skinScale = 1.4f;
                    int skinH = (int) (64 * skinScale);
                    int skinCenterY = rightY + (rightH - skinH) / 2 + 10;
                    render25DCharacter(context, skinTex, rightX + rightW / 2, skinCenterY, skinScale);
                }
            } else {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Loading skin..."), rightX + rightW / 2, rightY + rightH / 2, 0xFFFFFF);
            }

            // Drag hint
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8Drag to rotate"), rightX + rightW / 2, rightY + rightH - 14, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Renders a 3D player model from the skin texture using PlayerEntityModel.
     * Works WITHOUT needing a player entity or being in a world.
     * Returns true if rendering succeeded, false to indicate fallback needed.
     */
    @SuppressWarnings("unchecked")
    private boolean render3DPlayerModel(DrawContext context, Identifier skinTexture, float rotation, int centerX, int centerY, int panelHeight) {
        ensureModelInit();
        if (playerModel == null) return false;

        try {
            // Reset model to standing pose
            resetModelPose();

            // Flush any pending GUI draw calls so they render behind the model
            context.draw();

            // Calculate scale to fit panel height (~32 model units for a player)
            float scale = panelHeight * 0.11f; // scale to fit nicely

            context.getMatrices().push();

            // Position model at screen coordinates
            context.getMatrices().translate(centerX, centerY, 50.0f);

            // Scale: positive Y goes down in screen space, but model has Y-up, so negate Y
            context.getMatrices().scale(scale, -scale, scale);

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
    }

    private void resetPart(ModelPart part) {
        part.pitch = 0;
        part.yaw = 0;
        part.roll = 0;
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
