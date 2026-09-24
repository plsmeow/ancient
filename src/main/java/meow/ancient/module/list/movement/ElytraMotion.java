package meow.ancient.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import meow.ancient.event.list.EventPlayerUpdate;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.util.base.Instance;
import meow.ancient.util.player.combat.PredictUtils;

@ModuleInformation(moduleName = "Elytra Motion", moduleDesc = "Стопит перед целью на элитре", moduleCategory = ModuleCategory.MOVEMENT)
public class ElytraMotion extends Module {
    private boolean waitTarget;

    @EventHandler
    private void onPlayerTick(EventPlayerUpdate e) {
        if (mc.player == null) return;

        LivingEntity target = Instance.get(KillAura.class).getTarget();

        if (target == null) {
            if (!waitTarget) {
                mc.player.setNoGravity(false);
                waitTarget = true;
            }
            return;
        } else waitTarget = false;

        double dist = mc.player.getEyePos().distanceTo(target.getBoundingBox().getCenter());
        boolean shouldChase = target.isGliding() && target.getVelocity().length() * 20 >= 13;


        float pon;
        if(target.isGliding()) pon = 1.5f;
        else{
            pon = 3f;
        }
        if (mc.player.isGliding() && dist < pon && !shouldChase) {
            mc.player.setVelocity(0,0,0);
            mc.player.setNoGravity(true);
        } else {
            mc.player.setNoGravity(false);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player == null) return;
        waitTarget = false;
        mc.player.setNoGravity(false);
    }
}