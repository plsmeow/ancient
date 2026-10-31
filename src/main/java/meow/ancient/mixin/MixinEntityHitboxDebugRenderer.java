package meow.ancient.mixin;

import meow.ancient.client.util.friend.FriendRepository;
import meow.ancient.module.modules.combat.HitBoxesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
public class MixinEntityHitboxDebugRenderer {
    @Inject(method = "showHitboxes", at = @At("TAIL"))
    private void onShowHitboxes(Entity entity, float partialTicks, boolean bl, CallbackInfo ci) {
        if (!HitBoxesModule.INSTANCE.isEnabled()) return;
        if (!(entity instanceof Player player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || player == mc.player) return;
        if (FriendRepository.INSTANCE.isFriend(player)) return;

        double expand = HitBoxesModule.size.getValue().doubleValue();
        if (expand <= 0.0) return;

        Vec3 pos = entity.position();
        Vec3 renderPos = entity.getPosition(partialTicks);
        Vec3 diff = renderPos.subtract(pos);
        AABB box = entity.getBoundingBox().move(diff).inflate(expand);

        Gizmos.cuboid(box, GizmoStyle.stroke(0xFFFF4444, 2.0f));
    }
}
