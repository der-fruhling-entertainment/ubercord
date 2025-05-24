package net.derfruhling.minecraft.ubercord.server;

import java.util.UUID;

public record AuthorizeUserRequest(
        UUID id,
        String state
) {
}
