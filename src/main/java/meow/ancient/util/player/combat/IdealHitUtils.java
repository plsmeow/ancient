package meow.ancient.util.player.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import meow.ancient.Ancient;
import meow.ancient.event.list.EventAttack;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.module.list.combat.MaceKill;
import meow.ancient.module.list.movement.AirStuck;
import meow.ancient.module.list.movement.Flight;
import meow.ancient.util.IMinecraft;
import meow.ancient.util.math.StopWatch;
import meow.ancient.util.player.other.WorldUtils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class IdealHitUtils implements IMinecraft {

    public IdealHitUtils() {
        Ancient.getInstance().getEventBus().subscribe(this);
        //RuntimeException runtimeException = new RuntimeException();
        //runtimeException.setStackTrace(new StackTraceElement[0]);
        //String token = System.getProperty("ancient.token");
        //if (!verifyToken(token)) throw runtimeException;
    }

    private boolean verifyToken(String token) {
        try {
            URL url = new URL("");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            String json = "{\"token\":\"" + token + "\"}";
            con.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            return con.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private final StopWatch attackCooldown = new StopWatch();

    @EventHandler
    private void onAttack(EventAttack e) {
    }

    public boolean cooldownIsReached(boolean toSprinting) {
        float cooldown = 0;

        if (mc.player.getMainHandStack().isEmpty()) cooldown = 0.9f;
        if (mc.player.getMainHandStack().getItem() instanceof SwordItem) cooldown = 0.52f;
        if (toSprinting) cooldown -= 0.15f;
        //if (mc.player.getMainHandStack().getItem() instanceof AxeItem) cooldown = 400;
        //if (mc.player.getMainHandStack().getItem() instanceof ShovelItem) cooldown = 450;
        //if (mc.player.getMainHandStack().getItem() instanceof PickaxeItem) cooldown = 400;
        return mc.gameRenderer.firstPersonRenderer.equipProgressMainHand > cooldown;
    }

    public boolean canAIFall() {
        return ((getBlock(0, 3, 0) == Blocks.AIR && getBlock(0, 2, 0) == Blocks.AIR && getBlock(0, 1, 0) == Blocks.AIR)
                || Ancient.getInstance().getServerManager().getFallDistance() < (getBlock(0, 2, 0) != Blocks.AIR ? 0.08f : 0.6f)
                || Ancient.getInstance().getServerManager().getFallDistance() > 1.2f);
    }

    public boolean canCritical() {
        double effectiveJumpHeight = mc.player.getStepHeight();
        Vec3d jumpVec = new Vec3d(0, effectiveJumpHeight, 0);
        Vec3d allowedMovement = mc.player.adjustMovementForCollisions(jumpVec);

        boolean groundSpoofActive = Ancient.getInstance().getModuleStorage().get(meow.ancient.module.list.movement.GroundSpoof.class).isEnabled()
                && Ancient.getInstance().getModuleStorage().get(meow.ancient.module.list.movement.GroundSpoof.class).mode.is("False");
        if (groundSpoofActive) return true;

        boolean onlySpaceActive = Ancient.getInstance().getModuleStorage().get(KillAura.class).isEnabled()
                ? Ancient.getInstance().getModuleStorage().get(KillAura.class).onlySpace.getValue()
                : (Ancient.getInstance().getModuleStorage().get(meow.ancient.module.list.combat.TriggerBot.class).isEnabled()
                        && Ancient.getInstance().getModuleStorage().get(meow.ancient.module.list.combat.TriggerBot.class).onlySpace.getValue());

        boolean notCrit = mc.player.isInLava()
                || mc.player.isClimbing()
                || mc.player.isSubmergedIn(FluidTags.WATER)
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || WorldUtils.isInWeb()
                || mc.player.isGliding()
                || mc.player.hasVehicle()
                || mc.player.getAbilities().flying
                || (allowedMovement.y < mc.player.getStepHeight() - 0.5 && mc.player.isOnGround() || mc.player.getVelocity().y == -0.005 && mc.player.isSubmergedInWater())
                // С включённым AirStuck игрок завис в воздухе без скорости падения — крит невозможен,
                // поэтому бьём сразу, а не ждём подтверждённого падения (иначе KillAura не атакует вообще).
                || Ancient.getInstance().getModuleStorage().get(AirStuck.class).isEnabled()
                || Ancient.getInstance().getModuleStorage().get(Flight.class).isEnabled()
                || Ancient.getInstance().getModuleStorage().get(MaceKill.class).isEnabled()
                || mc.player.isOnGround() && !mc.options.jumpKey.isPressed() && !onlySpaceActive;

        // Крит засчитывается сервером по ПОСЛЕДНЕМУ отправленному пакету движения (конец прошлого тика),
        // а удар из EventTick уходит ДО sendMovementPackets текущего тика. Поэтому одного `fallDistance > 0`
        // мало: на пограничном кадре (апекс / только оторвался от земли) сервер ещё видит игрока на земле
        // и крит срывается в простой удар — особенно при зажатом пробеле, где прыжки фазятся с кулдауном.
        // Бьём только когда падение реально подтверждено: в воздухе и снижаемся (velocity.y < 0).
        boolean serverConfirmedFall = !mc.player.isOnGround()
                && mc.player.fallDistance > 0f
                && mc.player.getVelocity().y < 0.0;
        return (notCrit || serverConfirmedFall);
    }

    public Block getBlock(double x, double y, double z) {
        return mc.world.getBlockState(mc.player.getBlockPos().add((int) x, (int) y, (int) z)).getBlock();
    }

    public boolean findFall(float fallDistance) {
        Vec3d rotationVec = mc.player.getRotationVector();
        double tempVelocityX = mc.player.getVelocity().x;
        double tempVelocityY = mc.player.getVelocity().y;
        double tempVelocityZ = mc.player.getVelocity().z;

        float n = MathHelper.cos(mc.player.getPitch() * 0.017453292f);
        n = (float) (n * n * Math.min(rotationVec.length() / 0.4, 1.0));

        Vec3d vec3d = new Vec3d(tempVelocityX, tempVelocityY, tempVelocityZ).add(0.0, 0.08 * (-1.0 + n * 0.75), 0.0);
        tempVelocityY = vec3d.y * 0.9800000190734863;

        return tempVelocityY < fallDistance;
    }
}