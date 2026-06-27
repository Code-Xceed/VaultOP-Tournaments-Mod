package com.vaultop.mod.gui;

import com.google.gson.JsonObject;
import com.vaultop.mod.VaultOPMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import com.vaultop.mod.api.WebSocketMessageListener;
import java.util.ArrayList;
import java.util.List;

public class TournamentListScreen extends Screen implements WebSocketMessageListener {
    private final Screen parent;
    private final List<JsonObject> allTournaments = new ArrayList<>();
    private final List<JsonObject> filteredTournaments = new ArrayList<>();
    private TextFieldWidget searchField;
    private String statusText = "Loading tournaments...";
    private int scrollOffset = 0;
    private String activeFilter = "ALL";

    public TournamentListScreen(Screen parent) {
        super(Text.literal("VaultOP Tournaments"));
        this.parent = parent;
    }

    public static String getStatusFromLabel(String label) {
        if (label == null) return "NOT_REGISTERED";
        String lower = label.toLowerCase();
        if (lower.contains("approved") || lower.contains("registered")) {
            return "APPROVED";
        } else if (lower.contains("pending")) {
            return "PENDING";
        } else if (lower.contains("rejected")) {
            return "REJECTED";
        }
        return "NOT_REGISTERED";
    }

    public static void drawBeveledBox(DrawContext context, int x, int y, int w, int h, int bgColor, int highlightColor, int shadowColor) {
        drawPremiumBeveledBox(context, x, y, w, h, bgColor, highlightColor, shadowColor);
    }

    public static void drawAnimatedStatusThumbnail(DrawContext context, int x, int y, int w, int h, String status, Identifier customThumb, net.minecraft.client.font.TextRenderer textRenderer) {
        long time = System.currentTimeMillis();
        int color1, color2;
        String badgeText;
        int badgeColor;
        
        if ("ONGOING".equals(status)) {
            float pulse = (float) (Math.sin(time * 0.005) + 1.0) / 2.0f;
            int redVal1 = (int) (40 + pulse * 25);
            int redVal2 = (int) (120 + pulse * 45);
            color1 = 0xFF000000 | (redVal1 << 16);
            color2 = 0xFF000000 | (redVal2 << 16) | (12 << 8) | 12;
            badgeText = "🔴 LIVE";
            badgeColor = 0xFFFF3333;
        } else if ("REG_OPEN".equals(status)) {
            color1 = 0xFF021727;
            color2 = 0xFF006F7C;
            badgeText = "🟢 OPEN";
            badgeColor = 0xFF22FFBB;
        } else if ("REG_CLOSED".equals(status)) {
            color1 = 0xFF211303;
            color2 = 0xFF5B3005;
            badgeText = "🔒 CLOSED";
            badgeColor = 0xFFFF9922;
        } else {
            color1 = 0xFF121212;
            color2 = 0xFF292929;
            badgeText = "🏆 COMPLETED";
            badgeColor = 0xFFAAAAAA;
        }
        
        if (customThumb != null) {
            context.drawTexture(RenderLayer::getGuiTextured, customThumb, x, y, 0f, 0f, w, h, w, h);
            context.draw();
        } else {
            int slices = 15;
            for (int slice = 0; slice < slices; slice++) {
                int sx1 = x + (slice * w) / slices;
                int sx2 = x + ((slice + 1) * w) / slices;
                float offset = (float) slice / slices;
                
                float wave;
                if ("ONGOING".equals(status) || "REG_OPEN".equals(status) || "REG_CLOSED".equals(status)) {
                    wave = (float) (Math.sin(time * 0.003 - offset * 4.0) + 1.0) / 2.0f;
                } else {
                    wave = 0.5f;
                }
                
                int r = (int) (((color1 >> 16) & 0xFF) * (1.0f - wave) + ((color2 >> 16) & 0xFF) * wave);
                int g = (int) (((color1 >> 8) & 0xFF) * (1.0f - wave) + ((color2 >> 8) & 0xFF) * wave);
                int b = (int) ((color1 & 0xFF) * (1.0f - wave) + (color2 & 0xFF) * wave);
                int sliceColor = 0xFF000000 | (r << 16) | (g << 8) | b;
                
                context.fill(sx1, y, sx2, y + h, sliceColor);
            }
        }
        
        context.fill(x, y, x + w, y + 1, 0x33FFFFFF);
        context.fill(x, y + h - 1, x + w, y + h, 0x11FFFFFF);
        
        int boxW = w - 16;
        int boxH = 16;
        int boxX = x + 8;
        int boxY = y + h/2 - boxH/2;
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x90000000);
        
