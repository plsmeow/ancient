package meow.ancient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayerRenderer.class)
public class MixinEquipmentLayerRenderer {
    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At("HEAD"), cancellable = true)
    private void ancient$hideArmor(EquipmentClientInfo.LayerType type, ResourceKey<EquipmentAsset> asset,
        Model<?> model, Object state, ItemStack stack, PoseStack poses, SubmitNodeCollector collector,
        int light, Identifier texture, int outlineColor, int order, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noArmor() && switch (type) {
            case HUMANOID, HUMANOID_LEGGINGS, HUMANOID_BABY, WOLF_BODY, HORSE_BODY, NAUTILUS_BODY -> true;
            default -> false;
        }) ci.cancel();
    }

    @Redirect(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"))
    private boolean ancient$hideArmorGlint(ItemStack stack) {
        return !NoRenderModule.INSTANCE.noEnchantGlint() && stack.hasFoil();
    }
}
