package com.vaultop.mod.gui;

import com.google.gson.JsonObject;
import com.vaultop.mod.VaultOPMod;
import com.vaultop.mod.api.WebSocketMessageListener;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class HomeScreen extends Screen implements WebSocketMessageListener {
    private final Screen parent;
    private final List<JsonObject> announcements = new ArrayList<>();
    private String loadingStatus = "Loading announcements...";
    private int scrollOffset = 0;

    public HomeScreen(Screen parent) {
        super(Text.literal("VaultOP Home"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildWidgets();
        fetchAnnouncements();
    }

    private void rebuildWidgets() {
        this.clearChildren();

        // 1. Back button pushed to the extreme top-left corner
        this.addDrawableChild(new PremiumButtonWidget(10, 10, 40, 18, Text.literal("Back"), button -> close(), 0xFF8B2B2B, 0xFF4A1010, 0xFFE57373));

        // 2. Navigation buttons stacked in the extreme bottom-left corner
        int btnW = 90;
        int btnH = 18;
        int spacing = 4;
        
        int startY = this.height - 10 - (2 * btnH) - (1 * spacing);

        this.addDrawableChild(new PremiumButtonWidget(10, startY, btnW, btnH, Text.literal("Tournaments"), button -> {
            this.client.setScreen(new TournamentListScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        this.addDrawableChild(new PremiumButtonWidget(10, startY + btnH + spacing, btnW, btnH, Text.literal("Profile"), button -> {
            this.client.setScreen(new ProfileScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));
    }

    private void fetchAnnouncements() {
        if (VaultOPMod.getInstance().getRestClient() != null) {
            VaultOPMod.getInstance().getRestClient().fetchAnnouncements()
                .thenAccept(arr -> {
                    this.client.execute(() -> {
                        this.announcements.clear();
                        for (int i = 0; i < arr.size(); i++) {
                            this.announcements.add(arr.get(i).getAsJsonObject());
                        }
                        this.loadingStatus = this.announcements.isEmpty() ? "No active announcements." : "";
                    });
                })
                .exceptionally(ex -> {
                    this.client.execute(() -> {
                        this.loadingStatus = "Failed to load announcements.";
                    });
                    VaultOPMod.LOGGER.error("Failed to load announcements", ex);
                    return null;
                });
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int boardX = 115;
        int boardW = this.width - 127;
        int boardY = this.height - 105;
        int boardH = 75;

        if (mouseX >= boardX && mouseX <= boardX + boardW && mouseY >= boardY && mouseY <= boardY + boardH) {
            int scrollAreaH = boardH - 21;
            int totalHeight = announcements.size() * 42;
            int maxScroll = Math.max(0, totalHeight - scrollAreaH);
            if (verticalAmount != 0) {
                scrollOffset -= (int) Math.signum(verticalAmount) * 12;
                if (scrollOffset < 0) scrollOffset = 0;
                if (scrollOffset > maxScroll) scrollOffset = maxScroll;
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

        // 1. Draw outer premium dashboard container
        int panelX = 15;
        int panelY = 32;
        int panelW = this.width - 30;
        int panelH = this.height - 47;
        TournamentListScreen.drawPremiumBeveledBox(context, panelX, panelY, panelW, panelH, 0xD00A0E17, 0x302196F3, 0x152196F3);

        float tick = System.currentTimeMillis() / 50.0f;

        // 2. Draw Center Trophy
        int centerCX = panelX + (panelW) / 2;
        int trophyY = panelY + 28;
        drawBigTrophy(context, centerCX, trophyY, tick);

        // 3. Draw BIG, ANIMATED Title
        drawAnimatedTitle(context, centerCX, trophyY + 36, tick);

        // 4. Draw Bulletins & Announcements Board (Lower-Right Bottom Half)
        int boardX = 115;
        int boardW = this.width - 127;
        int boardY = this.height - 105;
        int boardH = 75;

        TournamentListScreen.drawPremiumBeveledBox(context, boardX, boardY, boardW, boardH, 0xE50B0C0E, 0x40D7A15C, 0x20D7A15C);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e📢 BULLETINS & ANNOUNCEMENTS"), boardX + boardW / 2, boardY + 4, 0xFFFFFF);
        context.fill(boardX + 8, boardY + 13, boardX + boardW - 8, boardY + 14, 0x20FFFFFF);

        int scrollAreaH = boardH - 21;
        if (!loadingStatus.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + loadingStatus), boardX + boardW / 2, boardY + 14 + scrollAreaH / 2 - 4, 0xFFFFFF);
        } else {
            context.enableScissor(boardX + 2, boardY + 15, boardX + boardW - 2, boardY + boardH - 4);

            int currentY = boardY + 16 - scrollOffset;
            int cardW = boardW - 16;

            for (JsonObject announcement : announcements) {
                String title = announcement.has("title") ? announcement.get("title").getAsString() : "";
                String desc = announcement.has("desc") ? announcement.get("desc").getAsString() : "";
                String category = announcement.has("type") ? announcement.get("type").getAsString() : "ANNOUNCEMENT";

                int typeBorderColor = 0x40808080;
                String categoryTag = "§7[GENERAL]";
                if ("RULES".equals(category)) {
                    typeBorderColor = 0x60FF5555;
                    categoryTag = "§c[RULES]";
                } else if ("SERVER".equals(category)) {
                    typeBorderColor = 0x6055FFFF;
                    categoryTag = "§b[SERVER]";
                } else if ("ANNOUNCEMENT".equals(category)) {
                    typeBorderColor = 0x6055FF55;
                    categoryTag = "§a[ALERT]";
                }

                // Draw card background
                TournamentListScreen.drawPremiumBeveledBox(context, boardX + 8, currentY, cardW, 38, 0x40000000, typeBorderColor, typeBorderColor / 2);

                // Category and title
                String displayTitle = title;
                if (this.textRenderer.getWidth(title) > cardW - 60) {
                    displayTitle = this.textRenderer.trimToWidth(title, cardW - 70) + "..";
                }
                context.drawTextWithShadow(this.textRenderer, Text.literal(categoryTag + " §e" + displayTitle), boardX + 14, currentY + 4, 0xFFFFFF);

                // Description
                int descY = currentY + 14;
                List<OrderedText> wrappedDesc = this.textRenderer.wrapLines(Text.literal("§f" + desc), cardW - 14);
                int lineCount = 0;
                for (OrderedText line : wrappedDesc) {
                    if (lineCount >= 2) break;
                    context.drawTextWithShadow(this.textRenderer, line, boardX + 14, descY + (lineCount * 9), 0xFFFFFF);
                    lineCount++;
                }

                currentY += 42;
            }

            context.disableScissor();

            // Scrollbar
            int totalHeight = announcements.size() * 42;
            int maxScroll = Math.max(0, totalHeight - scrollAreaH);
            if (maxScroll > 0) {
                int scrollbarX = boardX + boardW - 6;
                int scrollbarY = boardY + 16;
                context.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollAreaH, 0x22FFFFFF);

                int thumbHeight = (scrollAreaH * scrollAreaH) / (scrollAreaH + maxScroll);
                if (thumbHeight < 10) thumbHeight = 10;
                int thumbY = scrollbarY + (scrollOffset * (scrollAreaH - thumbHeight)) / maxScroll;
                context.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, 0x88FFFFFF);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawBigTrophy(DrawContext context, int cx, int cy, float tick) {
        float scale = 1.8f;
        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0);
        context.getMatrices().scale(scale, scale, 1.0f);

        int bob = (int) (Math.sin(tick * 0.12) * 1.5);
        int y = bob;

        int gold = 0xFFFFD700;
        int shadowGold = 0xFFB8860B;
        int shine = 0xFFFFF8C4;
        int baseColor = 0xFF3C464F;

        // Rim
        context.fill(-10, y - 18, 10, y - 16, gold);
        context.fill(-8, y - 16, 8, y - 14, gold);

        // Bowl
        context.fill(-8, y - 14, 8, y - 6, gold);
        context.fill(-6, y - 14, -4, y - 8, shine);
        context.fill(4, y - 14, 8, y - 6, shadowGold);

        // Left Handle
        context.fill(-13, y - 14, -10, y - 12, gold);
        context.fill(-15, y - 12, -13, y - 6, gold);
        context.fill(-13, y - 6, -10, y - 4, gold);

        // Right Handle
        context.fill(10, y - 14, 13, y - 12, gold);
        context.fill(13, y - 12, 15, y - 6, gold);
        context.fill(10, y - 6, 13, y - 4, gold);

        // Stem
        context.fill(-3, y - 6, 3, y + 2, gold);
        context.fill(1, y - 6, 3, y + 2, shadowGold);

        // Base
        context.fill(-6, y + 2, 6, y + 4, shadowGold);
        context.fill(-8, y + 4, 8, y + 8, baseColor);
        context.fill(-7, y + 5, -5, y + 7, 0x88FFFFFF);

        context.getMatrices().pop();
    }

    private void drawAnimatedTitle(DrawContext context, int centerX, int y, float tick) {
        String w1 = "VAULT";
        String w2 = "OP";
        String w3 = " TOURNAMENTS";

        int width1 = this.textRenderer.getWidth(w1);
        int width2 = this.textRenderer.getWidth(w2);
        int width3 = this.textRenderer.getWidth(w3);
        int totalWidth = width1 + width2 + width3;

        float titleScale = 2.2f + 0.12f * (float) Math.sin(tick * 0.08f);

        context.getMatrices().push();
        context.getMatrices().translate(centerX, y, 0);
        context.getMatrices().scale(titleScale, titleScale, 1.0f);

        // Center origin for the scaled string
        int startX = -totalWidth / 2;

        startX = drawWord(context, w1, startX, 0, tick, 0, 0xFFFFD700, 0xFFFFFF55);
        startX = drawWord(context, w2, startX, 0, tick, w1.length(), 0xFF55FF55, 0xFF55FFFF);
        startX = drawWord(context, w3, startX, 0, tick, w1.length() + w2.length(), 0xFFFFFFFF, 0xFFCCCCCC);

        context.getMatrices().pop();
    }

    private int drawWord(DrawContext context, String text, int startX, int y, float tick, int offset, int col1, int col2) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);
            int charW = this.textRenderer.getWidth(charStr);

            float floatY = (float) Math.sin((tick * 0.15) + ((i + offset) * 0.4)) * 1.0f;
            int color = getAnimatedColor(col1, col2, tick, i + offset);
            context.drawTextWithShadow(this.textRenderer, Text.literal(charStr), startX, y + (int) floatY, color);
            startX += charW;
        }
        return startX;
    }

    private int getAnimatedColor(int baseColor1, int baseColor2, float tick, int index) {
        float pulse = (float) Math.sin(tick * 0.06f + index * 0.3f);
        float ratio = (pulse + 1.0f) / 2.0f;
        int r1 = (baseColor1 >> 16) & 0xFF;
        int g1 = (baseColor1 >> 8) & 0xFF;
        int b1 = baseColor1 & 0xFF;
        int r2 = (baseColor2 >> 16) & 0xFF;
        int g2 = (baseColor2 >> 8) & 0xFF;
        int b2 = baseColor2 & 0xFF;
        int r = (int) (r1 * (1.0f - ratio) + r2 * ratio);
        int g = (int) (g1 * (1.0f - ratio) + g2 * ratio);
        int b = (int) (b1 * (1.0f - ratio) + b2 * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public void onWebSocketMessage(JsonObject json) {
        if (json.has("type") && !json.get("type").isJsonNull()) {
            String type = json.get("type").getAsString();
            if ("ANNOUNCEMENT".equals(type)) {
                this.client.execute(this::fetchAnnouncements);
            }
        }
    }
}
