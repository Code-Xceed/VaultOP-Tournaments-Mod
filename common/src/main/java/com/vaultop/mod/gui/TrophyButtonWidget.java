package com.vaultop.mod.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class TrophyButtonWidget extends ButtonWidget {
    public TrophyButtonWidget(int x, int y, PressAction onPress) {
        super(x, y, 20, 20, Text.literal(""), onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;

        boolean hovered = this.isSelected() || (mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= this.getY() && mouseY <= this.getY() + this.height);
        
        int bgColor = hovered ? 0xFF2A2A2A : 0xFF1E1E1E;
        int hl = hovered ? 0xFFFFD700 : 0xFF3C464F;
        int sh = hovered ? 0xFFB8860B : 0xFF0C0C0C;

        TournamentListScreen.drawPremiumBeveledBox(context, this.getX(), this.getY(), this.width, this.height, bgColor, hl, sh);
        
        int cx = this.getX() + 10;
        int cy = this.getY() + 10;
        int gold = hovered ? 0xFFFFFFFF : 0xFFFFD700;
        int darkGold = hovered ? 0xFFAAAAAA : 0xFFB8860B;

        // Draw pixel trophy cup icon
        // Cup bowl
        context.fill(cx - 3, cy - 4, cx + 4, cy - 1, gold);
        // Cup rim
        context.fill(cx - 4, cy - 5, cx + 5, cy - 4, gold);
        
        // Handles
        context.fill(cx - 5, cy - 4, cx - 4, cy - 2, gold);
        context.fill(cx + 4, cy - 4, cx + 5, cy - 2, gold);
        
        // Stem
        context.fill(cx - 1, cy - 1, cx + 2, cy + 2, gold);
        
        // Base
        context.fill(cx - 3, cy + 2, cx + 4, cy + 4, darkGold);
        context.fill(cx - 4, cy + 3, cx + 5, cy + 4, gold);
    }
}
