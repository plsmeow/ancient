package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import lombok.Getter;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import tech.onetap.Onetap;
import tech.onetap.event.EventGameUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.text.ValueUnit;

@ModuleInformation(moduleName = "AimAssist", moduleDesc = "Плавная легитная доводка прицела до цели", moduleCategory = ModuleCategory.COMBAT)
public class AimAssist extends Module {

    public final SliderSetting distance = new SliderSetting(
            "Дистанция", ValueUnit.countable("блок", "блока", "блоков"), 4.0f, 2.0f, 6.0f, 0.1f
    );
    public final SliderSetting fov = new SliderSetting(
            "FOV", ValueUnit.abbreviation("°"), 60.0f, 10.0f, 180.0f, 1.0f
    );
    public final SliderSetting horizontalSpeed = new SliderSetting(
            "Горизонтальная скорость", 4.0f, 0.5f, 20.0f, 0.5f
    );
    public final SliderSetting verticalSpeed = new SliderSetting(
            "Вертикальная скорость", 2.0f, 0.0f, 20.0f, 0.5f
    );
    public final SliderSetting smooth = new SliderSetting(
            "Плавность", 4.0f, 1.0f, 20.0f, 0.5f
    );
    public final ModeSetting aimPart = new ModeSetting(
            "Часть тела", "Ближайшая", "Ближайшая", "Голова", "Тело", "Ноги"
    );
    private final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Голые", true),
            new BooleanSetting("Монстры", true),
            new BooleanSetting("Животные", false)
    );
    public final BooleanSetting onlyClick = new BooleanSetting("Только при нажатии ЛКМ", true);
    public final BooleanSetting weaponOnly = new BooleanSetting("Только с оружием", true);
    public final BooleanSetting stopOnTarget = new BooleanSetting("Остановка на цели", true);
    public final BooleanSetting noWallHit = new BooleanSetting("Не наводить через стены", true);
    public final BooleanSetting randomization = new BooleanSetting("Рандомизация", true);
    public final BooleanSetting pauseEating = new BooleanSetting("Не наводить при еде", true);

    @Getter
    private LivingEntity target;

    private long lastUpdateTime = 0;
    private float yawRemainder = 0.0f;
    private float pitchRemainder = 0.0f;

    @Override
    public void onDisable() {
        target = null;
        lastUpdateTime = 0;
        yawRemainder = 0.0f;
        pitchRemainder = 0.0f;
        super.onDisable();
    }

    @EventHandler
    private void onGameUpdate(EventGameUpdate e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        // Ограничиваем вызовы до одного раза за кадр (защита от пачки вызовов EventGameUpdate в 240 Гц)
        long now = System.nanoTime();
        if (lastUpdateTime != 0 && (now - lastUpdateTime) < 1_000_000L) {
            return;
        }
        float dt = lastUpdateTime == 0 ? 0.016f : (float) ((now - lastUpdateTime) / 1_000_000_000.0);
        lastUpdateTime = now;
        dt = MathHelper.clamp(dt, 0.001f, 0.05f);

        // При активной KillAura отключаем доводку
        KillAura killAura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            target = null;
            yawRemainder = 0.0f;
            pitchRemainder = 0.0f;
            return;
        }

        if (pauseEating.getValue() && mc.player.isUsingItem()) {
            target = null;
            yawRemainder = 0.0f;
            pitchRemainder = 0.0f;
            return;
        }

        if (weaponOnly.getValue() && !isHoldingWeapon()) {
            target = null;
            yawRemainder = 0.0f;
            pitchRemainder = 0.0f;
            return;
        }

        if (onlyClick.getValue()) {
            boolean attackPressed = mc.options.attackKey.isPressed()
                    || GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (!attackPressed) {
                target = null;
                yawRemainder = 0.0f;
                pitchRemainder = 0.0f;
                return;
            }
        }

        Vec3d eyePos = mc.player.getEyePos();
        target = findTarget(eyePos);
        if (target == null) {
            yawRemainder = 0.0f;
            pitchRemainder = 0.0f;
            return;
        }

        // Если прицел уже внутри хитбокса цели — плавно останавливаем доводку для избежания дрожания
        if (stopOnTarget.getValue()) {
            if (mc.targetedEntity == target
                    || RaytraceUtil.rayTrace(mc.player.getRotationVector(), distance.getValue(), target.getBoundingBox())) {
                yawRemainder *= 0.5f;
                pitchRemainder *= 0.5f;
                return;
            }
        }

        Vec3d aimPoint = getAimPoint(target, eyePos);
        if (noWallHit.getValue() && !isPointVisible(eyePos, aimPoint) && !BestPoint.hasVisiblePoint(target, distance.getValue())) {
            target = null;
            yawRemainder = 0.0f;
            pitchRemainder = 0.0f;
            return;
        }

        float[] targetAngles = RotationUtil.calculateAngle(eyePos, aimPoint);

        float yawDelta = MathHelper.wrapDegrees(targetAngles[0] - mc.player.getYaw());
        float pitchDelta = MathHelper.wrapDegrees(targetAngles[1] - mc.player.getPitch());

        float smoothVal = (float) Math.max(1.0, smooth.getValue());
        float hSpeed = (float) horizontalSpeed.getValue();
        float vSpeed = (float) verticalSpeed.getValue();

        // Человекоподобная плавная вариация скорости (без высокочастотного белого шума)
        if (randomization.getValue()) {
            long time = System.currentTimeMillis();
            double timeSec = time * 0.0015;
            float randFactor = 0.90f + 0.20f * (float) (0.5 + 0.5 * Math.sin(timeSec * 3.1 + target.getId()));
            hSpeed *= randFactor;
            vSpeed *= randFactor;
        }

        // Плавная скорость доводки: плавность мягко демпфирует поворот к цели
        // При smooth = 20 поворот очень мягкий и легитный, без резких рывков
        float turnRateYaw = yawDelta * (25.0f / (smoothVal * 1.5f + 5.0f));
        float turnRatePitch = pitchDelta * (25.0f / (smoothVal * 1.5f + 5.0f));

        // Ограничиваем максимальную угловую скорость в градусах/сек (адекватно даже на половине слайдера)
        float maxTurnRateYaw = hSpeed * 12.0f;
        float maxTurnRatePitch = vSpeed * 12.0f;

        turnRateYaw = MathHelper.clamp(turnRateYaw, -maxTurnRateYaw, maxTurnRateYaw);
        turnRatePitch = MathHelper.clamp(turnRatePitch, -maxTurnRatePitch, maxTurnRatePitch);

        float stepYaw = turnRateYaw * dt;
        float stepPitch = turnRatePitch * dt;

        if (Math.abs(stepYaw) > Math.abs(yawDelta)) {
            stepYaw = yawDelta;
        }
        if (Math.abs(stepPitch) > Math.abs(pitchDelta)) {
            stepPitch = pitchDelta;
        }

        // Квантование углов под чувствительность мыши через GCD с накоплением остатка
        float gcd = GCDFixer.getGCDValue();
        if (gcd > 0.0001f) {
            yawRemainder += stepYaw;
            pitchRemainder += stepPitch;

            int yawCounts = Math.round(yawRemainder / gcd);
            int pitchCounts = Math.round(pitchRemainder / gcd);

            float fixedYaw = yawCounts * gcd;
            float fixedPitch = pitchCounts * gcd;

            yawRemainder -= fixedYaw;
            pitchRemainder -= fixedPitch;

            if (yawCounts != 0) {
                mc.player.setYaw(mc.player.getYaw() + fixedYaw);
                mc.player.prevYaw += fixedYaw;
            }
            if (pitchCounts != 0 && vSpeed > 0.0f) {
                float oldPitch = mc.player.getPitch();
                float newPitch = MathHelper.clamp(oldPitch + fixedPitch, -90.0f, 90.0f);
                float actualPitchDelta = newPitch - oldPitch;
                mc.player.setPitch(newPitch);
                mc.player.prevPitch += actualPitchDelta;
            }
        } else {
            mc.player.setYaw(mc.player.getYaw() + stepYaw);
            mc.player.prevYaw += stepYaw;
            if (vSpeed > 0.0f) {
                float oldPitch = mc.player.getPitch();
                float newPitch = MathHelper.clamp(oldPitch + stepPitch, -90.0f, 90.0f);
                float actualPitchDelta = newPitch - oldPitch;
                mc.player.setPitch(newPitch);
                mc.player.prevPitch += actualPitchDelta;
            }
        }
    }

    private LivingEntity findTarget(Vec3d eyePos) {
        if (mc.player == null || mc.world == null) return null;

        double reach = distance.getValue();
        double maxFov = fov.getValue();

        // Предпочтение текущей цели для предотвращения перескоков и дрожания
        if (target != null && isValidEntity(target)) {
            Vec3d point = getAimPoint(target, eyePos);
            double dist = eyePos.distanceTo(point);
            if (dist <= reach && (!noWallHit.getValue() || isPointVisible(eyePos, point) || BestPoint.hasVisiblePoint(target, reach))) {
                float[] angles = RotationUtil.calculateAngle(eyePos, point);
                float yawDiff = Math.abs(MathHelper.wrapDegrees(angles[0] - mc.player.getYaw()));
                float pitchDiff = Math.abs(MathHelper.wrapDegrees(angles[1] - mc.player.getPitch()));
                double fovDist = Math.hypot(yawDiff, pitchDiff);
                if (fovDist <= maxFov / 2.0) {
                    return target;
                }
            }
        }

        LivingEntity best = null;
        double bestFov = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidEntity(living)) continue;

            Vec3d point = getAimPoint(living, eyePos);
            double dist = eyePos.distanceTo(point);
            if (dist > reach) continue;

            if (noWallHit.getValue() && !isPointVisible(eyePos, point) && !BestPoint.hasVisiblePoint(living, reach)) continue;

            float[] angles = RotationUtil.calculateAngle(eyePos, point);
            float yawDiff = Math.abs(MathHelper.wrapDegrees(angles[0] - mc.player.getYaw()));
            float pitchDiff = Math.abs(MathHelper.wrapDegrees(angles[1] - mc.player.getPitch()));
            double fovDist = Math.hypot(yawDiff, pitchDiff);

            if (fovDist <= maxFov / 2.0 && fovDist < bestFov) {
                bestFov = fovDist;
                best = living;
            }
        }

        return best;
    }

    private boolean isPointVisible(Vec3d eyePos, Vec3d point) {
        if (mc.player == null || mc.world == null) return false;
        var blockHit = RaytraceUtil.raycast(eyePos, point, RaycastContext.ShapeType.COLLIDER, mc.player);
        return blockHit == null || blockHit.getType() == HitResult.Type.MISS
                || eyePos.squaredDistanceTo(blockHit.getPos()) >= eyePos.squaredDistanceTo(point) - 1e-4;
    }

    private Vec3d getAimPoint(LivingEntity entity, Vec3d eyePos) {
        Vec3d base;
        Box box = entity.getBoundingBox();
        switch (aimPart.getValue()) {
            case "Голова" -> base = new Vec3d(entity.getX(), entity.getEyeY() - 0.05, entity.getZ());
            case "Ноги" -> base = new Vec3d(entity.getX(), entity.getY() + 0.3, entity.getZ());
            case "Тело" -> base = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() * 0.55, entity.getZ());
            default -> {
                // Плавная непрерывная проекция на хитбокс без дискретных скачков сетки
                double clampedX = MathHelper.clamp(eyePos.x, box.minX + 0.08, box.maxX - 0.08);
                double clampedY = MathHelper.clamp(eyePos.y, box.minY + 0.15, box.maxY - 0.15);
                double clampedZ = MathHelper.clamp(eyePos.z, box.minZ + 0.08, box.maxZ - 0.08);
                base = new Vec3d(clampedX, clampedY, clampedZ);
            }
        }

        if (randomization.getValue()) {
            long time = System.currentTimeMillis();
            double timeSec = time * 0.0015;
            double jitterX = Math.sin(timeSec * 2.3 + entity.getId()) * 0.03;
            double jitterY = Math.cos(timeSec * 1.7 + entity.getId()) * 0.02;
            double jitterZ = Math.sin(timeSec * 2.7 + entity.getId()) * 0.03;
            base = base.add(jitterX, jitterY, jitterZ);
        }

        return base;
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.isOf(Items.MACE)
                || stack.getName().getString().contains("1.21 Mace");
    }

    private boolean isValidEntity(Entity entity) {
        if (!entity.isAlive()) return false;
        if (entity instanceof ClientPlayerEntity) return false;
        if (entity instanceof ArmorStandEntity) return false;

        PlayerEntity player = mc.player;
        if (player == null) return false;

        if (entity instanceof PlayerEntity p) {
            if (p.getArmor() != 0 && !targets.isEnabled("Игроки")) return false;
            if (p.getArmor() == 0 && !targets.isEnabled("Голые")) return false;
            if (Onetap.getInstance().getModuleStorage().get(AntiBot.class).isBot(p)) return false;
            if (!FriendRepository.shouldAttack(p)) return false;
        } else if (entity instanceof HostileEntity || entity instanceof AmbientEntity) {
            if (!targets.isEnabled("Монстры")) return false;
        } else if (entity instanceof PassiveEntity || entity instanceof FishEntity) {
            if (!targets.isEnabled("Животные")) return false;
        } else {
            return false;
        }

        return true;
    }
}
