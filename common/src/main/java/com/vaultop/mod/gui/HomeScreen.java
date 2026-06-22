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
        int panelX = 20;
        int panelY = 25;
        int panelW = this.width - 40;
        int panelH = this.height - 45;

        // 1. Back button at top-left
        int backX = panelX + 10;
        int backY = panelY + 10;
        this.addDrawableChild(new PremiumButtonWidget(backX, backY, 40, 20, Text.literal("Back"), button -> close(), 0xFF8B2B2B, 0xFF4A1010, 0xFFE57373));

        // 2. Navigation buttons stacked in the bottom-left corner with margin
        int navBtnW = 100;
        int navBtnH = 20;
        int navBtnX = panelX + 10;
        int navBtnY = panelY + panelH - 10 - (3 * navBtnH) - (2 * 4); // margin at bottom is 10, spacing is 4

        this.addDrawableChild(new PremiumButtonWidget(navBtnX, navBtnY, navBtnW, navBtnH, Text.literal("Tournaments"), button -> {
            this.client.setScreen(new TournamentListScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        this.addDrawableChild(new PremiumButtonWidget(navBtnX, navBtnY + 24, navBtnW, navBtnH, Text.literal("Profile"), button -> {
            this.client.setScreen(new ProfileScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        this.addDrawableChild(new PremiumButtonWidget(navBtnX, navBtnY + 48, navBtnW, navBtnH, Text.literal("Leaderboard"), button -> {
            this.client.setScreen(new LeaderboardScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        fetchAnnouncements();
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
        int panelX = 20;
        int panelY = 25;
        int panelW = this.width - 40;
        int panelH = this.height - 45;

        int boardX = panelX + 120;
        int boardY = panelY + panelH / 2 + 10;
        int boardW = panelW - 130;
        int boardH = panelH / 2 - 20;

        if (mouseX >= boardX && mouseX <= boardX + boardW && mouseY >= boardY && mouseY <= boardY + boardH) {
            int scrollAreaH = boardH - 25;
            int totalHeight = announcements.size() * 50;
            int maxScroll = Math.max(0, totalHeight - scrollAreaH);
            if (verticalAmount != 0) {
                scrollOffset -= (int) Math.signum(verticalAmount) * 15;
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

        int panelX = 20;
        int panelY = 25;
        int panelW = this.width - 40;
        int panelH = this.height - 45;

        // 1. Draw Main Center Dashboard Container
        TournamentListScreen.drawPremiumBeveledBox(context, panelX, panelY, panelW, panelH, 0xD00A0E17, 0x302196F3, 0x152196F3);

        float tick = System.currentTimeMillis() / 50.0f;

        // 2. Draw Left Stats Panel (Competitors Quick Stats)
        int leftStatsX = panelX + 10;
        int leftStatsY = panelY + 35;
        int leftStatsW = 100;
        int leftStatsH = 52;
        TournamentListScreen.drawPremiumBeveledBox(context, leftStatsX, leftStatsY, leftStatsW, leftStatsH, 0x8005080E, 0x20FFFFFF, 0x10FFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§9COMPETITORS"), leftStatsX + leftStatsW / 2, leftStatsY + 4, 0xFFFFFF);
        context.fill(leftStatsX + 6, leftStatsY + 13, leftStatsX + leftStatsW - 6, leftStatsY + 14, 0x20FFFFFF);
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("§7Tourneys: §e20+"), leftStatsX + 8, leftStatsY + 18, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§7Registered:"), leftStatsX + 8, leftStatsY + 30, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§b1,000+"), leftStatsX + 8, leftStatsY + 40, 0xFFFFFF);

        // 3. Draw Right Stats Panel (Prize Pool & Active Servers Stats)
        int rightStatsX = panelX + panelW - 110;
        int rightStatsY = panelY + 10;
        int rightStatsW = 100;
        int rightStatsH = 52;
        TournamentListScreen.drawPremiumBeveledBox(context, rightStatsX, rightStatsY, rightStatsW, rightStatsH, 0x8005080E, 0x20FFFFFF, 0x10FFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§eSTATS"), rightStatsX + rightStatsW / 2, rightStatsY + 4, 0xFFFFFF);
        context.fill(rightStatsX + 6, rightStatsY + 13, rightStatsX + rightStatsW - 6, rightStatsY + 14, 0x20FFFFFF);
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("§7Prizes: §63krs+"), rightStatsX + 8, rightStatsY + 18, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§7Servers:"), rightStatsX + 8, rightStatsY + 30, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§a5 Online"), rightStatsX + 8, rightStatsY + 40, 0xFFFFFF);

        // 4. Draw Center Trophy & Floating Title
        int centerCX = panelX + 110 + (panelW - 220) / 2;
        int trophyY = panelY + 12;
        drawBigTrophy(context, centerCX, trophyY, tick);
        drawAnimatedTitle(context, centerCX, trophyY + 32, tick);

        // 5. Draw Bulletins & Announcements Board (Lower-Right Half)
        int boardX = panelX + 120;
        int boardY = panelY + panelH / 2 + 10;
        int boardW = panelW - 130;
        int boardH = panelH / 2 - 20;

        TournamentListScreen.drawPremiumBeveledBox(context, boardX, boardY, boardW, boardH, 0xE50B0C0E, 0x40D7A15C, 0x20D7A15C);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e📢 BULLETINS & ANNOUNCEMENTS"), boardX + boardW / 2, boardY + 6, 0xFFFFFF);
        context.fill(boardX + 10, boardY + 17, boardX + boardW - 10, boardY + 18, 0x20FFFFFF);

        // Scissor clip area for scrollable announcements list
        int scrollAreaH = boardH - 25;
        if (!loadingStatus.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + loadingStatus), boardX + boardW / 2, boardY + 22 + scrollAreaH / 2 - 4, 0xFFFFFF);
        } else {
            context.enableScissor(boardX + 2, boardY + 20, boardX + boardW - 2, boardY + boardH - 4);

            int currentY = boardY + 22 - scrollOffset;
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
                TournamentListScreen.drawPremiumBeveledBox(context, boardX + 8, currentY, cardW, 46, 0x40000000, typeBorderColor, typeBorderColor / 2);

                // Category tag
                context.drawTextWithShadow(this.textRenderer, Text.literal(categoryTag), boardX + 14, currentY + 4, 0xFFFFFF);

                // Title
                String truncatedTitle = title;
                if (this.textRenderer.getWidth(title) > cardW - 20) {
                    truncatedTitle = this.textRenderer.trimToWidth(title, cardW - 30) + "...";
                }
                context.drawTextWithShadow(this.textRenderer, Text.literal("§e" + truncatedTitle), boardX + 14, currentY + 14, 0xFFFFFF);

                // Description
                int descY = currentY + 25;
                List<OrderedText> wrappedDesc = this.textRenderer.wrapLines(Text.literal("§f" + desc), cardW - 14);
                int lineCount = 0;
                for (OrderedText line : wrappedDesc) {
                    if (lineCount >= 2) break;
                    context.drawTextWithShadow(this.textRenderer, line, boardX + 14, descY + (lineCount * 9), 0xFFFFFF);
                    lineCount++;
                }

                currentY += 50;
            }

            context.disableScissor();

            // Draw Scrollbar if content exceeds height
            int totalHeight = announcements.size() * 50;
            int maxScroll = Math.max(0, totalHeight - scrollAreaH);
            if (maxScroll > 0) {
                int scrollbarX = boardX + boardW - 6;
                int scrollbarY = boardY + 22;
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
        int bob = (int) (Math.sin(tick * 0.12) * 2.5);
        int y = cy + bob;

        int gold = 0xFFFFD700;
        int shadowGold = 0xFFB8860B;
        int shine = 0xFFFFF8C4;
        int baseColor = 0xFF3C464F;

        // Rim
        context.fill(cx - 10, y - 18, cx + 10, y - 16, gold);
        context.fill(cx - 8, y - 16, cx + 8, y - 14, gold);

        // Bowl
        context.fill(cx - 8, y - 14, cx + 8, y - 6, gold);
        context.fill(cx - 6, y - 14, cx - 4, y - 8, shine);
        context.fill(cx + 4, y - 14, cx + 8, y - 6, shadowGold);

        // Left Handle
        context.fill(cx - 13, y - 14, cx - 10, y - 12, gold);
        context.fill(cx - 15, y - 12, cx - 13, y - 6, gold);
        context.fill(cx - 13, y - 6, cx - 10, y - 4, gold);

        // Right Handle
        context.fill(cx + 10, y - 14, cx + 13, y - 12, gold);
        context.fill(cx + 13, y - 12, cx + 15, y - 6, gold);
        context.fill(cx + 10, y - 6, cx + 13, y - 4, gold);

        // Stem
        context.fill(cx - 3, y - 6, cx + 3, y + 2, gold);
        context.fill(cx + 1, y - 6, cx + 3, y + 2, shadowGold);

        // Base
        context.fill(cx - 6, y + 2, cx + 6, y + 4, shadowGold);
        context.fill(cx - 8, y + 4, cx + 8, y + 8, baseColor);
        context.fill(cx - 7, y + 5, cx - 5, y + 7, 0x88FFFFFF);
    }

    private void drawAnimatedTitle(DrawContext context, int centerX, int y, float tick) {
        String w1 = "VAULT";
        String w2 = "OP";
        String w3 = " TOURNAMENTS";

        int width1 = this.textRenderer.getWidth(w1);
        int width2 = this.textRenderer.getWidth(w2);
        int width3 = this.textRenderer.getWidth(w3);
        int totalWidth = width1 + width2 + width3;

        int startX = centerX - totalWidth / 2;

        drawWord(context, w1, startX, y, tick, 0, 0xFFFFD700);
        startX += width1;

        drawWord(context, w2, startX, y, tick, w1.length(), 0xFF55FF55);
        startX += width2;

        drawWord(context, w3, startX, y, tick, w1.length() + w2.length(), 0xFFFFFFFF);
    }

    private void drawWord(DrawContext context, String text, int startX, int y, float tick, int offset, int color) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);
            int charW = this.textRenderer.getWidth(charStr);

            float floatY = (float) Math.sin((tick * 0.15) + ((i + offset) * 0.4)) * 2.0f;
            context.drawTextWithShadow(this.textRenderer, Text.literal(charStr), startX, y + (int) floatY, color);
            startX += charW;
        }
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
