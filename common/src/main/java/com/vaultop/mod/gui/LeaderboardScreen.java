package com.vaultop.mod.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vaultop.mod.VaultOPMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardScreen extends Screen {
    private final Screen parent;
    private final List<JsonObject> completedTournaments = new ArrayList<>();
    private JsonObject selectedTournament = null;
    private String statusText = "Loading leaderboards...";

    public LeaderboardScreen(Screen parent) {
        super(Text.literal("VaultOP Leaderboards"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(10, 10, 50, 20)
                .build());

        // Fetch tournaments and filter completed ones
        VaultOPMod.getInstance().getRestClient().fetchTournaments()
                .thenAccept(array -> {
                    this.client.execute(() -> {
                        completedTournaments.clear();
                        for (int i = 0; i < array.size(); i++) {
                            JsonObject t = array.get(i).getAsJsonObject();
                            String status = t.has("status") ? t.get("status").getAsString() : "";
                            if ("completed".equalsIgnoreCase(status) || "archived".equalsIgnoreCase(status)) {
                                completedTournaments.add(t);
                            }
                        }
                        if (completedTournaments.isEmpty()) {
                            statusText = "No completed tournaments found.";
                        } else {
                            statusText = "";
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

    private void rebuildWidgets() {
        this.clearChildren(); // rebuild screen layout

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(10, 10, 50, 20)
                .build());

        // Draw buttons for completed tournaments on the left side
        int startY = 50;
        for (int i = 0; i < completedTournaments.size(); i++) {
            final JsonObject t = completedTournaments.get(i);
            String name = t.has("name") ? t.get("name").getAsString() : "Tournament " + (i + 1);
            if (name.length() > 18) {
                name = name.substring(0, 15) + "...";
            }
            this.addDrawableChild(ButtonWidget.builder(Text.literal(name), button -> {
                selectedTournament = t;
            }).dimensions(10, startY + (i * 24), 100, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("VaultOP Leaderboards"), this.width / 2, 20, 0xFFFFFF);

        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusText), this.width / 2, this.height / 2, 0xAAAAAA);
        }

        if (selectedTournament != null) {
            // Draw selected tournament name
            String tName = selectedTournament.get("name").getAsString();
            context.drawTextWithShadow(this.textRenderer, Text.literal(tName), 130, 50, 0xFFFF55);

            // Draw header
            context.drawTextWithShadow(this.textRenderer, Text.literal("Rank   Player IGN           Score"), 130, 75, 0xAAAAAA);

            // Render leaderboard standings. Fetch if present, otherwise render clean template
            JsonArray standings = selectedTournament.has("standings") ? selectedTournament.getAsJsonArray("standings") : null;
            if (standings != null && standings.size() > 0) {
                for (int i = 0; i < Math.min(standings.size(), 5); i++) {
                    JsonObject entry = standings.get(i).getAsJsonObject();
                    String rank = String.valueOf(i + 1);
                    String ign = entry.has("ign") ? entry.get("ign").getAsString() : "Player";
                    String score = entry.has("score") ? entry.get("score").getAsString() : "N/A";

                    int y = 95 + (i * 18);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(rank), 135, y, 0xFFFFFF);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(ign), 165, y, 0x55FF55);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(score), 280, y, 0xFFFF55);
                }
            } else {
                // Template mock data for testing UI when database is empty
                String[][] mockData = {
                    {"1st", "Aditya_V", "12,500"},
                    {"2nd", "MinecraftPro", "11,200"},
                    {"3rd", "Speedrunner99", "10,800"},
                    {"4th", "FabricFanatic", "9,500"},
                    {"5th", "SodiumUser", "8,900"}
                };

                for (int i = 0; i < mockData.length; i++) {
                    int y = 95 + (i * 18);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(mockData[i][0]), 135, y, 0xFFFFFF);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(mockData[i][1]), 165, y, 0x55FF55);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(mockData[i][2]), 280, y, 0xFFFF55);
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
