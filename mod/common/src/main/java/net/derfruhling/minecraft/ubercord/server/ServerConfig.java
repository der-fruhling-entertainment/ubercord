package net.derfruhling.minecraft.ubercord.server;

import com.google.gson.Gson;
import net.derfruhling.minecraft.ubercord.DisplayConfig;
import net.derfruhling.minecraft.ubercord.DisplayMode;
import net.derfruhling.minecraft.ubercord.client.ClientConfig;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record ServerConfig(
        long clientId,
        @Nullable String botToken,
        @Nullable DisplayConfig display,
        @Nullable String authKey
) {
    public static @Nullable ServerConfig loadMaybe() {
        Path configFile = Path.of("config").resolve("ubercord-server.json");
        if (!Files.exists(configFile)) {
            return null;
        } else {
            try {
                return new Gson().fromJson(Files.readString(configFile), ServerConfig.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void save() throws IOException {
        Path configDir = Path.of("config");
        Files.writeString(configDir.resolve("ubercord-server.json"), new Gson().toJson(this), StandardCharsets.UTF_8);
    }
}
