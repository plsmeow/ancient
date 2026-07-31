package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import tech.onetap.event.list.EventHUD;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.impl.BuiltTexture;

@ModuleInformation(moduleName = "Arrows", moduleDesc = "Стрелки вокруг курсора, указывающие на игроков", moduleCategory = ModuleCategory.RENDER)
public class Arrows extends Module {
    private final SliderSetting radius = new SliderSetting("Дистанция", 45, 10, 150, 1);
    private final SliderSetting size = new SliderSetting("Размер", 12, 4, 40, 1);
    private final BooleanSetting onlyNotVisible = new BooleanSetting("Не в поле зрения", false);

    @Subscribe
    public void onHud(EventHUD event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden) return;

        DrawContext context = event.getDrawContext();
        MatrixStack ms = context.getMatrices();
        float tickDelta = event.getRenderTickCounter().getTickDelta(true);

        float centerX = mc.getWindow().getScaledWidth() / 2f;
        float centerY = mc.getWindow().getScaledHeight() / 2f;

        ClientPlayerEntity self = mc.player;
        double selfX = MathHelper.lerp(tickDelta, self.prevX, self.getX());
        double selfZ = MathHelper.lerp(tickDelta, self.prevZ, self.getZ());
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, self.prevYaw, self.getYaw());

        AbstractTexture texture = mc.getTextureManager().getTexture(Identifier.of("mre", "images/triangle.png"));
        int textureId = texture.getGlId();

        float arrowSize = size.getFloatValue();
        float arrowRadius = radius.getFloatValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == self || !player.isAlive()) continue;
            if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) == null) continue;
            if (onlyNotVisible.getValue() && mc.worldRenderer.frustum.isVisible(player.getBoundingBox())) continue;

            double targetX = MathHelper.lerp(tickDelta, player.prevX, player.getX());
            double targetZ = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());

            double dx = targetX - selfX;
            double dz = targetZ - selfZ;

            float angle = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
            float rotation = MathHelper.wrapDegrees(angle - yaw);

            int color = FriendRepository.isFriend(player.getNameForScoreboard())
                    ? ColorProvider.rgba(0, 255, 0, 255)
                    : ColorProvider.getThemeColor();

            ms.push();
            ms.translate(centerX, centerY, 0);
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
            ms.translate(0, -arrowRadius, 0);

            BuiltTexture arrow = Builder.texture()
                    .size(new SizeState(arrowSize, arrowSize))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .texture(0, 0, 1, 1, textureId)
                    .smoothness(1f)
                    .build();
            arrow.render(ms.peek().getPositionMatrix(), -arrowSize / 2f, -arrowSize / 2f);
            ms.pop();
        }
    }
}
