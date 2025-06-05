package net.derfruhling.minecraft.ubercord;

import net.derfruhling.discord.socialsdk4j.ActivityBuilder;
import net.derfruhling.discord.socialsdk4j.ActivityType;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public record DisplayConfig(
        DisplayMode idle,
        @Nullable DisplayMode playingSingleplayer,
        DisplayMode playingMultiplayer,
        @Nullable DisplayMode playingRealms,
        HashMap<String, DisplayMode> dimensions,
        HashMap<String, String> dimensionNames
) {
    public static final DisplayConfig DEFAULT_CLIENT = new DisplayConfig(
            new DisplayMode(ActivityType.Playing, "Idling...", "Waiting for something to happen?", new ActivityBuilder.Assets(
                    new ActivityBuilder.Asset("minecraft", "%player_name%: Minecraft %version%"),
                    new ActivityBuilder.Asset("idle", "Waiting for something to happen?")
            )),
            new DisplayMode(ActivityType.Playing, "Playing singleplayer", "%world_name%", new ActivityBuilder.Assets(
                    new ActivityBuilder.Asset("minecraft", "%player_name%: Minecraft %version%"),
                    new ActivityBuilder.Asset("%dimension_id%", "In %dimension%")
            )),
            new DisplayMode(ActivityType.Playing, "Playing online", "%server_name%", new ActivityBuilder.Assets(
                    new ActivityBuilder.Asset("minecraft", "%player_name%: Minecraft %version%"),
                    new ActivityBuilder.Asset("%dimension_id%", "In %dimension%")
            )),
            new DisplayMode(ActivityType.Playing, "Playing online", "In a realm...", new ActivityBuilder.Assets(
                    new ActivityBuilder.Asset("minecraft", "%player_name%: Minecraft %version%"),
                    new ActivityBuilder.Asset("%dimension_id%", "In %dimension%")
            )),
            new HashMap<>(),
            new HashMap<>()
    );

    static {
        DEFAULT_CLIENT.dimensionNames.put("minecraft:overworld", "the Overworld");
        DEFAULT_CLIENT.dimensionNames.put("minecraft:nether", "the Nether");
        DEFAULT_CLIENT.dimensionNames.put("minecraft:the_end", "the End");
    }

    public void encode(CompoundTag tag) {
        tag.put("idle", idle.toCompoundTag());

        if(playingSingleplayer != null) {
            tag.put("playing_s", playingSingleplayer.toCompoundTag());
        }

        tag.put("playing_m", playingMultiplayer.toCompoundTag());

        if(playingRealms != null) {
            tag.put("playing_r", playingRealms.toCompoundTag());
        }

        CompoundTag dims = new CompoundTag();

        dimensions.forEach((resourceLocation, displayMode) -> {
            dims.put(resourceLocation.toString(), displayMode.toCompoundTag());
        });

        tag.put("dimensions", dims);

        CompoundTag dimNames = new CompoundTag();

        dimensionNames.forEach((resourceLocation, name) -> {
            dimNames.putString(resourceLocation.toString(), name);
        });

        tag.put("dimension_names", dimNames);
    }

    public static DisplayConfig decode(CompoundTag tag) {
        DisplayMode idle = DisplayMode.decode(tag.getCompound("idle"));
        DisplayMode playingSingleplayer = tag.contains("playing_s")
                ? DisplayMode.decode(tag.getCompound("playing_s"))
                : null;
        DisplayMode playingMultiplayer = DisplayMode.decode(tag.getCompound("playing_m"));
        DisplayMode playingRealms = tag.contains("playing_r")
                ? DisplayMode.decode(tag.getCompound("playing_r"))
                : null;

        HashMap<String, DisplayMode> dimensions = new HashMap<>();
        CompoundTag dims = tag.getCompound("dimensions");

        for (String key : dims.getAllKeys()) {
            dimensions.put(key, DisplayMode.decode(dims.getCompound(key)));
        }

        HashMap<String, String> dimensionNames = new HashMap<>();
        CompoundTag dimNames = tag.getCompound("dimension_names");

        for (String key : dimNames.getAllKeys()) {
            dimensionNames.put(key, dimNames.getString(key));
        }

        return new DisplayConfig(idle, playingSingleplayer, playingMultiplayer, playingRealms, dimensions, dimensionNames);
    }

    public CompoundTag toCompoundTag() {
        CompoundTag tag = new CompoundTag();
        encode(tag);
        return tag;
    }
}
