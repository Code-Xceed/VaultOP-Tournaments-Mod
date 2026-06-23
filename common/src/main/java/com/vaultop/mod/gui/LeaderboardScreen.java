package com.vaultop.mod.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

public class LeaderboardScreen extends Screen {
    private final Screen parent;
    private final List<JsonObject> completedTournaments = new ArrayList<>();
    private JsonObject selectedTournament = null;
    private int selectedIndex = -1;
    private String statusText = "Loading leaderboards...";

    // 3D model properties
    @SuppressWarnings("rawtypes")
    private PlayerEntityModel playerModel;
    private float entityRotation = -20f; // Static 3D statue rotation angle matching web

    public LeaderboardScreen(Screen parent) {
        super(Text.literal("VaultOP Leaderboards"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ensureModelInit();
        rebuildWidgets();

        if (completedTournaments.isEmpty()) {
            VaultOPMod.getInstance().getRestClient().fetchTournaments()
                    .thenAccept(array -> {
                        this.client.execute(() -> {
                            completedTournaments.clear();
                            for (int i = 0; i < array.size(); i++) {
                                JsonObject t = array.get(i).getAsJsonObject();
                                JsonArray leaderboard = t.has("leaderboard") && !t.get("leaderboard").isJsonNull() 
                                        ? t.getAsJsonArray("leaderboard") 
                                        : null;
                                
                                if (leaderboard != null && leaderboard.size() > 0) {
                                    completedTournaments.add(t);
                                }
                            }
                            if (completedTournaments.isEmpty()) {
                                statusText = "No completed leaderboards found.";
                            } else {
                                statusText = "";
                                selectedIndex = 0;
                                selectedTournament = completedTournaments.get(0);
                            }
                            rebuildWidgets();
                        });
                    })
                    .exceptionally(ex -> {
                        this.client.execute(() -> {
                            statusText = "Failed to load leaderboards: " + ex.getMessage();
                        });
                        return null;
                    });
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void ensureModelInit() {
        if (playerModel != null) return;
        try {
            var client = MinecraftClient.getInstance();
            var loadedModels = client.getLoadedEntityModels();
            if (loadedModels == null) {
                VaultOPMod.LOGGER.info("[LeaderboardScreen] LoadedEntityModels is null, skipping init (will retry)");
                return;
            }
            ModelPart root = loadedModels.getModelPart(EntityModelLayers.PLAYER);
            playerModel = new PlayerEntityModel(root, false);
            VaultOPMod.LOGGER.info("[LeaderboardScreen] Successfully initialized 3D player model.");
        } catch (Exception e) {
            VaultOPMod.LOGGER.error("[LeaderboardScreen] Failed to initialize 3D player model:", e);
        }
    }

    private void rebuildWidgets() {
        this.clearChildren();

        // Back Button
        this.addDrawableChild(new PremiumButtonWidget(15, 15, 60, 20, Text.literal("◀ Back"), button -> close(), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        if (completedTournaments.size() > 1) {
            // Tournament Selector Arrow Left
            this.addDrawableChild(new PremiumButtonWidget(this.width / 2 - 130, 35, 20, 20, Text.literal("◀"), button -> {
                cycleTournament(-1);
            }, 0xFF3C464F, 0xFF0C0C0C, 0xFF4DEDF6));

            // Tournament Selector Arrow Right
            this.addDrawableChild(new PremiumButtonWidget(this.width / 2 + 110, 35, 20, 20, Text.literal("▶"), button -> {
                cycleTournament(1);
            }, 0xFF3C464F, 0xFF0C0C0C, 0xFF4DEDF6));
        }
    }

    private void cycleTournament(int direction) {
        if (completedTournaments.isEmpty()) return;
        selectedIndex = (selectedIndex + direction + completedTournaments.size()) % completedTournaments.size();
        selectedTournament = completedTournaments.get(selectedIndex);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier bgTex = Identifier.of("vaultop", "textures/gui/mod_bg_image.png");
        context.drawTexture(RenderLayer::getGuiTextured, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.fill(0, 0, this.width, this.height, 0x85050505);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Header Title
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("VAULTOP LEADERBOARDS"), this.width / 2, 18, 0xFF4DEDF6);

        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), this.width / 2, this.height / 2, 0xAAAAAA);
        }

        if (selectedTournament != null) {
            // Event Name inside selector
            String tName = selectedTournament.get("name").getAsString();
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("EVENT: " + tName), this.width / 2, 41, 0xFFFFAA00);

            // Fetch leaderboard array
            JsonArray leaderboard = selectedTournament.has("leaderboard") && !selectedTournament.get("leaderboard").isJsonNull()
                    ? selectedTournament.getAsJsonArray("leaderboard")
                    : new JsonArray();

            int centerX = this.width / 2;

            // 1. Render Top 3 Podium
            if (leaderboard.size() > 0) {
                // 1ST PLACE
                JsonObject first = leaderboard.get(0).getAsJsonObject();
                renderPodiumCard(context, first, 1, centerX, 120, 85, 0xFFFFCA3A, centerX - 45, 62, 90, 110);
            }

            if (leaderboard.size() > 1) {
                // 2ND PLACE
                JsonObject second = leaderboard.get(1).getAsJsonObject();
                renderPodiumCard(context, second, 2, centerX - 95, 125, 75, 0xFF4DEDF6, centerX - 135, 70, 80, 100);
            }

            if (leaderboard.size() > 2) {
                // 3RD PLACE
                JsonObject third = leaderboard.get(2).getAsJsonObject();
                renderPodiumCard(context, third, 3, centerX + 95, 128, 70, 0xFFE0A96D, centerX + 55, 74, 80, 96);
            }

            // 2. Render Lower Ranks (Ranks 4+)
            int tableY = 180;
            if (leaderboard.size() > 3) {
                // Table header
                context.drawTextWithShadow(this.textRenderer, Text.literal("Rank   Player IGN           Discord Username      Prize"), centerX - 180, tableY, 0x55FFFF);
                
                int maxRows = Math.min(leaderboard.size(), 8);
                for (int i = 3; i < maxRows; i++) {
                    JsonObject entry = leaderboard.get(i).getAsJsonObject();
                    int rankVal = entry.has("rank") ? entry.get("rank").getAsInt() : (i + 1);
                    String ign = entry.has("ign") ? entry.get("ign").getAsString() : "Player";
                    String username = entry.has("username") ? "@" + entry.get("username").getAsString() : "";
                    String prize = entry.has("prize") ? entry.get("prize").getAsString() : "";

                    int y = tableY + 14 + ((i - 3) * 15);
                    
                    // Background stripe
                    if ((i % 2) == 0) {
                        context.fill(centerX - 185, y - 2, centerX + 185, y + 10, 0x1AFFFFFF);
                    } else {
                        context.fill(centerX - 185, y - 2, centerX + 185, y + 10, 0x0DFFFFFF);
                    }

                    // Rank
                    context.drawTextWithShadow(this.textRenderer, Text.literal("#" + rankVal), centerX - 178, y, 0xAAAAAA);

                    // Player Skin Head + IGN
                    String skinUrl = VaultOPMod.getInstance().getConfigManager().getBackendUrl() + "/api/skin-proxy/" + ign + "?type=PREMIUM";
                    Identifier skinTex = DynamicTextureLoader.getOrLoad(skinUrl, "skin_" + ign);
                    if (skinTex != null) {
                        // Draw head (8,8 to 16,16) and overlay (40,8 to 48,16)
                        context.drawTexture(RenderLayer::getGuiTextured, skinTex, centerX - 145, y - 1, 8f, 8f, 8, 8, 8, 8, 64, 64);
                        context.drawTexture(RenderLayer::getGuiTextured, skinTex, centerX - 145, y - 1, 40f, 8f, 8, 8, 8, 8, 64, 64);
                    }
                    context.drawTextWithShadow(this.textRenderer, Text.literal(ign), centerX - 132, y, 0xFFFFFF);

                    // Discord
                    context.drawTextWithShadow(this.textRenderer, Text.literal(username), centerX - 25, y, 0x55FF55);

                    // Prize
                    context.drawTextWithShadow(this.textRenderer, Text.literal(prize), centerX + 95, y, 0xFFFFCA3A);
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPodiumCard(DrawContext context, JsonObject entry, int rank, int statueX, int statueY, int statueHeight, int themeColor, int boxX, int boxY, int boxW, int boxH) {
        String ign = entry.has("ign") ? entry.get("ign").getAsString() : "Player";
        String username = entry.has("username") ? "@" + entry.get("username").getAsString() : "";
        String prize = entry.has("prize") ? entry.get("prize").getAsString() : "";

        // 1. Draw Card Box
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x901F1F1F);
        drawBorder(context, boxX, boxY, boxW, boxH, themeColor);

        // 2. Draw 3D Character Statue
        String skinUrl = VaultOPMod.getInstance().getConfigManager().getBackendUrl() + "/api/skin-proxy/" + ign + "?type=PREMIUM";
        Identifier skinTex = DynamicTextureLoader.getOrLoad(skinUrl, "skin_" + ign);
        if (skinTex == null) {
            String fallbackUrl = VaultOPMod.getInstance().getConfigManager().getBackendUrl() + "/api/skin-proxy/Steeeve?type=PREMIUM";
            skinTex = DynamicTextureLoader.getOrLoad(fallbackUrl, "skin_steeeve");
        }

        if (skinTex != null) {
            // Renders 3D player model. If it fails, falls back to 2.5D character
            if (!render3DPlayerModel(context, skinTex, entityRotation, statueX, statueY, statueHeight)) {
                float scale = 1.0f;
                if (rank == 1) scale = 1.2f;
                render25DCharacter(context, skinTex, statueX, statueY - 35, scale);
            }
        }

        // 3. Draw Labels
        String rankLabel = rank == 1 ? "1ST PLACE" : rank == 2 ? "2ND PLACE" : "3RD PLACE";
        
        // Render Rank Badge
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(rankLabel), statueX, boxY + boxH - 42, themeColor);

        // Render IGN
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(ign), statueX, boxY + boxH - 30, 0xFFFFFF);

        // Render Discord Username
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(username), statueX, boxY + boxH - 20, 0x55FF55);

        // Render Prize Reward
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(prize), statueX, boxY + boxH - 10, 0xFFFFCA3A);
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color); // top
        context.fill(x, y + h - 1, x + w, y + h, color); // bottom
        context.fill(x, y, x + 1, y + h, color); // left
        context.fill(x + w - 1, y, x + w, y + h, color); // right
    }

    @SuppressWarnings("unchecked")
    private boolean render3DPlayerModel(DrawContext context, Identifier skinTexture, float rotation, int centerX, int centerY, int panelHeight) {
        ensureModelInit();
        if (playerModel == null) return false;

        try {
            resetModelPose();
            context.draw();

            // Calculate scale to fit panel height
            float scale = (panelHeight - 30) / 2.0f;

            context.getMatrices().push();
            context.getMatrices().translate(centerX, centerY - 0.5f * scale, 50.0f);
            context.getMatrices().scale(scale, scale, scale);

            // Apply user rotation
            Quaternionf bodyRot = new Quaternionf().rotateY((float) Math.toRadians(rotation));
            context.getMatrices().multiply(bodyRot);

            DiffuseLighting.enableGuiDepthLighting();

            VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer consumer = immediate.getBuffer(RenderLayer.getEntityTranslucent(skinTexture));

            int light = 0xF000F0; // full brightness
            int overlay = OverlayTexture.DEFAULT_UV;
            playerModel.render(context.getMatrices(), consumer, light, overlay, 0xFFFFFFFF);

            immediate.draw();
            context.getMatrices().pop();
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
