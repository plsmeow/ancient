package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.onetap.Onetap;
import tech.onetap.event.list.*;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.list.combat.TpAura;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.player.combat.HvhTargetPredict;
import tech.onetap.util.player.move.MoveUtil;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.text.ValueUnit;

@ModuleInformation(moduleName = "Speed", moduleCategory = ModuleCategory.MOVEMENT)
public class Speed extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Contact", "Contact", "Vulcan", "Vanilla");

    private final SliderSetting boost = new SliderSetting("Сила буста", 8.0f, 1.0f, 20.0f, 0.1f).setVisible(() -> mode.is("Contact"));
    private final SliderSetting targetRange = new SliderSetting("Радиус цели", 3.0f, 0.5f, 10.0f, 0.1f).setVisible(() -> mode.is("Contact"));
    private final SliderSetting contactRange = new SliderSetting("Радиус контакта", 0.5f, 0.1f, 2.0f, 0.1f).setVisible(() -> mode.is("Contact"));

    private final BooleanSetting playersOnly = new BooleanSetting("Только игроки", true).setVisible(() -> mode.is("Contact"));
    private final BooleanSetting contactOnlyWhileMoving = new BooleanSetting("Только в движении", true).setVisible(() -> mode.is("Contact"));
    private final BooleanSetting onlyWithAura = new BooleanSetting("Только с Aura", false).setVisible(() -> mode.is("Contact"));

    private final BooleanSetting predict = new BooleanSetting("Предикт", true).setVisible(() -> mode.is("Contact"));
    private final SliderSetting predictStrength = new SliderSetting("Сила предикта", 2.0f, 0.1f, 10.0f, 0.1f).setVisible(() -> mode.is("Contact") && predict.getValue());

    private final BooleanSetting vulcanOnlyWhileMoving = new BooleanSetting("Только в движении", true).setVisible(() -> mode.is("Vulcan"));

    private final SliderSetting vanillaSpeed = new SliderSetting("Скорость", 1.18f, 1.05f, 20.0f, 0.5f).setVisible(() -> mode.is("Vanilla"));

    // HvH Target — Vanilla: автоматически идём к цели KillAura с предиктом по X/Z
    private final BooleanSetting hvhTarget = new BooleanSetting("HvH Target", false).setVisible(() -> mode.is("Vanilla"));
    private final SliderSetting hvhTargetRange = new SliderSetting("Радиус цели", ValueUnit.countable("блок", "блока", "блоков"), 50, 1, 50, 0.5f)
            .setVisible(() -> mode.is("Vanilla") && hvhTarget.getValue());
    // Сила предикта = количество тиков вперёд для HvhTargetPredict
    private final SliderSetting hvhPredictStrength = new SliderSetting("Сила предикта", 4.0f, 0.5f, 20.0f, 0.1f)
            .setVisible(() -> mode.is("Vanilla") && hvhTarget.getValue());
    private final BooleanSetting hvhRender = new BooleanSetting("Рендер предикта", true)
            .setVisible(() -> mode.is("Vanilla") && hvhTarget.getValue());

    // Leave — Vanilla: отход на дистанцию пока удар не готов, иначе сближение к радиусу атаки
    private final BooleanSetting leave = new BooleanSetting("Leave", false).setVisible(() -> mode.is("Vanilla") && hvhTarget.getValue());
    private final SliderSetting leaveDistance = new SliderSetting("Дистанция отхода", ValueUnit.countable("блок", "блока", "блоков"), 8, 4, 20, 0.5f)
            .setVisible(() -> mode.is("Vanilla") && hvhTarget.getValue() && leave.getValue());
    private final SliderSetting attackDistance = new SliderSetting("Радиус удара", ValueUnit.countable("блок", "блока", "блоков"), 4, 1, 6, 0.1f)
            .setVisible(() -> mode.is("Vanilla") && hvhTarget.getValue() && leave.getValue());

    private static final double VANILLA_DEFAULT_SPEED = 0.2873;
    // Зона допуска у цели (блоки) — резкая остановка, чтобы избежать дёрганья
    private static final double HVH_TARGET_ZONE = 0.6;

    private LivingEntity lastHvhTarget = null;

    // Данные для рендера предикта (обновляются на тике в handleVanilla)
    private Vec3d renderPredicted = null;
    private Vec3d renderLeaveTarget = null; // null если leave не активен
    private boolean renderIsLeaving = false; // true = отход, false = сближение, null бездействие
    private LivingEntity renderTarget = null;

    public boolean isHvhTargetEnabled() {
        return mode.is("Vanilla") && hvhTarget.getValue();
    }

    @Override
    public void onDisable() {
        if (lastHvhTarget != null) HvhTargetPredict.reset(lastHvhTarget);
        lastHvhTarget = null;
        renderTarget = null;
        renderPredicted = null;
        renderLeaveTarget = null;
        super.onDisable();
    }

    @Subscribe
    private void onTick(EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Vulcan")) {
            handleVulcan();
            return;
        }

        if (mode.is("Vanilla")) {
            handleVanilla();
            return;
        }

        handleContact();
    }

    private void handleVanilla() {
        if (!MoveUtil.hasPlayerMovement()) return;

        double speed = vanillaSpeed.getValue() * VANILLA_DEFAULT_SPEED;

        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            double value = (mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1) * 0.205;
            speed += speed * value;
        }

        // HvH Target: если включён и есть валидная цель KillAura в радиусе — идём к предсказанной точке
        if (hvhTarget.getValue()) {
            KillAura aura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
            if (aura != null && aura.isEnabled() && aura.getTarget() != null) {
                LivingEntity target = aura.getTarget();
                if (lastHvhTarget != null && lastHvhTarget != target) {
                    HvhTargetPredict.reset(lastHvhTarget);
                }
                lastHvhTarget = target;
                Vec3d toTarget = target.getPos().subtract(mc.player.getPos());
                double horizontalDistSq = toTarget.x * toTarget.x + toTarget.z * toTarget.z;
                double range = hvhTargetRange.getValue();
                TpAura tpAura = Onetap.getInstance().getModuleStorage().get(TpAura.class);
                if (tpAura != null && tpAura.isEnabled()) {
                    range += tpAura.getMaxDistance();
                }
                if (horizontalDistSq <= range * range) {
                    Vec3d targetPos = HvhTargetPredict.predict(target, hvhPredictStrength.getValue());

                    // Leave: пока идёт задержка удара (ticksToAttack > 0) — отходим,
                    // иначе сближаемся к радиусу атаки
                    if (leave.getValue() && aura.ticksToAttack > 0) {
                        double desiredDist = leaveDistance.getValue();
                        if (horizontalDistSq < desiredDist * desiredDist) {
                            Vec3d fromTarget = mc.player.getPos().subtract(targetPos);
                            double[] dir = getDirectionToPoint(Vec3d.ZERO, new Vec3d(fromTarget.x, 0.0, fromTarget.z), speed);
                            Vec3d current = mc.player.getVelocity();
                            mc.player.setVelocity(dir[0], current.y, dir[1]);
                            // Рендер: предикт-цель + точка направления (отход)
                            renderTarget = target;
                            renderPredicted = targetPos;
                            // Точка отхода: дистанция desiredDist от targetPos по нормализованному вектору "от цели"
                            double len = Math.hypot(fromTarget.x, fromTarget.z);
                            if (len < 1.0E-6) {
                                renderLeaveTarget = targetPos;
                            } else {
                                double k = desiredDist / len;
                                renderLeaveTarget = new Vec3d(
                                        targetPos.x - fromTarget.x * k,
                                        targetPos.y,
                                        targetPos.z - fromTarget.z * k
                                );
                            }
                            renderIsLeaving = true;
                            return;
                        }
                    } else if (leave.getValue()) {
                        // Сближение к радиусу удара
                        double desiredDist = attackDistance.getValue();
                        if (tpAura != null && tpAura.isEnabled()) {
                            desiredDist += tpAura.getMaxDistance();
                        }
                        if (horizontalDistSq > desiredDist * desiredDist) {
                            double[] dir = getDirectionToPoint(mc.player.getPos(), targetPos, speed);
                            Vec3d current = mc.player.getVelocity();
                            mc.player.setVelocity(dir[0], current.y, dir[1]);
                            // Рендер: предикт-цель + точка сближения (desiredDist от игрока к цели)
                            renderTarget = target;
                            renderPredicted = targetPos;
                            double pdx = targetPos.x - mc.player.getX();
                            double pdz = targetPos.z - mc.player.getZ();
                            double plen = Math.hypot(pdx, pdz);
                            if (plen < 1.0E-6) {
                                renderLeaveTarget = mc.player.getPos();
                            } else {
                                double k = desiredDist / plen;
                                renderLeaveTarget = new Vec3d(
                                        mc.player.getX() + pdx * k,
                                        mc.player.getY(),
                                        mc.player.getZ() + pdz * k
                                );
                            }
                            renderIsLeaving = false;
                            return;
                        }
                        // Внутри зоны удара — резкая остановка, чтобы не перелетать цель
                        Vec3d current = mc.player.getVelocity();
                        mc.player.setVelocity(0.0, current.y, 0.0);
                        renderTarget = target;
                        renderPredicted = targetPos;
                        renderLeaveTarget = null;
                        return;
                    }

                    // Дистанция по горизонтали до точки предикта
                    double dx = targetPos.x - mc.player.getX();
                    double dz = targetPos.z - mc.player.getZ();
                    double distToPoint = Math.sqrt(dx * dx + dz * dz);

                    if (distToPoint <= HVH_TARGET_ZONE) {
                        // Достигли цели/предикта — резкая остановка, без дёрганья
                        Vec3d current = mc.player.getVelocity();
                        mc.player.setVelocity(0.0, current.y, 0.0);
                    } else {
                        // Движемся к точке, но ограничиваем скорость остатком
                        // дистанции, чтобы не перелетать цель на высокой скорости
                        double moveSpeed = Math.min(speed, distToPoint);
                        double[] dir = getDirectionToPoint(mc.player.getPos(), targetPos, moveSpeed);
                        Vec3d current = mc.player.getVelocity();
                        mc.player.setVelocity(dir[0], current.y, dir[1]);
                    }
                    // Рендер: только предикт-цель (без leave)
                    renderTarget = target;
                    renderPredicted = targetPos;
                    renderLeaveTarget = null;
                    return;
                }
            }
        }

        double[] change = transformVanillaStrafe(speed);

        Vec3d current = mc.player.getVelocity();
        mc.player.setVelocity(change[0], current.y, change[1]);
        // Вне HvH Target — рендер не рисуем
        renderTarget = null;
        renderPredicted = null;
        renderLeaveTarget = null;
    }

    private double[] transformVanillaStrafe(double speed) {
        float forward = Math.signum(mc.player.input.movementForward);
        float side = Math.signum(mc.player.input.movementSideways);
        float yaw = mc.player.getYaw();

        if (forward == 0.0f && side == 0.0f) return new double[]{0.0, 0.0};

        float strafe = 90 * side;
        if (forward != 0) strafe *= forward * 0.5f;

        yaw = yaw - strafe;
        if (forward < 0) yaw -= 180;
        double yawRadians = Math.toRadians(yaw);

        return new double[]{-Math.sin(yawRadians) * speed, Math.cos(yawRadians) * speed};
    }

    private void handleVulcan() {
        if (vulcanOnlyWhileMoving.getValue() && !MoveUtil.hasPlayerMovement()) return;
        if (!mc.player.isOnGround() || mc.player.horizontalCollision) return;
        if (mc.options.jumpKey.isPressed()) return;

        mc.player.jump();
        mc.player.setVelocity(mc.player.getVelocity().x, 0.1, mc.player.getVelocity().z);
        MoveUtil.setMotion(0.40f);
    }

    private void handleContact() {

        KillAura aura = null;
        if (onlyWithAura.getValue()) {
            aura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
            if (aura == null || !aura.isEnabled() || aura.getTarget() == null) return;
        }

        Box contactBox = mc.player.getBoundingBox().expand(contactRange.getValue());
        int contactCount = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;
            if (contactBox.intersects(entity.getBoundingBox())) contactCount++;
        }

        if (contactCount <= 0) return;
        if (contactOnlyWhileMoving.getValue() && !MoveUtil.hasPlayerMovement()) return;

        double motionBoost = boost.getValue() * 0.01 * contactCount;
        if (motionBoost <= 0.0) return;

        Entity nearest = (onlyWithAura.getValue() && aura != null) ? aura.getTarget() : findNearestTarget(targetRange.getValue());
        if (nearest == null) return;

        Vec3d targetPos = nearest.getPos();
        if (predict.getValue()) {
            Vec3d targetMotion = nearest.getVelocity();
            double horizontalMotionSq = targetMotion.x * targetMotion.x + targetMotion.z * targetMotion.z;
            if (horizontalMotionSq > 1.0E-4) {
                targetPos = targetPos.add(targetMotion.x * predictStrength.getValue(), 0.0, targetMotion.z * predictStrength.getValue());
            }
        }

        double[] direction = getDirectionToPoint(mc.player.getPos(), targetPos, motionBoost);
        mc.player.addVelocity(direction[0], 0.0, direction[1]);
    }

    private Entity findNearestTarget(double maxRange) {
        Entity nearest = null;
        double bestDistanceSq = Double.MAX_VALUE;
        double maxDistanceSq = maxRange * maxRange;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;

            double dx = entity.getX() - mc.player.getX();
            double dz = entity.getZ() - mc.player.getZ();
            double distanceSq = dx * dx + dz * dz;

            if (distanceSq <= maxDistanceSq && distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                nearest = entity;
            }
        }

        return nearest;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player || !entity.isAlive()) return false;
        if (playersOnly.getValue() && !(entity instanceof PlayerEntity)) return false;
        return entity instanceof LivingEntity || entity instanceof BoatEntity;
    }

    private double[] getDirectionToPoint(Vec3d from, Vec3d to, double speedValue) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-6) return new double[]{0.0, 0.0};
        return new double[]{dx / length * speedValue, dz / length * speedValue};
    }

    private boolean renderRegistered = false;

    @Override
    public void onEnable() {
        super.onEnable();
        if (!renderRegistered) {
            WorldRenderEvents.LAST.register((context) -> onWorldRender(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true)));
            renderRegistered = true;
        }
    }

    private void onWorldRender(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!isEnabled() || !mode.is("Vanilla") || !hvhTarget.getValue() || !hvhRender.getValue()) return;
        if (mc.player == null || mc.world == null) return;
        if (renderTarget == null || !renderTarget.isAlive() || renderPredicted == null) return;

        LivingEntity target = renderTarget;
        Vec3d predicted = renderPredicted;

        // Бокс вокруг предсказанной позиции цели (как у TpAura — по ширине/высоте цели)
        float halfWidth = target.getWidth() / 2f;
        Box hitbox = new Box(
                predicted.x - halfWidth, predicted.y, predicted.z - halfWidth,
                predicted.x + halfWidth, predicted.y + target.getHeight(), predicted.z + halfWidth
        );

        Vec3d camPos = camera.getPos();

        int colorMain = ColorProvider.getThemeColor();
        int colorTwo = ColorProvider.getThemeColorTwo();
        float mr = ((colorMain >> 16) & 0xFF) / 255f;
        float mg = ((colorMain >> 8) & 0xFF) / 255f;
        float mb = (colorMain & 0xFF) / 255f;
        float tr = ((colorTwo >> 16) & 0xFF) / 255f;
        float tg = ((colorTwo >> 8) & 0xFF) / 255f;
        float tb = (colorTwo & 0xFF) / 255f;

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(2.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 1) Бокс предикта (основной цвет темы)
        BufferBuilder boxBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        drawLineBox(boxBuffer, matrix,
                hitbox.minX - camPos.x, hitbox.minY - camPos.y, hitbox.minZ - camPos.z,
                hitbox.maxX - camPos.x, hitbox.maxY - camPos.y, hitbox.maxZ - camPos.z,
                mr, mg, mb, 0.8f);
        BufferRenderer.drawWithGlobalProgram(boxBuffer.end());

        // 2) Линия от ног игрока к ногам предикта (к низу бокса), основной цвет
        BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        double startX = mc.player.getX() - camPos.x;
        double startY = mc.player.getY() - camPos.y;
        double startZ = mc.player.getZ() - camPos.z;
        double endX = predicted.x - camPos.x;
        double endY = predicted.y - camPos.y;
        double endZ = predicted.z - camPos.z;
        line(lineBuffer, matrix, startX, startY, startZ, endX, endY, endZ, mr, mg, mb, 0.8f);

        // 3) При активном leave — точка направления (второй цвет темы):
        //    маленький бокс на земле + линия от ног игрока к этой точке
        if (renderLeaveTarget != null) {
            double lx = renderLeaveTarget.x - camPos.x;
            double ly = renderLeaveTarget.y - camPos.y;
            double lz = renderLeaveTarget.z - camPos.z;
            double s = 0.3;
            // Бокс-точка направления
            drawPointBox(lineBuffer, matrix, lx, ly, lz, s, tr, tg, tb, 0.9f);
            // Линия от ног игрока к точке направления
            line(lineBuffer, matrix, startX, startY, startZ, lx, ly, lz, tr, tg, tb, 0.9f);
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(1.0f);
    }

    private void drawLineBox(BufferBuilder buffer, Matrix4f matrix, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float r, float g, float b, float a) {
        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void drawPointBox(BufferBuilder buffer, Matrix4f matrix, double cx, double cy, double cz, double s, float r, float g, float b, float a) {
        double minX = cx - s, minY = cy - s, minZ = cz - s;
        double maxX = cx + s, maxY = cy + s, maxZ = cz + s;
        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void line(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }
}
