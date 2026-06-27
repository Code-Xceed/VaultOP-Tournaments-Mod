package com.codex.vaultop_tournaments;

import net.fabricmc.api.ModInitializer;

public class ExampleModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ExampleMod.init();
        com.vaultop.mod.VaultOPMod.init();
    }
}

