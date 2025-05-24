package net.derfruhling.minecraft.ubercord.server;

import com.google.common.io.ByteStreams;
import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;

import java.io.IOException;
import java.util.Base64;

public record AuthenticationConfig(
        String clientId,
        String clientKey
) {
    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", clientId);
        tag.putString("key", clientKey);
        return tag;
    }

    public static AuthenticationConfig decode(CompoundTag tag) {
        return new AuthenticationConfig(tag.getString("id"), tag.getString("key"));
    }

    public static AuthenticationConfig decode(String key) {
        byte[] ciphertext = Base64.getDecoder().decode(key);
        byte[] decoded = Ubercord.decryptSecretText(ciphertext);
        CompoundTag tag;

        try {
            tag = CompoundTag.TYPE.load(ByteStreams.newDataInput(decoded), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return decode(tag);
    }
}
