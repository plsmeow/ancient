package meow.ancient.mixin;

import meow.ancient.client.util.render.ChamsEntityRenderState;
import meow.ancient.module.modules.render.ChamsModule;
import meow.ancient.module.modules.render.NoRenderModule;
import meow.ancient.module.modules.render.TagsModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void ancient$extractChamsTarget(T entity, S state, float partialTick, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noGlowing()) state.outlineColor = 0;
        if (NoRenderModule.INSTANCE.noInvisibility()) state.isInvisible = false;
        ((ChamsEntityRenderState) state).ancient$setChamsTarget(ChamsModule.INSTANCE.shouldRender(entity));
    }

    @Inject(
        method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
        at = @At("TAIL")
    )
    private void hideVanillaNameTags(T entity, S state, float partialTick, double nameTagDist, double belowNameDist, CallbackInfo ci) {
        if (TagsModule.INSTANCE.shouldHideVanillaTag(entity) || NoRenderModule.INSTANCE.noNametags()) {
            state.nameTag = null;
            state.scoreText = null;
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void onShouldRender(T entity, Frustum frustum, double d, double e, double f, float g, CallbackInfoReturnable<Boolean> cir) {
        if (NoRenderModule.INSTANCE.noEntity(entity)) {
            cir.setReturnValue(false);
        } else if (NoRenderModule.INSTANCE.noDeadEntities() && entity instanceof LivingEntity && !((LivingEntity) entity).isAlive()) {
            cir.setReturnValue(false);
        }
    }
}
