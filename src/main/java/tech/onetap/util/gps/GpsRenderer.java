package tech.onetap.util.gps;

import com.google.common.eventbus.Subscribe;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventHUD;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.render.renderers.impl.BuiltTexture;

public final class GpsRenderer implements IMinecraft {
    private static final GpsRenderer INSTANCE = new GpsRenderer();
    public static GpsRenderer get() { return INSTANCE; }

    private double targetX, targetZ;
    @Setter private boolean enabled;

    private GpsRenderer() { Onetap.getInstance().getEventBus().register(this); }

    public void setTarget(double x, double z) { this.targetX = x; this.targetZ = z; }

    @Subscribe
    private void onHud(EventHUD e) {
        if (!enabled) return;
        if (MinecraftClient.getInstance().player == null) return;

        MatrixStack ms = e.getDrawContext().getMatrices();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        Vec3d pos = mc.player.getPos();
        double dx = targetX - pos.x; double dz = targetZ - pos.z;
        int dist = (int) Math.sqrt(dx*dx + dz*dz);

        float angle = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float rot = MathHelper.wrapDegrees(angle - mc.player.getYaw());

        String distTxt = dist + "m";
        int white = ColorProvider.rgba(255,255,255,255);
        double cx = sw / 2.0, cy = sh / 2.0 - 80;

        ms.push();
        ms.translate(cx, cy, 0);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot));
        AbstractTexture tex = mc.getTextureManager().getTexture(Identifier.of("mre", "images/triangle.png"));
        int id = tex.getGlId();
        int theme = ColorProvider.getThemeColor();

        BuiltTexture arrow2 = Builder.texture()
                .size(new SizeState(18, 18))
                .radius(QuadRadiusState.NO_ROUND)
                .color(new QuadColorState(theme))
                .texture(0, 0, 1, 1, id)
                .smoothness(2)
                .build();
        arrow2.render(ms.peek().getPositionMatrix(), -9, -9);

        BuiltTexture arrow = Builder.texture()
                .size(new SizeState(18, 18))
                .radius(QuadRadiusState.NO_ROUND)
                .color(new QuadColorState(theme))
                .texture(0, 0, 1, 1, id)
                .smoothness(1f)
                .build();
        arrow.render(ms.peek().getPositionMatrix(), -9, -9);
        ms.pop();

        float w1 = Fonts.SFMEDIUM.get().getWidth(distTxt,7);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), distTxt, (float)(cx-w1/2), (float)(cy+8), white,7);
    }
} 