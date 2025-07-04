package net.derfruhling.minecraft.ubercord.mixin;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.widget.SpruceLabelWidget;
import net.derfruhling.minecraft.ubercord.client.DisplaysStatusMessage;
import net.derfruhling.minecraft.ubercord.client.PlatformPlacement;
import net.derfruhling.minecraft.ubercord.client.StatusMessage;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.derfruhling.minecraft.ubercord.gui.StatusMessageWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen implements DisplaysStatusMessage {
    @Unique private StatusMessageWidget ubercord$additionalText;

    protected TitleScreenMixin(Component component) {
        super(component);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void init(CallbackInfo ci) {
        ubercord$additionalText = new StatusMessageWidget(Position.of(2, height - PlatformPlacement.getOffsetForTitleScreenStatus()), width, false);
        addRenderableWidget(ubercord$additionalText);
    }
}
