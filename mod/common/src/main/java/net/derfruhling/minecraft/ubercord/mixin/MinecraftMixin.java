package net.derfruhling.minecraft.ubercord.mixin;

import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "addInitialScreens", at = @At("HEAD"))
    private void addInitialScreens(List<Function<Runnable, Screen>> list, CallbackInfo ci) {
        list.addAll(Arrays.asList(UbercordClient.consumeScreensToShowBeforeTitle()));
    }
}
