package net.derfruhling.minecraft.ubercord.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.derfruhling.minecraft.ubercord.DisplayConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public record ServerConfig(
        UUID serverId,
        UUID serverConfigId,
        @Nullable String botToken,
        @Nullable DisplayConfig display,
        @Nullable String authDomain,
        @Nullable String authKey
) {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public ServerConfig() {
        this(UUID.randomUUID(), UUID.randomUUID(), null, null, null, null);
    }

    public static ServerConfig loadOrDefault(MinecraftServer server) {
        Path configFile = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("ubercord-server.json");
        if (!Files.exists(configFile)) {
            ServerConfig cfg = new ServerConfig();

            try {
                cfg.save(server);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return cfg;
        } else {
            try {
                return GSON.fromJson(Files.readString(configFile), ServerConfig.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void save(MinecraftServer server) throws IOException {
        Path configDir = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("ubercord-server.json"), GSON.toJson(this), StandardCharsets.UTF_8);
    }
}
