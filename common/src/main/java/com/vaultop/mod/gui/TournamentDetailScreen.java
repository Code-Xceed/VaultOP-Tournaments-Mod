package com.vaultop.mod.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vaultop.mod.VaultOPMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

public class TournamentDetailScreen extends Screen {
    private final Screen parent;
    private final JsonObject tournament;
    private final String tournamentId;
    
    private PremiumButtonWidget joinButton;
    private PremiumButtonWidget registerButton;
    
    private String serverIp = "";
    private boolean matchStarted = false;
    private long countdownTime = 0; // in seconds
    
    private final List<String> rules = new ArrayList<>();
    private final List<String[]> schedule = new ArrayList<>();
    
    private String userStatus = "NOT_REGISTERED";
    private String registeredIgn = "";
    private String broadcastMessage = "";
    
    private String activeTab = "OVERVIEW";
    private int scrollOffset = 0;

    public TournamentDetailScreen(Screen parent, JsonObject tournament) {
        super(Text.literal("VaultOP Tournament Details"));
        this.parent = parent;
        this.tournament = tournament;
        this.tournamentId = tournament.has("id") && !tournament.get("id").isJsonNull() ? tournament.get("id").getAsString() : "";
        
        // Initial setup from cached data
        String initialLabel = "Not Registered";
        if (tournament.has("userStatusLabel") && !tournament.get("userStatusLabel").isJsonNull()) {
            initialLabel = tournament.get("userStatusLabel").getAsString();
        }
        this.userStatus = TournamentListScreen.getStatusFromLabel(initialLabel);
        
        if (tournament.has("broadcast") && !tournament.get("broadcast").isJsonNull()) {
            this.broadcastMessage = tournament.get("broadcast").getAsString();
        }
        
        if (tournament.has("matchStarted") && !tournament.get("matchStarted").isJsonNull()) {
            this.matchStarted = tournament.get("matchStarted").getAsBoolean();
        }
        if (tournament.has("serverIp") && !tournament.get("serverIp").isJsonNull()) {
            this.serverIp = tournament.get("serverIp").getAsString();
        }

        calculateCountdown();

        // Load Rules safely
        if (tournament.has("rules") && !tournament.get("rules").isJsonNull()) {
            try {
                JsonArray rulesArray = tournament.getAsJsonArray("rules");
                for (int i = 0; i < rulesArray.size(); i++) {
                    rules.add(rulesArray.get(i).getAsString());
                }
            } catch (Exception e) {
                loadDefaultRules();
            }
        } else {
            loadDefaultRules();
        }

        // Load Schedule safely
        if (tournament.has("schedule") && !tournament.get("schedule").isJsonNull()) {
            try {
                JsonArray schedArray = tournament.getAsJsonArray("schedule");
                for (int i = 0; i < schedArray.size(); i++) {
                    JsonObject entry = schedArray.get(i).getAsJsonObject();
                    String time = entry.has("time") && !entry.get("time").isJsonNull() ? entry.get("time").getAsString() : "TBD";
                    String event = entry.has("event") && !entry.get("event").isJsonNull() ? entry.get("event").getAsString() : "";
                    schedule.add(new String[]{time, event});
                }
            } catch (Exception e) {
                loadDefaultSchedule();
            }
        } else {
            loadDefaultSchedule();
        }
    }