        float textPulse = (float) (Math.sin(time * 0.006) + 1.0) / 2.0f;
        int alpha = (int) (180 + textPulse * 75);
        int finalBadgeColor = (alpha << 24) | (badgeColor & 0xFFFFFF);
        
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(badgeText), x + w/2, boxY + 4, finalBadgeColor);
    }

    public static void drawPremiumBeveledBox(DrawContext context, int x, int y, int w, int h, int bgColor, int highlightColor, int shadowColor) {
        if (bgColor != 0) {
            context.fill(x + 2, y + 2, x + w - 2, y + h - 2, bgColor);
        }
        context.fill(x, y, x + w, y + 1, highlightColor);
        context.fill(x + 1, y + 1, x + w - 1, y + 2, highlightColor);
        context.fill(x, y, x + 1, y + h, highlightColor);
        context.fill(x + 1, y + 1, x + 2, y + h - 1, highlightColor);
        context.fill(x, y + h - 1, x + w, y + h, shadowColor);
        context.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shadowColor);
        context.fill(x + w - 1, y, x + w, y + h, shadowColor);
        context.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, shadowColor);
    }

    private void refreshList() {
        VaultOPMod.getInstance().getRestClient().fetchTournaments()
                .thenAccept(array -> {
                    this.client.execute(() -> {
                        allTournaments.clear();
                        for (int i = 0; i < array.size(); i++) {
                            allTournaments.add(array.get(i).getAsJsonObject());
                        }
                        statusText = allTournaments.isEmpty() ? "No tournaments available." : "";
                        filterTournaments();
                    });
                })
                .exceptionally(ex -> {
                    this.client.execute(() -> {
                        statusText = "Failed to load: " + ex.getMessage();
                    });
                    return null;
                });
    }

    @Override
    protected void init() {
        // Setup Search Field in the center of the top bar
        this.searchField = new TextFieldWidget(this.textRenderer, this.width / 2 - 75, 10, 150, 20, Text.literal("Search"));
        this.searchField.setChangedListener(this::onSearchChanged);
        this.searchField.setDrawsBackground(false);

        rebuildWidgets();

        // Fetch Tournaments from REST API
        refreshList();
    }

    private void rebuildWidgets() {
        this.clearChildren();
        
        if (this.searchField != null) {
            this.searchField.setX(this.width / 2 - 75);
            this.searchField.setY(10);
            this.searchField.setWidth(150);
            this.addDrawableChild(this.searchField);
        }

        // Back button centered at the bottom of the screen
        this.addDrawableChild(new PremiumButtonWidget(this.width / 2 - 40, this.height - 25, 80, 20, Text.literal("Back"), button -> close(), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        // Refresh button next to Search field
        this.addDrawableChild(new PremiumButtonWidget(this.width / 2 + 82, 10, 20, 20, Text.literal("↻"), button -> {
            statusText = "Loading tournaments...";
            this.allTournaments.clear();
            this.filteredTournaments.clear();
            refreshList();
            VaultOPMod.getInstance().forceRefreshData();
        }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        // Horizontal filter buttons in right top corner
        int btnStartX = this.width - 235;
        this.addDrawableChild(new PremiumButtonWidget(btnStartX, 10, 70, 20, Text.literal("All"), button -> setFilter("ALL"), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3)
                .setSelectedSupplier(() -> "ALL".equals(activeFilter)));

        this.addDrawableChild(new PremiumButtonWidget(btnStartX + 75, 10, 70, 20, Text.literal("Active"), button -> setFilter("ACTIVE"), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3)
                .setSelectedSupplier(() -> "ACTIVE".equals(activeFilter)));

        this.addDrawableChild(new PremiumButtonWidget(btnStartX + 150, 10, 70, 20, Text.literal("Completed"), button -> setFilter("COMPLETED"), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3)
                .setSelectedSupplier(() -> "COMPLETED".equals(activeFilter)));

        // Grid details buttons
        int gridWidth = this.width - 30;
        int cols = Math.max(1, gridWidth / 190);

        int cardY = 50;
        for (int i = 0; i < filteredTournaments.size(); i++) {
            final JsonObject t = filteredTournaments.get(i);
            int row = i / cols;
            int col = i % cols;
            int x = 15 + col * 190;
            int y = cardY + row * 180 - scrollOffset;

            // Details button inside card
            boolean isVisible = (y >= 40 && y + 170 <= this.height - 30);
            PremiumButtonWidget detailsBtn = new PremiumButtonWidget(x + 10, y + 142, 160, 20, Text.literal("View Info"), button -> {
                this.client.setScreen(new TournamentDetailScreen(this, t));
            }, 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3);
            detailsBtn.visible = isVisible;
            this.addDrawableChild(detailsBtn);
        }
    }

    private void setFilter(String newFilter) {
        this.activeFilter = newFilter;
        filterTournaments();
    }

    private void onSearchChanged(String text) {
        filterTournaments();
    }

    private void filterTournaments() {
        filteredTournaments.clear();
        String query = searchField.getText().toLowerCase();
        for (JsonObject t : allTournaments) {
            String name = t.has("name") && !t.get("name").isJsonNull() ? t.get("name").getAsString().toLowerCase() : "";
            String mode = t.has("mode") && !t.get("mode").isJsonNull() ? t.get("mode").getAsString().toLowerCase() : "";
            String status = t.has("status") && !t.get("status").isJsonNull() ? t.get("status").getAsString() : "";
            
            boolean matchesSearch = name.contains(query) || mode.contains(query);
            boolean matchesFilter = false;

            if ("ACTIVE".equals(activeFilter)) {
                matchesFilter = "ONGOING".equals(status) || "REG_OPEN".equals(status) || "REG_CLOSED".equals(status);
            } else if ("COMPLETED".equals(activeFilter)) {
                matchesFilter = "COMPLETED".equals(status);
            } else {
                matchesFilter = true;
            }

            if (matchesSearch && matchesFilter) {
                filteredTournaments.add(t);
            }
        }
        
        int cardAreaHeight = this.height - 50 - 30;
        int cols = Math.max(1, (this.width - 30) / 190);
        int rows = (filteredTournaments.size() + cols - 1) / cols;
        int totalHeight = rows * 180;
        int maxScroll = Math.max(0, totalHeight - cardAreaHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            scrollOffset -= (int) Math.signum(verticalAmount) * 20;
            int cardAreaHeight = this.height - 50 - 30;
            int cols = Math.max(1, (this.width - 30) / 190);
            int rows = (filteredTournaments.size() + cols - 1) / cols;
            int totalHeight = rows * 180;
            int maxScroll = Math.max(0, totalHeight - cardAreaHeight);
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            rebuildWidgets();
            return true;
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

        // 1. Draw Top Header Panel (Height = 40) with double bevel
        drawPremiumBeveledBox(context, 0, -2, this.width, 42, 0xD00A0F18, 0x25FFFFFF, 0x10FFFFFF);
        
        // Brand logo in left corner
        context.drawTextWithShadow(this.textRenderer, Text.literal("VaultOP Esports"), 15, 16, 0xFFFFD700);

        // Draw premium bevel box around the search field text input (at this.width / 2 - 75, 10, 150, 20)
        drawPremiumBeveledBox(context, this.width / 2 - 77, 9, 154, 22, 0x8005080E, 0x30FFFFFF, 0x15FFFFFF);

        // 2. Draw Cards
        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), this.width / 2, this.height / 2, 0xFFAAAAAA);
        }

        int gridWidth = this.width - 30;
        int cols = Math.max(1, gridWidth / 190);

        int cardY = 50;
        for (int i = 0; i < filteredTournaments.size(); i++) {
            JsonObject t = filteredTournaments.get(i);
            int row = i / cols;
            int col = i % cols;
            int x = 15 + col * 190;
            int y = cardY + row * 180 - scrollOffset;

            // Render boundary check
            if (y + 170 < 40 || y > this.height - 30) continue;

            String name = t.has("name") && !t.get("name").isJsonNull() ? t.get("name").getAsString() : "Unnamed Tournament";
            String status = t.has("status") && !t.get("status").isJsonNull() ? t.get("status").getAsString() : "REG_CLOSED";
            String prizePool = t.has("prizePool") && !t.get("prizePool").isJsonNull() ? t.get("prizePool").getAsString() : "$0";
            String gameVersion = t.has("gameVersion") && !t.get("gameVersion").isJsonNull() ? t.get("gameVersion").getAsString() : "1.21.5";
            
            String label = "Not Registered";
            if (t.has("userStatusLabel") && !t.get("userStatusLabel").isJsonNull()) {
                label = t.get("userStatusLabel").getAsString();
            }
            String uStatus = getStatusFromLabel(label);

            // Hover state
            boolean isHovered = mouseX >= x && mouseX <= x + 180 && mouseY >= y && mouseY <= y + 170;
            int bgColor = isHovered ? 0xCF121B2A : 0x8F080C14;
            int highlightColor = isHovered ? 0xFF2196F3 : 0x30FFFFFF;
            int shadowColor = isHovered ? 0x802196F3 : 0x15FFFFFF;

            // Draw Beveled 3D Card (Actual solid card design, not floating)
            drawBeveledBox(context, x, y, 180, 170, bgColor, highlightColor, shadowColor);

            // Thumbnail Image Box
            int thumbX = x + 10;
            int thumbY = y + 8;
            int thumbW = 160;
            int thumbH = 76;

            // Preload image in background cache
            String thumbUrl = t.has("thumbnailUrl") && !t.get("thumbnailUrl").isJsonNull() ? t.get("thumbnailUrl").getAsString() : "";
            String tourneyId = t.has("id") && !t.get("id").isJsonNull() ? t.get("id").getAsString() : "unknown";
            Identifier customThumb = null;
            if (!thumbUrl.isEmpty()) {
                customThumb = DynamicTextureLoader.getOrLoad(thumbUrl, tourneyId);
            }

            // Draw dynamic animated status thumbnail
            drawAnimatedStatusThumbnail(context, thumbX, thumbY, thumbW, thumbH, status, customThumb, this.textRenderer);

            // Truncate name considering larger font
            String displayTitle = name;
            float titleScale = 1.15f;
            int maxTitleWidth = (int) (156 / titleScale);
            if (this.textRenderer.getWidth(displayTitle) > 156) {
                displayTitle = this.textRenderer.trimToWidth(displayTitle, maxTitleWidth - 4) + "...";
            }
            
            context.getMatrices().push();
            context.getMatrices().scale(titleScale, titleScale, 1.0f);
            int tx = (int) ((x + 12) / titleScale);
            int ty = (int) ((y + 90) / titleScale);
            context.drawTextWithShadow(this.textRenderer, Text.literal(displayTitle), tx, ty, isHovered ? 0xFF55FFFF : 0xFFFFFFFF);
            context.getMatrices().pop();

            // Metadata info: Combined on a single line with colorful text elements!
            int currentX = x + 12;
            context.drawTextWithShadow(this.textRenderer, Text.literal("🎮 "), currentX, y + 106, 0xFF55FFFF); // Cyan controller
            currentX += this.textRenderer.getWidth("🎮 ");
            context.drawTextWithShadow(this.textRenderer, Text.literal(gameVersion), currentX, y + 106, 0xFF55FFFF); // Cyan version
            currentX += this.textRenderer.getWidth(gameVersion);
            context.drawTextWithShadow(this.textRenderer, Text.literal(" | "), currentX, y + 106, 0xFF999999);
            currentX += this.textRenderer.getWidth(" | ");
            context.drawTextWithShadow(this.textRenderer, Text.literal("🏆 "), currentX, y + 106, 0xFFFFD700); // Gold trophy
            currentX += this.textRenderer.getWidth("🏆 ");
            context.drawTextWithShadow(this.textRenderer, Text.literal(prizePool), currentX, y + 106, 0xFFFFD700); // Gold prize pool

            // User status badge pill: Shifted to y + 120 (so it doesn't overlap the View Info button!)
            int badgeY = y + 120;
            int badgeW = 160;
            int badgeH = 16;
            
            int badgeColor;
            if ("APPROVED".equals(uStatus)) {
                badgeColor = 0xFF4CAF50; // Green
            } else if ("PENDING".equals(uStatus)) {
                badgeColor = 0xFFFFB300; // Orange-gold
            } else if ("REJECTED".equals(uStatus)) {
                badgeColor = 0xFFF44336; // Red
            } else {
                badgeColor = 0xFFF44336; // Not registered - Red
            }
            
            int badgeBg = (0x33 << 24) | (badgeColor & 0xFFFFFF);
            int badgeBorder = (0x80 << 24) | (badgeColor & 0xFFFFFF);
            drawPremiumBeveledBox(context, x + 10, badgeY, badgeW, badgeH, badgeBg, badgeBorder, badgeBorder);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label.toUpperCase()), x + 10 + badgeW / 2, badgeY + 4, badgeColor);
        }

        // Draw scrollbar if needed
        int cardAreaHeight = this.height - 50 - 30;
        int rows = (filteredTournaments.size() + cols - 1) / cols;
        int totalHeight = rows * 180;
        if (totalHeight > cardAreaHeight) {
            int scrollbarX = this.width - 8;
            int scrollbarY = 50;
            int scrollbarHeight = cardAreaHeight;
            int scrollbarWidth = 6;
            
            context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0x22FFFFFF);
            int thumbHeight = (cardAreaHeight * scrollbarHeight) / totalHeight;
            if (thumbHeight < 15) thumbHeight = 15;
            int thumbY = scrollbarY + (scrollOffset * (scrollbarHeight - thumbHeight)) / (totalHeight - cardAreaHeight);
            context.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, 0x88FFFFFF);
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
            if ("TOURNAMENTS_SYNC".equals(type)) {
                if (json.has("tournaments") && json.get("tournaments").isJsonArray()) {
                    com.google.gson.JsonArray array = json.getAsJsonArray("tournaments");
                    this.client.execute(() -> {
                        allTournaments.clear();
                        for (int i = 0; i < array.size(); i++) {
                            allTournaments.add(array.get(i).getAsJsonObject());
                        }
                        statusText = allTournaments.isEmpty() ? "No tournaments available." : "";
                        filterTournaments();
                    });
                }
            } else if ("TOURNAMENT_UPDATED".equals(type) || "MAINTENANCE_UPDATE".equals(type)) {
                this.client.execute(this::refreshList);
            }
        }
    }
}
