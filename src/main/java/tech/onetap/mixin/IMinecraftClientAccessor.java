package tech.onetap.mixin;

import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.MinecraftClient;

@Mixin(MinecraftClient.class)
public interface IMinecraftClientAccessor {
    @Mutable
    @Accessor("session")
    void setSession(Session session);
}
