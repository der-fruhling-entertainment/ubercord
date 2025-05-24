package net.derfruhling.minecraft.ubercord.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class VolatileClientConfig {
    public @Nullable String preferredChannel;

    public VolatileClientConfig(@Nullable String preferredChannel) {
        this.preferredChannel = preferredChannel;
    }

    public VolatileClientConfig() {
        this.preferredChannel = null;
    }

    public static VolatileClientConfig loadMaybe() {
        Path configFile = Minecraft.getInstance().gameDirectory.toPath().resolve("ubercord").resolve("user-prefs.json");
        if (!Files.exists(configFile)) {
            return new VolatileClientConfig();
        } else {
            try {
                return new Gson().fromJson(Files.readString(configFile), VolatileClientConfig.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void save() throws IOException {
        Path configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("ubercord");
        Files.writeString(configDir.resolve("user-prefs.json"), new Gson().toJson(this), StandardCharsets.UTF_8);
    }

    public @Nullable String getPreferredChannel() {
        return preferredChannel;
    }

    public void setPreferredChannel(String preferredChannel) throws IOException {
        this.preferredChannel = preferredChannel;
        save();
    }
}
