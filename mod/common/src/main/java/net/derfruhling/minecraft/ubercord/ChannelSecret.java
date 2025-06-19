package net.derfruhling.minecraft.ubercord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

public final class ChannelSecret {
    private ChannelSecret() {}

    public static String generateGlobalSecret(String name) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(String.format("::%s", name).getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param name The name for the channel
     * @param serverId A unique ID representing the server
     * @return A secret which can be joined
     * @deprecated Do not use this, use the üPS API to make server lobbies.
     */
    @Deprecated
    public static String generateServerBasedSecret(UUID serverId, String name) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(String.format("~%s:%s", serverId, name).getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
