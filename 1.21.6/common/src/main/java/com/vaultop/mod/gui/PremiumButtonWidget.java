package com.vaultop.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.function.BooleanSupplier;

public class PremiumButtonWidget extends ButtonWidget {
    private final int highlightColor;
    private final int shadowColor;
    private final int activeColor;
    private BooleanSupplier isSelectedSupplier = () -> false;

    public PremiumButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress, int highlightColor, int shadowColor, int activeColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.highlightColor = highlightColor;
        this.shadowColor = shadowColor;
        this.activeColor = activeColor;
    }

    public PremiumButtonWidget setSelectedSupplier(BooleanSupplier supplier) {
        this.isSelectedSupplier = supplier;
        return this;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer renderer = client.textRenderer;

        boolean hovered = this.isSelected() || (mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= this.getY() && mouseY <= this.getY() + this.height);
        boolean selected = isSelectedSupplier.getAsBoolean();
        
        int bgColor = 0xFF1E1E1E;
        int hl = this.highlightColor;
        int sh = this.shadowColor;
        
        if (!this.active) {
            bgColor = 0xFF151515;
            hl = 0xFF3C3C3C;
            sh = 0xFF0C0C0C;
        } else if (selected) {
            bgColor = 0xFF0B1A24;
            hl = 0xFF2196F3;
            sh = 0xFF0D2F4D;
        } else if (hovered) {
            bgColor = 0xFF2A2A2A;
            if (this.activeColor != 0) {
                hl = this.activeColor;
            }
        }

        // Draw double beveled 3D borders
        TournamentListScreen.drawPremiumBeveledBox(context, this.getX(), this.getY(), this.width, this.height, bgColor, hl, sh);

        // Draw centered message text
        int textColor;
        if (!this.active) {
            textColor = 0xFF666666;
        } else if (selected) {
            textColor = 0xFF55FFFF;
        } else if (hovered) {
            textColor = 0xFFFFD700;
        } else {
            textColor = 0xFFFFFFFF;
        }
        
        int textX = this.getX() + (this.width - renderer.getWidth(this.getMessage())) / 2;
        int textY = this.getY() + (this.height - 8) / 2;
        context.drawTextWithShadow(renderer, this.getMessage(), textX, textY, textColor);
    }
}
