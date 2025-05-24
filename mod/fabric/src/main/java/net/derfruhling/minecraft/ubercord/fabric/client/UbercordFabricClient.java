package net.derfruhling.minecraft.ubercord.fabric.client;

import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class UbercordFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if(FabricLoader.getInstance().isModLoaded("cloth-config")) {
            UbercordClient.setClothConfigPresent(true);
        }

        UbercordClient.init();
    }
}
