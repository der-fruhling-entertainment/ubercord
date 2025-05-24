package net.derfruhling.minecraft.ubercord.fabric.server;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.fabricmc.api.DedicatedServerModInitializer;

public final class UbercordFabricServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        Ubercord.initDedicatedServer();
    }
}
