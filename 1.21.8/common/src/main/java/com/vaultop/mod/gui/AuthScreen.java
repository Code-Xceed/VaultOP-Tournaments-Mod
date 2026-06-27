package com.vaultop.mod.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vaultop.mod.VaultOPMod;
import com.vaultop.mod.auth.LocalhostCallbackServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AuthScreen extends Screen {
    private final Screen parent;
    private LocalhostCallbackServer callbackServer;
    
    private String statusText = "Ready to Authenticate";
    private String codeText = "";
    private boolean isWaiting = false;
    private PremiumButtonWidget authButton;
    private PremiumButtonWidget cancelButton;

    public AuthScreen(Screen parent) {
        super(Text.literal("VaultOP Authentication"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = 200;
        int height = 20;
        int x = this.width / 2 - width / 2;
        int y = this.height / 2 + 10;

        // Create Authenticate Button
        this.authButton = new PremiumButtonWidget(x, y, width, height, Text.literal("Authenticate"), button -> startAuthFlow(), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3);
        this.addDrawableChild(this.authButton);

        // Create Cancel/Back Button
        this.cancelButton = new PremiumButtonWidget(x, y + 24, width, height, Text.literal("Cancel"), button -> close(), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3);
        this.addDrawableChild(this.cancelButton);
    }

    private void startAuthFlow() {
        if (isWaiting) return;
        
        isWaiting = true;
        this.authButton.active = false;
        statusText = "Starting local server...";

        // 1. Start Local Loopback Server
        this.callbackServer = new LocalhostCallbackServer(new LocalhostCallbackServer.Callback() {
            @Override
            public void onSuccess(String token) {
                statusText = "Authenticated successfully!";
                VaultOPMod.getInstance().getSessionManager().saveSession(token);
                VaultOPMod.getInstance().startWebSocket(token);
                
                // Close screen on the main client thread
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().setScreen(parent);
                });
            }

            @Override
            public void onFailure(String reason) {
                isWaiting = false;
                authButton.active = true;
                statusText = "Authentication Failed: " + reason;
                codeText = "";
                if (callbackServer != null) {
                    callbackServer.stop();
                }
            }
        });
        this.callbackServer.start();

        // 2. Fetch Linking Code from Backend
        statusText = "Fetching code from backend...";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
                
        String backendUrl = VaultOPMod.getInstance().getConfigManager().getBackendUrl();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + "/api/auth/mod/initiate"))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    try {
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        String code = json.get("code").getAsString();
                        String confirmUrl = json.get("confirmUrl").getAsString();
                        
                        MinecraftClient.getInstance().execute(() -> {
                            statusText = "Browser opened. Please confirm link.";
                            codeText = "Your Link Code: " + code.toUpperCase();
                            
                            // Open default browser
                            Util.getOperatingSystem().open(confirmUrl);
                        });
                    } catch (Exception e) {
                        MinecraftClient.getInstance().execute(() -> {
                            onAuthFailure("Failed to parse backend response.");
                        });
                    }
                })
                .exceptionally(throwable -> {
                    MinecraftClient.getInstance().execute(() -> {
                        onAuthFailure("Backend server is offline.");
                    });
                    return null;
                });
    }

    private void onAuthFailure(String reason) {
        isWaiting = false;
        this.authButton.active = true;
        this.statusText = reason;
        this.codeText = "";
        if (this.callbackServer != null) {
            this.callbackServer.stop();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier bgTex = Identifier.of("vaultop", "textures/gui/mod_bg_image.png");
        context.drawTexture(RenderPipelines.GUI_TEXTURED, bgTex, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        context.fill(0, 0, this.width, this.height, 0x80050505);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw background dirt or default gradient
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // Draw Titles
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // Draw Status text
        context.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, this.height / 2 - 30, 0xFFAAAAAA);
        
        // Draw Linking Code if available
        if (!codeText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, codeText, this.width / 2, this.height / 2 - 10, 0x4dedf6);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.callbackServer != null) {
            this.callbackServer.stop();
        }
        MinecraftClient.getInstance().setScreen(this.parent);
    }
}
