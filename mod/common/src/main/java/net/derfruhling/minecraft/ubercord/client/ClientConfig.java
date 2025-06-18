package net.derfruhling.minecraft.ubercord.client;

import com.google.gson.Gson;
import net.derfruhling.discord.socialsdk4j.ActivityBuilder;
import net.derfruhling.discord.socialsdk4j.ActivityType;
import net.derfruhling.minecraft.ubercord.DisplayConfig;
import net.derfruhling.minecraft.ubercord.DisplayMode;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private long defaultClientId = 1367390201621512313L;

    private DisplayMode titleDisplay = new DisplayMode(
            ActivityType.Playing,
            "On the menu",
            "Thinking about what to do...",
            new ActivityBuilder.Assets(
                    new ActivityBuilder.Asset("minecraft", "%player_name%: Minecraft %version%"),
                    new ActivityBuilder.Asset("idle", "Waiting for something to happen?")
            )
    );

    private DisplayConfig defaultConfig = DisplayConfig.DEFAULT_CLIENT;

    private boolean hasAgreedToProvisionalServiceUsage = false;

    public ClientConfig(long defaultClientId, DisplayMode titleDisplay, DisplayConfig defaultConfig, boolean hasAgreedToProvisionalServiceUsage) {
        this.defaultClientId = defaultClientId;
        this.titleDisplay = titleDisplay;
        this.defaultConfig = defaultConfig;
        this.hasAgreedToProvisionalServiceUsage = hasAgreedToProvisionalServiceUsage;
    }

    public ClientConfig() {}

    public static ClientConfig loadMaybe() {
        Path configFile = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("ubercord-client.json");
        if (!Files.exists(configFile)) {
            return new ClientConfig();
        } else {
            try {
                return new Gson().fromJson(Files.readString(configFile), ClientConfig.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void save() throws IOException {
        Path configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
        Files.writeString(configDir.resolve("ubercord-client.json"), new Gson().toJson(this), StandardCharsets.UTF_8);
    }

    public long getDefaultClientId() {
        return defaultClientId;
    }

    public DisplayMode getTitleDisplay() {
        return titleDisplay;
    }

    public DisplayConfig getDefaultConfig() {
        return defaultConfig == null ? DisplayConfig.DEFAULT_CLIENT : defaultConfig;
    }

    public boolean hasAgreedToProvisionalServiceUsage() {
        return hasAgreedToProvisionalServiceUsage;
    }

    public void setHasAgreedToProvisionalServiceUsage(boolean hasAgreedToProvisionalServiceUsage) {
        this.hasAgreedToProvisionalServiceUsage = hasAgreedToProvisionalServiceUsage;
    }
}
