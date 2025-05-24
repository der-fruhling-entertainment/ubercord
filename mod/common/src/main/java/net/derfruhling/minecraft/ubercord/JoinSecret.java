package net.derfruhling.minecraft.ubercord;

import com.google.common.io.ByteStreams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;

import java.io.IOException;
import java.util.Base64;

public record JoinSecret(String inviter, String ip, int port) {
    public String encode() {
        CompoundTag tag = new CompoundTag();
        tag.putString("i", inviter);
        tag.putString("a", ip);
        tag.putInt("p", port);

        var out = ByteStreams.newDataOutput();
        try {
            tag.write(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    public static JoinSecret decode(String joinSecret) {
        byte[] bytes = Base64.getDecoder().decode(joinSecret);
        CompoundTag tag;
        try {
            tag = CompoundTag.TYPE.load(ByteStreams.newDataInput(bytes), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new JoinSecret(tag.getString("i"), tag.getString("a"), tag.getInt("p"));
    }
}
