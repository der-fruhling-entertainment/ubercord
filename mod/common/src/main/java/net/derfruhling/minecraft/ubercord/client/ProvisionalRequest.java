package net.derfruhling.minecraft.ubercord.client;

import java.time.Instant;
import java.util.UUID;

public record ProvisionalRequest(
        String username,
        UUID uuid,
        long expiresAt
) {
}
