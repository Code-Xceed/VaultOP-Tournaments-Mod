package com.codex.vaultop_tournaments;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ProtectedModePreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        System.out.println("[VaultOP] Running Protected Mode Check...");

        List<String> unauthorizedMods = new ArrayList<>();

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            
            // Check if it's a bundled mod (Jar in Jar)
            boolean isBundled = mod.getContainingMod().isPresent();
            if (isBundled) {
                // Implicitly allow nested/bundled mods if their parent is allowed.
                String parentId = mod.getContainingMod().get().getMetadata().getId();
                if (isAllowed(parentId)) {
                    continue;
                }
            }

            if (!isAllowed(modId)) {
                unauthorizedMods.add(mod.getMetadata().getName() + " (" + modId + ")");
            }
        }

        if (!unauthorizedMods.isEmpty()) {
            System.err.println("[VaultOP] Protected Mode Triggered! Unauthorized mods found:");
            for (String m : unauthorizedMods) {
                System.err.println(" - " + m);
            }
            showErrorDialogAndExit(unauthorizedMods);
        } else {
            System.out.println("[VaultOP] Protected Mode Passed. All mods verified.");
        }
    }

    private boolean isAllowed(String modId) {
        // Core Minecraft & Loader
        if (modId.equals("minecraft") || modId.equals("java") || modId.equals("fabricloader")) {
            return true;
        }
        // Our Mod
        if (modId.equals("vaultop_tournaments")) {
            return true;
        }
        // Approved Mods
        if (modId.equals("modmenu") || modId.equals("sodium") || modId.equals("vulkanmod")) {
            return true;
        }
        // UI & API Libraries
        if (modId.equals("placeholder-api") || modId.contains("cloth-config") || modId.contains("cloth_config") || 
            modId.contains("yacl") || modId.contains("yet_another_config_lib")) {
            return true;
        }
        // Fabric API & its submodules
        if (modId.equals("fabric") || modId.startsWith("fabric-")) {
            return true;
        }
        return false;
    }

    private void showErrorDialogAndExit(List<String> unauthorizedMods) {
        // Set System Look and Feel for a native appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore if L&F fails, it will fallback to Metal
        }

        // We use HTML inside the JLabel to give it a slightly nicer look.
        StringBuilder htmlMsg = new StringBuilder();
        htmlMsg.append("<html><body style='width: 350px; font-family: sans-serif; color: #E0E0E0;'>");
        htmlMsg.append("<h2 style='color: #FF5555; margin-top: 0;'>Protected Mode Active</h2>");
        htmlMsg.append("<p>The following unauthorized mods were detected in your <b>mods</b> folder:</p>");
        htmlMsg.append("<ul style='color: #FFAA00;'>");
        for (String m : unauthorizedMods) {
            htmlMsg.append("<li>").append(m).append("</li>");
        }
        htmlMsg.append("</ul>");
        
        htmlMsg.append("<p>Only the following mods are permitted to be installed:</p>");
        htmlMsg.append("<ul style='color: #55FF55;'>");
        htmlMsg.append("<li>VaultOP Tournaments</li>");
        htmlMsg.append("<li>Fabric API</li>");
        htmlMsg.append("<li>Mod Menu</li>");
        htmlMsg.append("<li>Sodium</li>");
        htmlMsg.append("<li>VulkanMod</li>");
        htmlMsg.append("<li>Placeholder API</li>");
        htmlMsg.append("<li>Cloth Config</li>");
        htmlMsg.append("<li>YetAnotherConfigLib (YACL)</li>");
        htmlMsg.append("</ul>");
        
        htmlMsg.append("<p>Please remove the unauthorized mods to launch the game.</p>");
        htmlMsg.append("</body></html>");

        // Custom JOptionPane styling for a darker theme
        UIManager.put("OptionPane.background", new Color(30, 30, 30));
        UIManager.put("Panel.background", new Color(30, 30, 30));

        JOptionPane.showMessageDialog(
            null, 
            htmlMsg.toString(), 
            "VaultOP - Protected Mode", 
            JOptionPane.ERROR_MESSAGE
        );
        
        System.exit(1);
    }
}
