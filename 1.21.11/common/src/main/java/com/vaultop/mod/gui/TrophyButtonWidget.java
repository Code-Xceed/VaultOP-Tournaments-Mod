package com.vaultop.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TrophyButtonWidget extends ClickableWidget {
    private final PressAction onPress;

    public interface PressAction {
        void onPress(TrophyButtonWidget button);
    }

    public TrophyButtonWidget(int x, int y, PressAction onPress) {
        super(x, y, 20, 20, net.minecraft.text.Text.literal(""));
        this.onPress = onPress;
    }

    @Override
    public void onClick(net.minecraft.client.gui.Click click, boolean booleanVal) {
        playClickSound(MinecraftClient.getInstance().getSoundManager());
        this.onPress.onPress(this);
    }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;

        boolean hovered = this.isSelected() || (mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= this.getY() && mouseY <= this.getY() + this.height);
        
        int bgColor = hovered ? 0xFF2A2A2A : 0xFF1E1E1E;
        int hl = hovered ? 0xFFFFD700 : 0xFF3C464F;
        int sh = hovered ? 0xFFB8860B : 0xFF0C0C0C;

        TournamentListScreen.drawPremiumBeveledBox(context, this.getX(), this.getY(), this.width, this.height, bgColor, hl, sh);
        
        Identifier logoTex = Identifier.of("vaultop", "textures/gui/logo.png");
        context.drawTexture(RenderPipelines.GUI_TEXTURED, logoTex, this.getX() + 2, this.getY() + 2, 0f, 0f, this.width - 4, this.height - 4, this.width - 4, this.height - 4);
    }
}
