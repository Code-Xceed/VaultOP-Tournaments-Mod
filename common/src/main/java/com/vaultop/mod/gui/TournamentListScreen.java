package com.vaultop.mod.gui;

import com.google.gson.JsonObject;
import com.vaultop.mod.VaultOPMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class TournamentListScreen extends Screen {
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

    @Override
    protected void init() {
        // Setup Search Field in the center of the top bar
        this.searchField = new TextFieldWidget(this.textRenderer, this.width / 2 - 75, 10, 150, 20, Text.literal("Search"));
        this.searchField.setChangedListener(this::onSearchChanged);
        this.searchField.setDrawsBackground(false);

        rebuildWidgets();

        // Fetch Tournaments from REST API
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
        int cols = Math.max(1, gridWidth / 150);

        int cardY = 50;
        for (int i = 0; i < filteredTournaments.size(); i++) {
            final JsonObject t = filteredTournaments.get(i);
            int row = i / cols;
            int col = i % cols;
            int x = 15 + col * 150;
            int y = cardY + row * 120 - scrollOffset;

            // Details button inside card
            boolean isVisible = (y >= 40 && y + 110 <= this.height - 30);
            PremiumButtonWidget detailsBtn = new PremiumButtonWidget(x + 10, y + 80, 120, 20, Text.literal("View Info"), button -> {
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
        int cols = Math.max(1, (this.width - 30) / 150);
        int rows = (filteredTournaments.size() + cols - 1) / cols;
        int totalHeight = rows * 120;
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
            int cols = Math.max(1, (this.width - 30) / 150);
            int rows = (filteredTournaments.size() + cols - 1) / cols;
            int totalHeight = rows * 120;
            int maxScroll = Math.max(0, totalHeight - cardAreaHeight);
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // 1. Draw Top Header Panel (Height = 40) with double bevel
        drawPremiumBeveledBox(context, 0, -2, this.width, 42, 0xFF121212, 0xFF3C464F, 0xFF0C0C0C);
        
        // Brand logo in left corner
        context.drawTextWithShadow(this.textRenderer, Text.literal("VaultOP Esports"), 15, 16, 0xFFFFD700);

        // Draw premium bevel box around the search field text input (at this.width / 2 - 75, 10, 150, 20)
        drawPremiumBeveledBox(context, this.width / 2 - 77, 9, 154, 22, 0xFF050709, 0xFF1D2933, 0xFF0A1015);

        // 2. Draw Cards
        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), this.width / 2, this.height / 2, 0xAAAAAA);
        }

        int gridWidth = this.width - 30;
        int cols = Math.max(1, gridWidth / 150);

        int cardY = 50;
        for (int i = 0; i < filteredTournaments.size(); i++) {
            JsonObject t = filteredTournaments.get(i);
            int row = i / cols;
            int col = i % cols;
            int x = 15 + col * 150;
            int y = cardY + row * 120 - scrollOffset;

            // Render boundary check
            if (y + 110 < 40 || y > this.height - 30) continue;

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
            boolean isHovered = mouseX >= x && mouseX <= x + 140 && mouseY >= y && mouseY <= y + 110;
            int bgColor = isHovered ? 0xFF252525 : 0xFF1E1E1E;
            int highlightColor = isHovered ? 0xFF2196F3 : 0xFF3C3C3C;
            int shadowColor = isHovered ? 0xFF0D2F4D : 0xFF0C0C0C;

            // Draw Beveled 3D Card
            drawBeveledBox(context, x, y, 140, 110, bgColor, highlightColor, shadowColor);

            // Left status indicator bar inside the card
            int indicatorColor = 0xFF888888;
            if ("APPROVED".equals(uStatus)) {
                indicatorColor = 0xFF4CAF50;
            } else if ("PENDING".equals(uStatus)) {
                indicatorColor = 0xFFFF9800;
            } else if ("REJECTED".equals(uStatus)) {
                indicatorColor = 0xFFF44336;
            } else {
                if ("ONGOING".equals(status)) {
                    indicatorColor = 0xFFF44336;
                } else if ("REG_OPEN".equals(status)) {
                    indicatorColor = 0xFF2196F3;
                }
            }
            context.fill(x + 3, y + 4, x + 6, y + 106, indicatorColor);

            // Status badge text
            String statusBadgeText = "🔒 CLOSED";
            int badgeColor = 0xFF888888;
            if ("ONGOING".equals(status)) {
                statusBadgeText = "🔴 ONGOING";
                badgeColor = 0xFFF44336;
            } else if ("REG_OPEN".equals(status)) {
                statusBadgeText = "🟢 REG OPEN";
                badgeColor = 0xFF4CAF50;
            } else if ("REG_CLOSED".equals(status)) {
                statusBadgeText = "🔒 REG CLOSED";
                badgeColor = 0xFFFFB300;
            } else if ("COMPLETED".equals(status)) {
                statusBadgeText = "🔒 COMPLETED";
                badgeColor = 0xFF888888;
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(statusBadgeText), x + 12, y + 8, badgeColor);

            // Truncate name
            String displayTitle = name;
            if (this.textRenderer.getWidth(displayTitle) > 116) {
                displayTitle = this.textRenderer.trimToWidth(displayTitle, 108) + "...";
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(displayTitle), x + 12, y + 22, isHovered ? 0xFF55FFFF : 0xFFFFFFFF);

            // Metadata info
            context.drawTextWithShadow(this.textRenderer, Text.literal("Ver: " + gameVersion), x + 12, y + 38, 0x999999);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Prize: " + prizePool), x + 12, y + 48, 0xFFFFD700);

            // User status badge
            int userBadgeColor = 0xFF888888;
            if ("APPROVED".equals(uStatus)) userBadgeColor = 0xFF4CAF50;
            else if ("PENDING".equals(uStatus)) userBadgeColor = 0xFFFF9800;
            else if ("REJECTED".equals(uStatus)) userBadgeColor = 0xFFF44336;
            context.drawTextWithShadow(this.textRenderer, Text.literal(label.toUpperCase()), x + 12, y + 64, userBadgeColor);
        }

        // Draw scrollbar if needed
        int cardAreaHeight = this.height - 50 - 30;
        int rows = (filteredTournaments.size() + cols - 1) / cols;
        int totalHeight = rows * 120;
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
}