    public static long parseTournamentDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "TBD".equalsIgnoreCase(dateStr.trim())) {
            return 0;
        }
        try {
            String[] parts = dateStr.split(" @ ");
            if (parts.length != 2) return 0;
            
            String datePart = parts[0].trim();
            String timePart = parts[1].trim();
            
            String[] dateSub = datePart.split("\\s+");
            if (dateSub.length < 3) return 0;
            String monthName = dateSub[0];
            int day = Integer.parseInt(dateSub[1].replace(",", "").trim());
            int year = Integer.parseInt(dateSub[2].trim());
            
            String[] timeSub = timePart.split("\\s+");
            if (timeSub.length < 3) return 0;
            String[] timeSegments = timeSub[0].split(":");
            int hour = Integer.parseInt(timeSegments[0].trim());
            int minute = Integer.parseInt(timeSegments[1].trim());
            String ampm = timeSub[1].toUpperCase();
            String tz = timeSub[2].toUpperCase();
            
            if (ampm.equals("PM") && hour < 12) hour += 12;
            if (ampm.equals("AM") && hour == 12) hour = 0;
            
            String[] MONTH_NAMES = {
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            };
            int monthIdx = -1;
            for (int i = 0; i < MONTH_NAMES.length; i++) {
                if (MONTH_NAMES[i].equalsIgnoreCase(monthName)) {
                    monthIdx = i;
                    break;
                }
            }
            if (monthIdx == -1) return 0;
            
            java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            cal.set(year, monthIdx, day, hour, minute, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long utcEpochMs = cal.getTimeInMillis();
            
            int tzOffsetMinutes = 0;
            if (tz.equals("IST")) {
                tzOffsetMinutes = 330;
            } else if (tz.equals("EST")) {
                tzOffsetMinutes = -300;
            } else if (tz.equals("PST")) {
                tzOffsetMinutes = -480;
            } else if (tz.equals("CET")) {
                tzOffsetMinutes = 60;
            } else if (tz.equals("JST")) {
                tzOffsetMinutes = 540;
            } else if (tz.equals("UTC") || tz.equals("GMT")) {
                tzOffsetMinutes = 0;
            }
            
            return utcEpochMs - tzOffsetMinutes * 60L * 1000L;
        } catch (Exception e) {
            return 0;
        }
    }

    private void calculateCountdown() {
        String dateStr = tournament.has("date") && !tournament.get("date").isJsonNull() ? tournament.get("date").getAsString() : "";
        long targetTime = parseTournamentDate(dateStr);
        if (targetTime <= 0) {
            this.countdownTime = 0;
            return;
        }
        long now = System.currentTimeMillis();
        long diffMs = targetTime - now;
        if (diffMs > 0) {
            this.countdownTime = diffMs / 1000;
        } else {
            this.countdownTime = 0;
        }
    }

    private void loadDefaultRules() {
        rules.add("Must use the official VaultOP client mod pack.");
        rules.add("Cheating or hacking is strictly prohibited.");
        rules.add("Strict mod list compliance is enforced.");
    }

    private void loadDefaultSchedule() {
        schedule.add(new String[]{"18:00 UTC", "Event setup & sync"});
        schedule.add(new String[]{"18:30 UTC", "Matches start"});
    }

    @Override
    protected void init() {
        // Back Button in top left (above header banner)
        this.addDrawableChild(new PremiumButtonWidget(10, 10, 50, 20, Text.literal("Back"), button -> close(), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3));

        // Tab selection buttons
        this.addDrawableChild(new PremiumButtonWidget(20, 115, 100, 20, Text.literal("Overview"), button -> selectTab("OVERVIEW"), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3)
                .setSelectedSupplier(() -> "OVERVIEW".equals(activeTab)));
        this.addDrawableChild(new PremiumButtonWidget(125, 115, 110, 20, Text.literal("Rules & Timeline"), button -> selectTab("RULES"), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3)
                .setSelectedSupplier(() -> "RULES".equals(activeTab)));
        this.addDrawableChild(new PremiumButtonWidget(240, 115, 110, 20, Text.literal("Connect & Status"), button -> selectTab("CONNECT"), 0xFF3C464F, 0xFF0C0C0C, 0xFF2196F3)
                .setSelectedSupplier(() -> "CONNECT".equals(activeTab)));

        // Context-sensitive buttons (positioned in Connect tab dynamically)
        this.joinButton = new PremiumButtonWidget(0, 0, 100, 20, Text.literal("Join Server"), button -> joinServer(), 0xFF4CAF50, 0xFF1B5E20, 0xFF81C784);
        this.joinButton.visible = false;
        this.addDrawableChild(this.joinButton);

        this.registerButton = new PremiumButtonWidget(0, 0, 100, 20, Text.literal("Register Now"), button -> registerOnWeb(), 0xFF2196F3, 0xFF0D2F4D, 0xFF64B5F6);
        this.registerButton.visible = false;
        this.addDrawableChild(this.registerButton);

        // Fetch real-time registration status
        fetchFreshStatus();

        // Listen for WebSocket notifications
        if (VaultOPMod.getInstance().getWebSocketManager() != null) {
            VaultOPMod.getInstance().getWebSocketManager().setMessageListener(json -> {
                if (json.has("type") && !json.get("type").isJsonNull()) {
                    String type = json.get("type").getAsString();
                    if ("MATCH_START".equals(type) && json.has("tournamentId") && !json.get("tournamentId").isJsonNull()) {
                        String tId = json.get("tournamentId").getAsString();
                        if (tId.equals(tournamentId)) {
                            this.serverIp = json.has("ip") && !json.get("ip").isJsonNull() ? json.get("ip").getAsString() : "";
                            this.matchStarted = true;
                            this.client.execute(() -> {
                                if (this.joinButton != null) {
                                    this.joinButton.active = true;
                                }
                            });
                        }
                    }
                    if ("TOURNAMENT_UPDATED".equals(type) && json.has("tournamentId") && !json.get("tournamentId").isJsonNull()) {
                        String tId = json.get("tournamentId").getAsString();
                        if (tId.equals(tournamentId)) {
                            this.client.execute(this::fetchFreshStatus);
                        }
                    }
                }
            });
        }
    }

    private void selectTab(String tab) {
        this.activeTab = tab;
        this.scrollOffset = 0;
    }

    private void fetchFreshStatus() {
        String token = VaultOPMod.getInstance().getSessionManager().getSessionToken();
        if (token != null && !token.trim().isEmpty() && !tournamentId.isEmpty()) {
            VaultOPMod.getInstance().getRestClient().fetchRegistrationStatus(tournamentId, token)
                    .thenAccept(reg -> {
                        this.client.execute(() -> {
                            if (reg.has("status") && !reg.get("status").isJsonNull()) {
                                this.userStatus = reg.get("status").getAsString();
                            }
                            if (reg.has("ign") && !reg.get("ign").isJsonNull()) {
                                this.registeredIgn = reg.get("ign").getAsString();
                            }
                        });
                    })
                    .exceptionally(ex -> null);
        }
    }

    private void registerOnWeb() {
        if (!tournamentId.isEmpty()) {
            String webUrl = VaultOPMod.getInstance().getConfigManager().getWebsiteUrl();
            Util.getOperatingSystem().open(webUrl + "/tournaments/" + tournamentId + "/register");
        }
    }

    private void joinServer() {
        if (serverIp.isEmpty()) return;
        
        ServerInfo info = new ServerInfo("VaultOP Tournament Server", serverIp, ServerInfo.ServerType.OTHER);
        ServerAddress address = ServerAddress.parse(serverIp);
        
        this.client.execute(() -> {
            ConnectScreen.connect(this, this.client, address, info, false, null);
        });
    }

    private int getMaxScroll() {
        int contentHeight = 0;
        int leftWidth = (int) ((this.width - 40) * 0.6) - 10;
        
        if ("OVERVIEW".equals(activeTab)) {
            String description = tournament.has("description") && !tournament.get("description").isJsonNull() ? tournament.get("description").getAsString() : "";
            List<OrderedText> descLines = this.textRenderer.wrapLines(Text.literal(description), leftWidth - 16);
            int leftHeight = 30 + descLines.size() * 10;
            int rightHeight = 4 * 38 + 10;
            contentHeight = Math.max(leftHeight, rightHeight);
        } else if ("RULES".equals(activeTab)) {
            int leftHeight = 30 + rules.size() * 12;
            int rightHeight = 30 + schedule.size() * 18;
            contentHeight = Math.max(leftHeight, rightHeight);
        } else if ("CONNECT".equals(activeTab)) {
            int leftHeight = 160;
            int rightHeight = 180;
            contentHeight = Math.max(leftHeight, rightHeight);
        }
        
        int viewHeight = this.height - 145 - 20;
        return Math.max(0, contentHeight - viewHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            int maxScroll = getMaxScroll();
            if (maxScroll > 0) {
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

        String name = tournament.has("name") && !tournament.get("name").isJsonNull() ? tournament.get("name").getAsString() : "Unnamed Tournament";
        String description = tournament.has("description") && !tournament.get("description").isJsonNull() ? tournament.get("description").getAsString() : "No description provided.";
        String prize = tournament.has("prizePool") && !tournament.get("prizePool").isJsonNull() ? tournament.get("prizePool").getAsString() : "$0";
        String gameVersion = tournament.has("gameVersion") && !tournament.get("gameVersion").isJsonNull() ? tournament.get("gameVersion").getAsString() : "1.21.5";
        String status = tournament.has("status") && !tournament.get("status").isJsonNull() ? tournament.get("status").getAsString() : "REG_CLOSED";
        String date = tournament.has("date") && !tournament.get("date").isJsonNull() ? tournament.get("date").getAsString() : "TBD";

        // Draw header panel
        TournamentListScreen.drawPremiumBeveledBox(context, 0, -2, this.width, 37, 0xD00A0F18, 0x25FFFFFF, 0x10FFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(name), this.width / 2, 12, 0xFFFFFF);

        // 1. Draw Giant Esports Banner Card at top
        TournamentListScreen.drawPremiumBeveledBox(context, 20, 45, this.width - 40, 60, 0xBF080C14, 0x802196F3, 0x402196F3);

        // Retrieve custom thumbnail if available
        String thumbUrl = tournament.has("thumbnailUrl") && !tournament.get("thumbnailUrl").isJsonNull() ? tournament.get("thumbnailUrl").getAsString() : "";
        Identifier customThumb = null;
        if (!thumbUrl.isEmpty()) {
            customThumb = DynamicTextureLoader.getOrLoad(thumbUrl, tournamentId);
        }

        // 1. Draw 16:9 Animated Status Thumbnail on the left of the banner
        TournamentListScreen.drawAnimatedStatusThumbnail(context, 22, 47, 106, 56, status, customThumb, this.textRenderer);
        // Vertical divider line separating thumbnail and details
        context.fill(132, 47, 133, 103, 0x33FFFFFF);
        int textX = 140;

        // Name inside banner
        context.drawTextWithShadow(this.textRenderer, Text.literal(name.toUpperCase()), textX, 53, 0xFFFFD700);

        // Draw the Event Date (moved up to y=70 to replace removed status badge below the title)
        context.drawTextWithShadow(this.textRenderer, Text.literal("Event Date: " + date), textX, 70, 0xFF888888);

        // Giant countdown / status box on the right side of the banner (Pulsing high-tech design)
        long time = System.currentTimeMillis();
        double pulseSin = Math.sin(time * 0.005);
        double pulseVal = (pulseSin + 1.0) / 2.0; // 0.0 to 1.0

        int badgeColor = 0xFF888888;
        if ("ONGOING".equals(status)) {
            badgeColor = 0xFFF44336;
        } else if ("REG_OPEN".equals(status)) {
            badgeColor = 0xFF4CAF50;
        } else if ("REG_CLOSED".equals(status)) {
            badgeColor = 0xFFFFB300;
        }

        if ("ONGOING".equals(status) || matchStarted) {
            // Event is Live with pulsing red box
            int alphaBg = (int) (0x40 + pulseVal * 0x30); // 64 to 112 opacity
            int liveBgColor = (alphaBg << 24) | 0x3A0000;
            int rIntensity = (int) (180 + pulseVal * 75);
            int liveBorderColor = (0xFF << 24) | (rIntensity << 16) | (15 << 8) | 15;
            int liveShadowColor = (0x80 << 24) | (rIntensity << 16) | (15 << 8) | 15;

            TournamentListScreen.drawPremiumBeveledBox(context, this.width - 170, 52, 140, 46, liveBgColor, liveBorderColor, liveShadowColor);
            
            // Flashing indicator dot
            String liveText = "EVENT IS LIVE";
            int liveTextW = this.textRenderer.getWidth(liveText);
            int startTextX = this.width - 100 - (liveTextW / 2);
            context.drawTextWithShadow(this.textRenderer, Text.literal("🔴"), startTextX - 10, 60, (int) (180 + pulseVal * 75) << 24 | 0xFF0000);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(liveText), this.width - 100, 60, 0xFFFFFFFF);
            
            // Gold pulsing JOIN button
            int goldText = (0xFF << 24) | (255 << 16) | ((int) (200 + pulseVal * 55) << 8) | 55;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("JOIN SERVER"), this.width - 100, 74, goldText);
        } else if ("COMPLETED".equals(status)) {
            // Event is completed
            TournamentListScreen.drawPremiumBeveledBox(context, this.width - 170, 52, 140, 46, 0x801A1A1A, 0x40FFFFFF, 0x20FFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("🔒 COMPLETED"), this.width - 100, 60, 0xFFAAAAAA);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("EVENT ENDED"), this.width - 100, 74, 0x88888888);
        } else if ("APPROVED".equals(userStatus)) {
            // User is approved: show pulsing countdown starts in box
            int alphaBg = (int) (0x30 + pulseVal * 0x20); // 48 to 80 opacity
            int liveBgColor = (alphaBg << 24) | 0x051A2E;
            int bIntensity = (int) (150 + pulseVal * 105);
            int liveBorderColor = (0xFF << 24) | (33 << 16) | (bIntensity << 8) | 0xFF;
            int liveShadowColor = (0x80 << 24) | (33 << 16) | (bIntensity << 8) | 0xFF;

            TournamentListScreen.drawPremiumBeveledBox(context, this.width - 170, 52, 140, 46, liveBgColor, liveBorderColor, liveShadowColor);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("MATCH STARTS IN"), this.width - 100, 58, 0xFF888888);
            
            String timeStr = "TBD";
            if (countdownTime > 0) {
                long days = countdownTime / (24 * 3600);
                long hours = (countdownTime % (24 * 3600)) / 3600;
                long minutes = (countdownTime % 3600) / 60;
                long seconds = countdownTime % 60;
                if (days > 0) {
                    timeStr = String.format("%02dd : %02dh : %02dm : %02ds", days, hours, minutes, seconds);
                } else {
                    timeStr = String.format("%02dh : %02dm : %02ds", hours, minutes, seconds);
                }
            }
            int timeColor = (0xFF << 24) | ((int)(200 + pulseVal * 55) << 16) | 0xFFFF; // fade white to cyan
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(timeStr), this.width - 100, 74, timeColor);
        } else {
            // User is not approved: show registration status info
            int alphaBg = (int) (0x30 + pulseVal * 0x20);
            int liveBgColor = (alphaBg << 24) | 0x080C14;
            int bIntensity = (int) (60 + pulseVal * 50);
            int liveBorderColor = (0xFF << 24) | (bIntensity << 16) | (bIntensity << 8) | bIntensity;
            int liveShadowColor = (0x80 << 24) | (bIntensity << 16) | (bIntensity << 8) | bIntensity;

            TournamentListScreen.drawPremiumBeveledBox(context, this.width - 170, 52, 140, 46, liveBgColor, liveBorderColor, liveShadowColor);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("REGISTRATION"), this.width - 100, 58, 0xFF888888);
            
            String regStatusStr = "REG_OPEN".equals(status) ? "🟢 OPEN" : "🔒 CLOSED";
            int regStatusColor = "REG_OPEN".equals(status) ? 0xFF4CAF50 : 0xFFFFB300;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(regStatusStr), this.width - 100, 74, regStatusColor);
        }

        // Panel bounds calculation
        int leftWidth = (int) ((this.width - 40) * 0.6) - 10;
        int rightWidth = (this.width - 40) - leftWidth - 10;
        int rightX = 20 + leftWidth + 10;

        int scissorY = 145;
        int scissorHeight = this.height - 145 - 20;

        // Render Scrollable Content inside Scissor view
        context.enableScissor(20, scissorY, this.width - 20, scissorY + scissorHeight);

        int renderY = 145 - scrollOffset;

        if ("OVERVIEW".equals(activeTab)) {
            joinButton.visible = false;
            registerButton.visible = false;

            // Left Overview Panel
            TournamentListScreen.drawPremiumBeveledBox(context, 20, renderY, leftWidth, Math.max(scissorHeight, 200), 0x9F080C14, 0x30FFFFFF, 0x15FFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Tournament Overview"), 28, renderY + 8, 0xFF2196F3);
            context.fill(28, renderY + 18, 20 + leftWidth - 8, renderY + 19, 0x30FFFFFF); // Subtle 1px separator line
            
            int yText = renderY + 26;
            List<OrderedText> descLines = this.textRenderer.wrapLines(Text.literal(description), leftWidth - 16);
            for (OrderedText line : descLines) {
                context.drawTextWithShadow(this.textRenderer, line, 28, yText, 0x999999);
                yText += 10;
            }

            // Right Spec blocks vertical stack
            int bX = rightX;
            int bW = rightWidth;

            // Spec 1: Game Version
            drawSpecCard(context, bX, renderY, bW, "MINECRAFT VERSION", gameVersion, 0xFFFFFF);
            // Spec 2: Prize Pool
            drawSpecCard(context, bX, renderY + 38, bW, "TOURNAMENT PRIZE POOL", prize, 0xFFFFD700);
            // Spec 3: Event Date
            drawSpecCard(context, bX, renderY + 76, bW, "TOURNAMENT START DATE", date, 0xFFFFFF);
            // Spec 4: Server Status
            String sVal = "ONGOING".equals(status) ? "LIVE" : ("REG_OPEN".equals(status) ? "UPCOMING (REG OPEN)" : ("REG_CLOSED".equals(status) ? "UPCOMING (REG CLOSED)" : "COMPLETED"));
            drawSpecCard(context, bX, renderY + 114, bW, "TOURNAMENT STATUS", sVal, badgeColor);

        } else if ("RULES".equals(activeTab)) {
            joinButton.visible = false;
            registerButton.visible = false;

            // Left Rules Card
            int leftPanelH = Math.max(scissorHeight, 30 + rules.size() * 12);
            TournamentListScreen.drawPremiumBeveledBox(context, 20, renderY, leftWidth, leftPanelH, 0x9F080C14, 0x30FFFFFF, 0x15FFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Tournament Rules"), 28, renderY + 8, 0xFF2196F3);
            context.fill(28, renderY + 18, 20 + leftWidth - 8, renderY + 19, 0x30FFFFFF); // Subtle 1px separator line
            int ry = renderY + 26;
            for (int i = 0; i < rules.size(); i++) {
                context.fill(30, ry + 3, 33, ry + 6, 0xFF2196F3);
                context.drawTextWithShadow(this.textRenderer, Text.literal(rules.get(i)), 38, ry, 0x999999);
                ry += 12;
            }

            // Right Schedule Timeline Card
            int rightPanelH = Math.max(scissorHeight, 30 + schedule.size() * 18);
            TournamentListScreen.drawPremiumBeveledBox(context, rightX, renderY, rightWidth, rightPanelH, 0x9F080C14, 0x30FFFFFF, 0x15FFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Event Timeline"), rightX + 8, renderY + 8, 0xFF2196F3);
            context.fill(rightX + 8, renderY + 18, rightX + rightWidth - 8, renderY + 19, 0x30FFFFFF); // Subtle 1px separator line
            int sy = renderY + 26;
            if (schedule.size() > 0) {
                context.fill(rightX + 12, renderY + 26, rightX + 13, renderY + 26 + (schedule.size() - 1) * 18 + 4, 0x44FFFFFF);
            }
            for (int i = 0; i < schedule.size(); i++) {
                String[] entry = schedule.get(i);
                context.fill(rightX + 10, sy + 3, rightX + 15, sy + 8, 0x33FFFFFF);
                context.fill(rightX + 11, sy + 4, rightX + 14, sy + 7, 0xFFFFFFFF);
                context.drawTextWithShadow(this.textRenderer, Text.literal(entry[0]), rightX + 22, sy, 0xFF2196F3);
                context.drawTextWithShadow(this.textRenderer, Text.literal(entry[1]), rightX + 80, sy, 0xFFFFFF);
                sy += 18;
            }

        } else if ("CONNECT".equals(activeTab)) {
            // Left Status Card
            TournamentListScreen.drawPremiumBeveledBox(context, 20, renderY, leftWidth, scissorHeight, 0x9F080C14, 0x30FFFFFF, 0x15FFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Registration & Whitelist"), 28, renderY + 8, 0xFF2196F3);
            context.fill(28, renderY + 18, 20 + leftWidth - 8, renderY + 19, 0x30FFFFFF); // Subtle 1px separator line
            
            int cy = renderY + 26;
            if ("APPROVED".equals(userStatus)) {
                // Glow box with beveled style
                int cardBgColor = 0x154CAF50;
                int cardBorderColor = 0xFF4CAF50;
                TournamentListScreen.drawPremiumBeveledBox(context, 28, cy, leftWidth - 16, 32, cardBgColor, cardBorderColor, cardBorderColor);
                
                context.drawTextWithShadow(this.textRenderer, Text.literal("✔ APPROVED COMPETITOR"), 34, cy + 4, 0xFF4CAF50);
                context.drawTextWithShadow(this.textRenderer, Text.literal("Whitelisted IGN: " + registeredIgn), 34, cy + 18, 0xFFFFFF);
                cy += 38;
                
                if (!broadcastMessage.isEmpty()) {
                    List<OrderedText> bcLines = this.textRenderer.wrapLines(Text.literal(broadcastMessage), leftWidth - 24);
                    int bcHeight = bcLines.size() * 10 + 16;
                    int bcBg = 0x1AFFB300;
                    int bcBorder = 0xFFFFB300;
                    TournamentListScreen.drawPremiumBeveledBox(context, 28, cy, leftWidth - 16, bcHeight, bcBg, bcBorder, bcBorder);
                    
                    context.drawTextWithShadow(this.textRenderer, Text.literal("📢 STAFF BROADCAST MESSAGE"), 34, cy + 4, 0xFFFFB300);
                    int bcy = cy + 16;
                    for (OrderedText line : bcLines) {
                        context.drawTextWithShadow(this.textRenderer, line, 34, bcy, 0xDDDDDD);
                        bcy += 10;
                    }
                }
            } else if ("PENDING".equals(userStatus)) {
                int cardBgColor = 0x15FFB300;
                int cardBorderColor = 0xFFFFB300;
                TournamentListScreen.drawPremiumBeveledBox(context, 28, cy, leftWidth - 16, 50, cardBgColor, cardBorderColor, cardBorderColor);
                
                context.drawTextWithShadow(this.textRenderer, Text.literal("⏳ AWAITING APPROVAL"), 34, cy + 4, 0xFFFFB300);
                
                List<OrderedText> pLines = this.textRenderer.wrapLines(Text.literal("Event staff is currently reviewing your registration request. Whitelist updates synchronize automatically."), leftWidth - 24);
                int py = cy + 18;
                for (OrderedText line : pLines) {
                    context.drawTextWithShadow(this.textRenderer, line, 34, py, 0xBBBBBB);
                    py += 10;
                }
            } else if ("REJECTED".equals(userStatus)) {
                int cardBgColor = 0x1AF44336;
                int cardBorderColor = 0xFFF44336;
                TournamentListScreen.drawPremiumBeveledBox(context, 28, cy, leftWidth - 16, 45, cardBgColor, cardBorderColor, cardBorderColor);
                
                context.drawTextWithShadow(this.textRenderer, Text.literal("❌ APPROVAL DECLINED"), 34, cy + 4, 0xFFF44336);
                
                List<OrderedText> rLines = this.textRenderer.wrapLines(Text.literal("Your application was declined by event administrators. Check compliance regulations or contact staff."), leftWidth - 24);
                int ry = cy + 18;
                for (OrderedText line : rLines) {
                    context.drawTextWithShadow(this.textRenderer, line, 34, ry, 0xBBBBBB);
                    ry += 10;
                }
            } else {
                int cardBgColor = 0x1AF44336; // Red background for not registered
                int cardBorderColor = 0xFFF44336; // Red border for not registered
                TournamentListScreen.drawPremiumBeveledBox(context, 28, cy, leftWidth - 16, 45, cardBgColor, cardBorderColor, cardBorderColor);
                
                context.drawTextWithShadow(this.textRenderer, Text.literal("❓ NOT REGISTERED"), 34, cy + 4, 0xFFF44336);
                
                List<OrderedText> unLines = this.textRenderer.wrapLines(Text.literal("You have not submitted a registration for this event yet. If registration is open, register now."), leftWidth - 24);
                int uny = cy + 18;
                for (OrderedText line : unLines) {
                    context.drawTextWithShadow(this.textRenderer, line, 34, uny, 0x999999);
                    uny += 10;
                }
            }

            // Right Server Connection Card
            TournamentListScreen.drawPremiumBeveledBox(context, rightX, renderY, rightWidth, scissorHeight, 0x9F080C14, 0x30FFFFFF, 0x15FFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Server Connection"), rightX + 8, renderY + 8, 0xFF2196F3);
            context.fill(rightX + 8, renderY + 18, rightX + rightWidth - 8, renderY + 19, 0x30FFFFFF); // Subtle 1px separator line

            boolean isServerJoinable = matchStarted || !serverIp.isEmpty();
            int btnY = renderY + 70;
            boolean btnVisible = (btnY >= 145 && btnY + 20 <= this.height - 20);

            if ("APPROVED".equals(userStatus) && isServerJoinable) {
                context.fill(rightX + 8, renderY + 26, rightX + rightWidth - 8, renderY + 58, 0x114CAF50);
                context.drawTextWithShadow(this.textRenderer, Text.literal("SERVER STATUS: ONLINE"), rightX + 14, renderY + 32, 0xFF4CAF50);
                context.drawTextWithShadow(this.textRenderer, Text.literal("Matches are in progress. Join now!"), rightX + 14, renderY + 44, 0x999999);

                joinButton.visible = btnVisible;
                joinButton.setX(rightX + 8);
                joinButton.setY(btnY);
                joinButton.setWidth(rightWidth - 16);
                joinButton.setHeight(20);
                registerButton.visible = false;

                // Pulsing glow border around button
                float pulse = (float) (Math.sin(time / 200.0) + 1.0) / 2.0f;
                int pulseAlpha = (int) (100 + (pulse * 155));
                int pulseColor = (pulseAlpha << 24) | 0x4CAF50;
                context.fill(rightX + 6, btnY - 2, rightX + rightWidth - 6, btnY - 1, pulseColor);
                context.fill(rightX + 6, btnY + 21, rightX + rightWidth - 6, btnY + 22, pulseColor);
                context.fill(rightX + 6, btnY - 2, rightX + 7, btnY + 22, pulseColor);
                context.fill(rightX + rightWidth - 7, btnY - 2, rightX + rightWidth - 6, btnY + 22, pulseColor);
            } else if ("NOT_REGISTERED".equals(userStatus) && "REG_OPEN".equals(status)) {
                context.drawTextWithShadow(this.textRenderer, Text.literal("Registration is open!"), rightX + 8, renderY + 26, 0xFF4CAF50);
                context.drawTextWithShadow(this.textRenderer, Text.literal("Submit details via companion website."), rightX + 8, renderY + 38, 0x999999);

                registerButton.visible = btnVisible;
                registerButton.active = true;
                registerButton.setMessage(Text.literal("Register Now"));
                registerButton.setX(rightX + 8);
                registerButton.setY(btnY);
                registerButton.setWidth(rightWidth - 16);
                registerButton.setHeight(20);
                joinButton.visible = false;
            } else if ("PENDING".equals(userStatus)) {
                context.drawTextWithShadow(this.textRenderer, Text.literal("Your status is pending."), rightX + 8, renderY + 26, 0xFFFF9800);
                context.drawTextWithShadow(this.textRenderer, Text.literal("You will connect here once approved."), rightX + 8, renderY + 38, 0x999999);

                registerButton.visible = btnVisible;
                registerButton.active = false;
                registerButton.setMessage(Text.literal("AWAITING APPROVAL"));
                registerButton.setX(rightX + 8);
                registerButton.setY(btnY);
                registerButton.setWidth(rightWidth - 16);
                registerButton.setHeight(20);
                joinButton.visible = false;
            } else {
                joinButton.visible = false;
                registerButton.visible = false;
                
                context.drawTextWithShadow(this.textRenderer, Text.literal("PROTECTED MODE ACTIVE"), rightX + 8, renderY + 26, 0xFFFFB300);
                List<OrderedText> pmLines = this.textRenderer.wrapLines(Text.literal("Strict client scans are active for this tournament. Cheating or invalid modpacks will log blacklists."), rightWidth - 16);
                int pmy = renderY + 38;
                for (OrderedText line : pmLines) {
                    context.drawTextWithShadow(this.textRenderer, line, rightX + 8, pmy, 0x888888);
                    pmy += 10;
                }
            }
        }

        context.disableScissor();

        // 3. Draw Scrollbar
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarX = this.width - 12;
            int scrollbarY = 145;
            int scrollbarHeight = scissorHeight;
            int scrollbarWidth = 4;
            
            context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0x22FFFFFF);
            int thumbHeight = (scrollbarHeight * scrollbarHeight) / (scrollbarHeight + maxScroll);
            if (thumbHeight < 15) thumbHeight = 15;
            int thumbY = scrollbarY + (scrollOffset * (scrollbarHeight - thumbHeight)) / maxScroll;
            context.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, 0x88FFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawSpecCard(DrawContext context, int x, int y, int w, String label, String value, int valColor) {
        // Stacked premium bevel box: label on top, value on bottom
        TournamentListScreen.drawPremiumBeveledBox(context, x, y, w, 34, 0x80080C14, 0x25FFFFFF, 0x10FFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), x + 8, y + 5, 0x888888);
        context.fill(x + 6, y + 14, x + w - 6, y + 15, 0x15FFFFFF); // Subtle 1px separator line
        context.drawTextWithShadow(this.textRenderer, Text.literal(value), x + 16, y + 20, valColor);
    }

    @Override
    public void tick() {
        super.tick();
        calculateCountdown();
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
