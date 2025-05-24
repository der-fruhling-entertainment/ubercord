package net.derfruhling.minecraft.ubercord;

import net.derfruhling.discord.socialsdk4j.Activity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public record DisplayConfig(
        DisplayMode idle,
        @Nullable DisplayMode playingSingleplayer,
        DisplayMode playingMultiplayer,
        @Nullable DisplayMode playingRealms,
        HashMap<ResourceLocation, DisplayMode> dimensions,
        HashMap<ResourceLocation, String> dimensionNames
) {
    public static final DisplayConfig DEFAULT_CLIENT = new DisplayConfig(
            new DisplayMode("Minecraft", Activity.Type.Playing, "Idling...", "Waiting for something to happen?", new Activity.Assets(
                    new Activity.Asset("%dimension_id%", "In %dimension%"),
                    new Activity.Asset("idle", "%player_name%")
            )),
            new DisplayMode("Minecraft", Activity.Type.Playing, "Playing singleplayer", "%world_name%", new Activity.Assets(
                    new Activity.Asset("%dimension_id%", "In %dimension%"),
                    new Activity.Asset("online", "%player_name%")
            )),
            new DisplayMode("Minecraft", Activity.Type.Playing, "Playing online", "%server_name%", new Activity.Assets(
                    new Activity.Asset("%dimension_id%", "In %dimension%"),
                    new Activity.Asset("online", "%player_name%")
            )),
            new DisplayMode("Minecraft", Activity.Type.Playing, "Playing online", "In a realm...", new Activity.Assets(
                    new Activity.Asset("%dimension_id%", "In %dimension%"),
                    new Activity.Asset("online", "%player_name%")
            )),
            new HashMap<>(),
            new HashMap<>()
    );

    static {
        DEFAULT_CLIENT.dimensionNames.put(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), "the Overworld");
        DEFAULT_CLIENT.dimensionNames.put(ResourceLocation.fromNamespaceAndPath("minecraft", "nether"), "the Nether");
        DEFAULT_CLIENT.dimensionNames.put(ResourceLocation.fromNamespaceAndPath("minecraft", "the_end"), "the End");
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

        HashMap<ResourceLocation, DisplayMode> dimensions = new HashMap<>();
        CompoundTag dims = tag.getCompound("dimensions");

        for (String key : dims.getAllKeys()) {
            dimensions.put(ResourceLocation.parse(key), DisplayMode.decode(dims.getCompound(key)));
        }

        HashMap<ResourceLocation, String> dimensionNames = new HashMap<>();
        CompoundTag dimNames = tag.getCompound("dimension_names");

        for (String key : dimNames.getAllKeys()) {
            dimensionNames.put(ResourceLocation.parse(key), dimNames.getString(key));
        }

        return new DisplayConfig(idle, playingSingleplayer, playingMultiplayer, playingRealms, dimensions, dimensionNames);
    }

    public CompoundTag toCompoundTag() {
        CompoundTag tag = new CompoundTag();
        encode(tag);
        return tag;
    }
}
