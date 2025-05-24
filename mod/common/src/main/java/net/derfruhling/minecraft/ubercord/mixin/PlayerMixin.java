package net.derfruhling.minecraft.ubercord.mixin;

import net.derfruhling.minecraft.ubercord.DiscordIdContainer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
@Implements({@Interface(iface = DiscordIdContainer.class, prefix = "uber$")})
public class PlayerMixin {
    private @Unique long userId = -1;

    public long uber$getUserId() {
        return userId;
    }

    public void uber$setUserId(long userId) {
        this.userId = userId;
    }
}
