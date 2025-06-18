package net.derfruhling.minecraft.ubercord.gui;

import net.derfruhling.discord.socialsdk4j.User;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public abstract class Avatar {
    public abstract @Nullable AbstractTexture getTexture();
    public abstract ResourceLocation getTextureLocation();
    public abstract boolean isReady();

    public static Avatar forUser(Minecraft client, User user) {
        if(user.isProvisional()) {
            return new MinecraftAvatar(client, user);
        } else {
            return new DiscordAvatar(client, user);
        }
    }
}
