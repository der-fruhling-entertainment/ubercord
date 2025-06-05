package net.derfruhling.minecraft.ubercord;

import net.derfruhling.discord.socialsdk4j.ActivityBuilder;
import net.derfruhling.discord.socialsdk4j.ActivityType;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public record DisplayMode(
        ActivityType type,
        String state,
        String details,
        @Nullable ActivityBuilder.Assets assets
) {
    public void encode(CompoundTag tag) {
        tag.putInt("type", type.ordinal());
        tag.putString("state", state);
        tag.putString("details", details);
        if (assets != null) {
            CompoundTag assetsTag = new CompoundTag();

            if(assets.small() != null) {
                CompoundTag smallTag = new CompoundTag();
                smallTag.putString("image", assets.small().image());

                if(assets.small().text() != null) {
                    smallTag.putString("text", assets.small().text());
                }

                assetsTag.put("small", smallTag);
            }

            if(assets.large() != null) {
                CompoundTag largeTag = new CompoundTag();
                largeTag.putString("image", assets.large().image());

                if(assets.large().text() != null) {
                    largeTag.putString("text", assets.large().text());
                }

                assetsTag.put("large", largeTag);
            }

            tag.put("assets", assetsTag);
        }
    }

    public static DisplayMode decode(CompoundTag tag) {
        ActivityType type = ActivityType.values()[tag.getInt("type")];
        String state = tag.getString("state");
        String details = tag.getString("details");

        String smallImage = null;
        String smallText = null;
        String largeImage = null;
        String largeText = null;

        if (tag.contains("assets")) {
            CompoundTag assetsTag = tag.getCompound("assets");

            if (assetsTag.contains("small")) {
                CompoundTag smallTag = assetsTag.getCompound("small");
                smallImage = smallTag.getString("image");

                if (smallTag.contains("text")) {
                    smallText = smallTag.getString("text");
                }
            }

            if (assetsTag.contains("large")) {
                CompoundTag largeTag = assetsTag.getCompound("large");
                largeImage = largeTag.getString("image");

                if (largeTag.contains("text")) {
                    largeText = largeTag.getString("text");
                }
            }
        }

        ActivityBuilder.Assets assets = null;

        if (smallImage != null || largeImage != null) {
            assets = new ActivityBuilder.Assets(
                    largeImage != null ? new ActivityBuilder.Asset(largeImage, largeText) : null,
                    smallImage != null ? new ActivityBuilder.Asset(smallImage, smallText) : null
            );
        }

        return new DisplayMode(type, state, details, assets);
    }

    public CompoundTag toCompoundTag() {
        CompoundTag tag = new CompoundTag();
        encode(tag);
        return tag;
    }
}
