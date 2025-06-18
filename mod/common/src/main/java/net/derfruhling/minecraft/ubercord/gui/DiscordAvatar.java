package net.derfruhling.minecraft.ubercord.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.derfruhling.discord.socialsdk4j.User;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class DiscordAvatar extends Avatar {
    private static final Logger log = LoggerFactory.getLogger(DiscordAvatar.class);
    private AbstractTexture texture;
    private ResourceLocation resourceLocation;
    private boolean isReady = false;

    @Override
    public @Nullable AbstractTexture getTexture() {
        return texture;
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return resourceLocation;
    }

    @Override
    public boolean isReady() {
        return isReady;
    }

    private static final HttpClient http = HttpClient.newBuilder().build();

    public DiscordAvatar(Minecraft client, long userId, String avatarHash) {
        if(avatarHash != null) {
            this.resourceLocation = ResourceLocation.fromNamespaceAndPath("ubercord", "dynamic/user_avatars/" + userId);

            client.execute(() -> {
                //noinspection ConstantValue,DataFlowIssue
                if(client.getTextureManager().getTexture(resourceLocation, null) != null) {
                    texture = client.getTextureManager().getTexture(resourceLocation);
                    isReady = true;
                    return;
                }

                http.sendAsync(
                                HttpRequest.newBuilder()
                                        .GET()
                                        .uri(URI.create("https://cdn.discordapp.com/avatars/" + userId + "/" + avatarHash + ".png?size=64"))
                                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord)")
                                        .build(),
                                HttpResponse.BodyHandlers.ofInputStream()
                        )
                        .<Void>handleAsync((response, throwable) -> {
                            if(throwable != null) {
                                log.error("Failed to retrieve {}'s avatar {}", userId, avatarHash, throwable);
                            } else if(response.statusCode() != 200) {
                                if(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json")) {
                                    try {
                                        log.error("HTTP {} trying to retrieve {}'s avatar {}: {}", response.statusCode(), userId, avatarHash, new String(response.body().readAllBytes(), StandardCharsets.UTF_8));
                                    } catch (IOException e) {
                                        log.error("HTTP {} trying to retrieve {}'s avatar {}, experienced exception trying to read body", response.statusCode(), userId, avatarHash, e);
                                    }
                                } else {
                                    log.error("HTTP {} trying to retrieve {}'s avatar {}, body is not present / is not JSON", response.statusCode(), userId, avatarHash);
                                }
                            } else {
                                client.execute(() -> {
                                    try {
                                        texture = new DynamicTexture(NativeImage.read(response.body()));
                                        client.getTextureManager().register(resourceLocation, texture);
                                        log.info("Got {}'s avatar", userId);
                                        isReady = true;
                                    } catch (IOException e) {
                                        log.error("Got exception reading {}'s avatar as an image", userId, e);
                                    }
                                });
                            }

                            return null;
                        });
            });
        } else {
            this.resourceLocation = ResourceLocation.fromNamespaceAndPath("ubercord", "textures/gui/default_avatar.png");
            this.isReady = true;
        }
    }

    public DiscordAvatar(Minecraft client, User user) {
        this(client, user.id, user.getAvatar());
    }
}
