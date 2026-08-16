package tech.onetap.util.rotation;

import meteordevelopment.orbit.EventHandler;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.LookEvent;
import tech.onetap.event.list.RotationEvent;
import tech.onetap.module.list.render.FreeLook;
import tech.onetap.util.base.Instance;

public class FreeLookComponent extends Component {

    @Getter
    @Setter
    private static boolean active;
    @Getter
    @Setter
    private static float freeYaw, freePitch;

    @EventHandler
    public void onEvent(LookEvent event) {
        if (active) {
            rotateTowards(event.getYaw(), event.getPitch());
            event.cancelEvent();
        }
    }

    @EventHandler
    public void onEvent(RotationEvent event) {
        if (active) {
            event.setYaw(freeYaw);
            event.setPitch(freePitch);
        } else {
            freeYaw = event.getYaw();
            freePitch = event.getPitch();
        }
    }

    private void rotateTowards(double targetYaw, double targetPitch) {
        freePitch = MathHelper.clamp((float) (freePitch + targetPitch * 0.15D), -90.0F, 90.0F);
        freeYaw = (float) (freeYaw + targetYaw * 0.15D);
    }

    /**
     * FreeLook активен для взаимодействий: тихая ротация (silent aim) или модуль Third Person.
     * Тогда прицел и использование предметов идут по взгляду камеры, а не по ротации тела.
     */
    public static boolean interactionActive() {
        FreeLook thirdPerson = Instance.get(FreeLook.class);
        return (thirdPerson != null && thirdPerson.isActive()) || active;
    }

    public static float interactionYaw() {
        FreeLook thirdPerson = Instance.get(FreeLook.class);
        return (thirdPerson != null && thirdPerson.isActive()) ? thirdPerson.getCameraYaw() : freeYaw;
    }

    public static float interactionPitch() {
        FreeLook thirdPerson = Instance.get(FreeLook.class);
        return (thirdPerson != null && thirdPerson.isActive()) ? thirdPerson.getCameraPitch() : freePitch;
    }

    /**
     * Направление взгляда FreeLook в мировых координатах.
     * Возвращает null, когда FreeLook неактивен, — тогда взаимодействия остаются на ротации тела.
     */
    public static Vec3d interactionDirection() {
        if (!interactionActive()) return null;

        float yaw = interactionYaw();
        float pitch = interactionPitch();

        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }
}