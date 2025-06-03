package net.derfruhling.minecraft.ubercord.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerState {
    public enum PreferredAuth {
        Provisional,
        Discord
    }

    record AuthState(String token, String refreshToken) {}

    private final UUID serverUniqueId;
    private PreferredAuth preferredAuth = PreferredAuth.Discord;
    private @Nullable AuthState provisionalAuth = null;
    private List<String> joinedChannels = new ArrayList<>();

    public PlayerState(UUID serverUniqueId) {
        this.serverUniqueId = serverUniqueId;
    }

    public UUID getServerUniqueId() {
        return serverUniqueId;
    }

    public PreferredAuth getPreferredAuth() {
        return preferredAuth;
    }

    public void setPreferredAuth(PreferredAuth preferredAuth) {
        this.preferredAuth = preferredAuth;
    }

    @Nullable AuthState getProvisionalAuth() {
        return provisionalAuth;
    }

    void setProvisionalAuth(@Nullable AuthState provisionalAuth) {
        this.provisionalAuth = provisionalAuth;
    }

    public List<String> getJoinedChannels() {
        return joinedChannels;
    }

    public static PlayerState loadMaybe(UUID serverUniqueId) {
        Path configFile = Minecraft.getInstance().gameDirectory.toPath().resolve("ubercord").resolve("servers").resolve(serverUniqueId + ".json");
        if (!Files.exists(configFile)) {
            return new PlayerState(serverUniqueId);
        } else {
            try {
                return new Gson().fromJson(Files.readString(configFile), PlayerState.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void save() throws IOException {
        Path configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("ubercord").resolve("servers");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve(serverUniqueId + ".json"), new Gson().toJson(this), StandardCharsets.UTF_8);
    }
}
