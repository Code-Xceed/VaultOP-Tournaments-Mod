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

    public HomeScreen(Screen parent) {
        super(Text.literal("VaultOP Home"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 110;
        int buttonHeight = 20;
        
        // Navigation buttons aligned on the left side of the panel
        int startX = 35;
        int startY = 65;
        
        this.addDrawableChild(new PremiumButtonWidget(startX, startY, buttonWidth, buttonHeight, Text.literal("Tournaments"), button -> {
            this.client.setScreen(new TournamentListScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        this.addDrawableChild(new PremiumButtonWidget(startX, startY + 24, buttonWidth, buttonHeight, Text.literal("Profile"), button -> {
            this.client.setScreen(new ProfileScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        this.addDrawableChild(new PremiumButtonWidget(startX, startY + 48, buttonWidth, buttonHeight, Text.literal("Leaderboard"), button -> {
            this.client.setScreen(new LeaderboardScreen(this));
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        // Back to game button below
        this.addDrawableChild(new PremiumButtonWidget(startX, startY + 76, buttonWidth, buttonHeight, Text.literal("Back to Game"), button -> {
            this.client.setScreen(null); // Close the GUI and return to game
        }, 0xFF8B2B2B, 0xFF4A1010, 0xFFE57373));
        
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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier bgTex = Identifier.of("vaultop", "textures/gui/mod_bg_image.png");
        context.drawTexture(RenderLayer::getGuiTextured, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.fill(0, 0, this.width, this.height, 0x80050505);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // 1. Draw a main center dashboard container beveled panel
        int panelX = 20;
        int panelY = 25;
        int panelW = this.width - 40;
        int panelH = this.height - 45;
        
        // Draw the background panel: dark theme with subtle blue glow border
        TournamentListScreen.drawPremiumBeveledBox(context, panelX, panelY, panelW, panelH, 0xD00A0E17, 0x302196F3, 0x152196F3);
        
        // 2. Title header inside the container
        String titleStr = "§6§lVAULT§e§lOP §f§lTOURNAMENTS";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(titleStr), this.width / 2, panelY + 10, 0xFFFFFF);
        
        // Divider below title
        context.fill(panelX + 15, panelY + 24, panelX + panelW - 15, panelY + 25, 0x30FFFFFF);
        
        // 3. Right column: Announcements Board
        int boardX = panelX + 135;
        int boardY = panelY + 32;
        int boardW = panelW - 150;
        int boardH = panelH - 44;
        
        // Draw the wooden / dark steel bulletin board panel with gold borders
        TournamentListScreen.drawPremiumBeveledBox(context, boardX, boardY, boardW, boardH, 0xE50B0C0E, 0x40D7A15C, 0x20D7A15C); 
        
        // Bulletin Board Header
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e📢 BULLETINS & ANNOUNCEMENTS"), boardX + boardW / 2, boardY + 6, 0xFFFFFF);
        context.fill(boardX + 10, boardY + 17, boardX + boardW - 10, boardY + 18, 0x20FFFFFF);
        
        // Render announcements
        if (!loadingStatus.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + loadingStatus), boardX + boardW / 2, boardY + boardH / 2 - 4, 0xFFFFFF);
        } else {
            int currentY = boardY + 22;
            int cardW = boardW - 16;
            int count = 0;
            
            for (JsonObject announcement : announcements) {
                if (count >= 2) break; // Display max 2 announcements for spacing
                
                String title = announcement.has("title") ? announcement.get("title").getAsString() : "";
                String desc = announcement.has("desc") ? announcement.get("desc").getAsString() : "";
                String category = announcement.has("type") ? announcement.get("type").getAsString() : "ANNOUNCEMENT";
                
                // Color configuration based on type
                int typeBorderColor = 0x40808080;
                String categoryTag = "§7[GENERAL]";
                if ("RULES".equals(category)) {
                    typeBorderColor = 0x60FF5555; // Red
                    categoryTag = "§c[RULES]";
                } else if ("SERVER".equals(category)) {
                    typeBorderColor = 0x6055FFFF; // Cyan
                    categoryTag = "§b[SERVER]";
                } else if ("ANNOUNCEMENT".equals(category)) {
                    typeBorderColor = 0x6055FF55; // Green
                    categoryTag = "§a[ALERT]";
                }
                
                // Draw card container
                TournamentListScreen.drawPremiumBeveledBox(context, boardX + 8, currentY, cardW, 46, 0x40000000, typeBorderColor, typeBorderColor / 2);
                
                // Category tag
                context.drawTextWithShadow(this.textRenderer, Text.literal(categoryTag), boardX + 14, currentY + 4, 0xFFFFFF);
                
                // Announcement Title
                String truncatedTitle = title;
                if (this.textRenderer.getWidth(title) > cardW - 20) {
                    truncatedTitle = this.textRenderer.trimToWidth(title, cardW - 30) + "...";
                }
                context.drawTextWithShadow(this.textRenderer, Text.literal("§e" + truncatedTitle), boardX + 14, currentY + 14, 0xFFFFFF);
                
                // Description (with wrapping)
                int descY = currentY + 25;
                List<OrderedText> wrappedDesc = this.textRenderer.wrapLines(Text.literal("§f" + desc), cardW - 14);
                int lineCount = 0;
                for (OrderedText line : wrappedDesc) {
                    if (lineCount >= 2) break; // limit to 2 lines per card
                    context.drawTextWithShadow(this.textRenderer, line, boardX + 14, descY + (lineCount * 9), 0xFFFFFF);
                    lineCount++;
                }
                
                currentY += 50;
                count++;
            }
        }
        
        super.render(context, mouseX, mouseY, delta);
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
