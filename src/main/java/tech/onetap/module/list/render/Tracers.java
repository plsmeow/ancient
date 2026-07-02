package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.AntiBot;
import tech.onetap.module.list.player.FreeCamera;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.render.lines.VertexUtil;
import tech.onetap.util.render.providers.ColorProvider;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "Tracers", moduleCategory = ModuleCategory.RENDER)
public class Tracers extends Module {
    private final BooleanSetting onlyWhenNotVisible = new BooleanSetting("Не в поле зрения", false);
    private final BooleanSetting onlyNetherite = new BooleanSetting("Незеритовая броня", false);
    private final BooleanSetting pinkElytra = new BooleanSetting("Розовый цвет Элитр", false);

    @Subscribe
    public void onWorldRender(EventWorldRender event) {
        MatrixStack stack = event.getMatrixStack();
        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        Vector3f lookVec = mc.gameRenderer.getCamera().getHorizontalPlane();
        Vec3d eyePos = cameraPos.add(new Vec3d(lookVec).multiply(3));

        stack.push();
        RenderSystem.enableBlend();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(1f);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

        boolean need = false;

        for (LivingEntity entity : collectTargets()) {
            if (onlyWhenNotVisible.getValue() && mc.worldRenderer.frustum.isVisible(entity.getBoundingBox())) continue;
            if (entity == mc.player) continue;

            double tx = entity.prevX + (entity.getX() - entity.prevX) * tickDelta;
            double ty = entity.prevY + (entity.getY() - entity.prevY) * tickDelta;
            double tz = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta;
            Vec3d targetPos = new Vec3d(tx, ty, tz);

            Color color = new Color(
                    FriendRepository.isFriend(entity.getNameForScoreboard())
                            ? ColorProvider.rgba(0, 255, 0, 255)
                            : (pinkElytra.getValue() && isWearingElytra(entity) 
                                ? ColorProvider.rgba(255, 105, 180, 255)
                                : -1)
            );

            VertexUtil.vertexLine(stack, buffer,
                    (float) (eyePos.x - cameraPos.x),
                    (float) (eyePos.y - cameraPos.y),
                    (float) (eyePos.z - cameraPos.z),

                    (float) (targetPos.x - cameraPos.x),
                    (float) (targetPos.y - cameraPos.y),
                    (float) (targetPos.z - cameraPos.z),
                    color
            );

            need = true;
        }

        if (need) BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        RenderSystem.disableBlend();
        stack.pop();
    }

    private java.util.List<LivingEntity> collectTargets() {
        List<LivingEntity> list = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;

            if (!(living instanceof PlayerEntity)) continue;

            PlayerEntity playerEntity = (PlayerEntity) living;
            if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getPlayerListEntry(playerEntity.getUuid()) == null)
                continue;

            if (FriendRepository.isFriend(living.getNameForScoreboard())) {
                if (onlyNetherite.getValue() && !isWearingAnyNetherite(playerEntity)) continue;
                list.add(living);
                continue;
            }

            if (!isValid(living)) continue;
            if (onlyNetherite.getValue() && !isWearingAnyNetherite(playerEntity)) continue;
            list.add(living);
        }
        return list;
    }

    private boolean isWearingAnyNetherite(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getEquippedStack(slot);
            if (isNetheriteArmor(stack)) return true;
        }
        return false;
    }

    private boolean isWearingElytra(LivingEntity entity) {
        ItemStack chestStack = entity.getEquippedStack(EquipmentSlot.CHEST);
        return chestStack.isOf(Items.ELYTRA);
    }

    private boolean isNetheriteArmor(ItemStack stack) {
        return stack.isOf(Items.NETHERITE_HELMET)
                || stack.isOf(Items.NETHERITE_CHESTPLATE)
                || stack.isOf(Items.NETHERITE_LEGGINGS)
                || stack.isOf(Items.NETHERITE_BOOTS);
    }

    private boolean isValid(Entity entity) {
        if (!entity.isAlive()) return false;
        if (entity == Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer) return false;
        if (entity instanceof ClientPlayerEntity) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if ((entity instanceof HostileEntity || entity instanceof AmbientEntity))
            return false;
        if ((entity instanceof PassiveEntity || entity instanceof FishEntity))
            return false;
        if (entity instanceof PlayerEntity p) {
            if (Onetap.getInstance().getModuleStorage().get(AntiBot.class).isBot(p)) return false;
            if (!FriendRepository.shouldAttack(p)) return false;
        }
        return true;
    }
}