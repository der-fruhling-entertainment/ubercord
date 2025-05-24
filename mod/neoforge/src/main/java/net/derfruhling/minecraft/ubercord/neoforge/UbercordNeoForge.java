package net.derfruhling.minecraft.ubercord.neoforge;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;

@Mod(Ubercord.MOD_ID)
public final class UbercordNeoForge {
    public UbercordNeoForge() {
        // Run our common setup.
        Ubercord.init();

        switch (FMLEnvironment.dist) {
            case CLIENT -> {
                if(FMLLoader.getLoadingModList().getModFileById("cloth-config") != null) {
                    UbercordClient.setClothConfigPresent(true);
                }
                UbercordClient.init();
            }
            case DEDICATED_SERVER -> Ubercord.initDedicatedServer();
        }
    }
}
