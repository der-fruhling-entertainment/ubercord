package net.derfruhling.minecraft.ubercord.gui;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import net.derfruhling.discord.socialsdk4j.User;
import net.derfruhling.minecraft.ubercord.client.RemotePlayerInfo;
import net.minecraft.client.Minecraft;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class MinecraftAvatar extends Avatar {
    private static final Logger log = LoggerFactory.getLogger(MinecraftAvatar.class);
    private AbstractTexture texture;
    private ResourceLocation resourceLocation;
    private boolean isReady = false;
    private boolean isReal;

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

    public MinecraftAvatar(Minecraft client, User targetUser) {
        Path playerInfoPath = Path.of("ubercord", "player_list", targetUser.id + ".json");
        if(Files.exists(playerInfoPath)) {
            RemotePlayerInfo info;

            try {
                info = new Gson().fromJson(Files.readString(playerInfoPath), RemotePlayerInfo.class);
            } catch (IOException e) {
                this.resourceLocation = ResourceLocation.fromNamespaceAndPath("ubercord", "gui/default_provisional_avatar");
                this.isReal = false;
                this.isReady = true;
                log.error("Failed to read player info for user {}", targetUser.id, e);
                return;
            }

            this.resourceLocation = ResourceLocation.fromNamespaceAndPath("ubercord", "dynamic/user_avatars/" + targetUser.id);
            this.isReal = true;
            initialize(client, info.minecraftUUID());
        } else {
            useDefaultAvatar();
        }
    }

    private void useDefaultAvatar() {
        this.resourceLocation = ResourceLocation.fromNamespaceAndPath("ubercord", "textures/gui/default_provisional_avatar.png");
        this.isReal = false;
        this.isReady = true;
    }

    private void initialize(Minecraft client, UUID uuid) {
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
                                    .uri(URI.create("https://mc-heads.net/avatar/" + uuid + "/8"))
                                    .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord)")
                                    .header("Accept", "image/png")
                                    .build(),
                            HttpResponse.BodyHandlers.ofInputStream()
                    )
                    .<Void>handleAsync((response, throwable) -> {
                        if(throwable != null) {
                            log.error("Failed to retrieve player {}'s avatar", uuid, throwable);
                            useDefaultAvatar();
                        } else if(response.statusCode() != 200) {
                            if(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json")) {
                                try {
                                    log.error("HTTP {} trying to retrieve player {}'s avatar: {}", response.statusCode(), uuid, new String(response.body().readAllBytes(), StandardCharsets.UTF_8));
                                } catch (IOException e) {
                                    log.error("HTTP {} trying to retrieve player {}'s avatar, experienced exception trying to read body", response.statusCode(), uuid, e);
                                } finally {
                                    useDefaultAvatar();
                                }
                            } else {
                                log.error("HTTP {} trying to retrieve player {}'s avatar, body is not present / is not JSON", response.statusCode(), uuid);
                                useDefaultAvatar();
                            }
                        } else {
                            client.execute(() -> {
                                try {
                                    texture = new DynamicTexture(NativeImage.read(response.body()));
                                    client.getTextureManager().register(resourceLocation, texture);
                                    log.info("Got player {}'s avatar", uuid);
                                    isReady = true;
                                } catch (IOException e) {
                                    log.error("Got exception reading player {}'s avatar as an image", uuid, e);
                                    useDefaultAvatar();
                                }
                            });
                        }

                        return null;
                    });
        });
    }
}
