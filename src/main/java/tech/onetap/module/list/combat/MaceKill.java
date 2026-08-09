package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.text.ValueUnit;

@ModuleInformation(moduleName = "MaceKill", moduleDesc = "Усиливает булаву через packet criticals", moduleCategory = ModuleCategory.COMBAT)
public final class MaceKill extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Default", "Default", "Custom", "Calculate", "Matrix", "NCP", "Sample");
    private final SliderSetting fallHeight = new SliderSetting("Fall Height", 20, 5, 150, 1);
    private final SliderSetting packetCount = new SliderSetting("Кол-во пакетов", 2, 1, 15, 1)
            .setVisible(() -> mode.is("Custom"));
    private final SliderSetting heightSeparator = new SliderSetting("Height Separator", 8.9, 0.1, 20, 0.1)
            .setVisible(() -> mode.is("NCP"));
    public final BooleanSetting customAttackDelay = new BooleanSetting("Кастомная задержка", true);
    private final SliderSetting attackDelay = new SliderSetting("Задержка удара", ValueUnit.countable("тик", "тика", "тиков"), 5, 0, 20, 1)
            .setVisible(customAttackDelay::getValue);
    public final BooleanSetting funskyAuto = new BooleanSetting("Funsky Auto", false);
    private final ModeSetting funskyMode = new ModeSetting("Funsky режим", "Hit", "Hit", "4.5s", "5s")
            .setVisible(() -> funskyAuto.getValue());
    private final BooleanSetting funskyChatTimer = new BooleanSetting("Debug", false)
            .setVisible(() -> funskyAuto.getValue() && (funskyMode.is("4.5s") || funskyMode.is("5s")));
    private final BooleanSetting funskyForceFirst = new BooleanSetting("Force 1", true)
            .setVisible(() -> funskyAuto.getValue() && (funskyMode.is("4.5s") || funskyMode.is("5s")));

    public static boolean cancelCrit;
    public static boolean killAuraTriggered;

    private boolean funskyWaitingSmash;
    private boolean funskyFirstSmash;
    private boolean funskyHitRegistered;
    private int funskyGraceTicks;
    private int funskyPrevHurtTime;
    private LivingEntity funskyTarget;
    private int funskySmashTicks = 1000;
    private double lastFunskyTimerValue = -1.0;

    private int attackTicks;

    public int getAttackTicks() {
        return attackTicks;
    }

    public boolean isCustomDelayEnabled() {
        return customAttackDelay.getValue();
    }

    public void resetAttackDelay() {
        attackTicks = attackDelay.getIntValue();
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (attackTicks > 0) attackTicks--;
        if (funskySmashTicks < 1000) funskySmashTicks++;

        if (funskyChatTimer.getValue() && isFunskyActive() && !funskyMode.is("Hit")) {
            int delay = getFunskySmashDelayTicks();
            if (funskySmashTicks < delay) {
                double remaining = (delay - funskySmashTicks) * 0.05;
                double displayValue = Math.round(remaining * 10.0) / 10.0;
                if (displayValue != lastFunskyTimerValue) {
                    lastFunskyTimerValue = displayValue;
                    ChatUtil.send(String.format("§e%.1f", displayValue));
                }
            } else if (lastFunskyTimerValue != 0.0) {
                lastFunskyTimerValue = 0.0;
            }
        } else if (lastFunskyTimerValue != -1.0) {
            lastFunskyTimerValue = -1.0;
        }
    }

    public boolean isFunskySmashReady() {
        return funskyFirstSmash || funskySmashTicks >= getFunskySmashDelayTicks();
    }

    private int getFunskySmashDelayTicks() {
        if (funskyMode.is("4.5s")) return 90;
        if (funskyMode.is("5s")) return 100;
        return 0;
    }

    public boolean isFunskyActive() {
        return isEnabled() && funskyAuto.getValue();
    }

    public boolean isFunskyWaitingSmash() {
        return funskyWaitingSmash;
    }

    public boolean isFunskyTimerMode() {
        return funskyMode.is("4.5s") || funskyMode.is("5s");
    }

    public void updateFunskyState(LivingEntity target) {
        if (!isFunskyActive() || target == null || !target.isAlive()) {
            funskyTarget = null;
            funskyWaitingSmash = false;
            funskyFirstSmash = false;
            funskyHitRegistered = false;
            funskyGraceTicks = 0;
            funskyPrevHurtTime = 0;
            return;
        }
        if (target != funskyTarget) {
            funskyTarget = target;
            boolean timerMode = funskyMode.is("4.5s") || funskyMode.is("5s");
            funskyFirstSmash = timerMode && funskyForceFirst.getValue();
            funskyWaitingSmash = funskyFirstSmash;
            funskyHitRegistered = false;
            funskyGraceTicks = 0;
            funskyPrevHurtTime = target.hurtTime;
            return;
        }
        if (funskyGraceTicks > 0) funskyGraceTicks--;
        int hurtTime = target.hurtTime;
        if (!funskyWaitingSmash && funskyGraceTicks == 0) {
            if (hurtTime > funskyPrevHurtTime) {
                funskyHitRegistered = true;
            }
            boolean timerMode = funskyMode.is("4.5s") || funskyMode.is("5s");
            if (funskyHitRegistered) {
                if (!timerMode || funskySmashTicks >= 20) {
                    funskyWaitingSmash = true;
                }
            } else if (timerMode && !funskyFirstSmash && funskySmashTicks >= getFunskySmashDelayTicks()) {
                // Только таймерные режимы: смэш по истечении интервала без простого удара.
                // В режиме Hit задержка равна 0, и без этой проверки смэш включался бы сразу.
                funskyWaitingSmash = true;
            }
        }
        funskyPrevHurtTime = hurtTime;
    }

    public void onFunskySmash() {
        funskyWaitingSmash = false;
        funskyFirstSmash = false;
        funskyHitRegistered = false;
        funskyGraceTicks = 10;
        funskySmashTicks = 0;

        if (funskyChatTimer.getValue() && isFunskyActive() && !funskyMode.is("Hit")) {
            ChatUtil.send("§aReady");
            lastFunskyTimerValue = -1.0;
        }
    }

    private void resetFunskyState() {
        funskyTarget = null;
        funskyWaitingSmash = false;
        funskyFirstSmash = false;
        funskyHitRegistered = false;
        funskyGraceTicks = 0;
        funskyPrevHurtTime = 0;
        funskySmashTicks = 1000;
        lastFunskyTimerValue = -1.0;
    }

    @Override
    public void onEnable() {
        attackTicks = 0;
        resetFunskyState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        resetFunskyState();
        super.onDisable();
    }

    @EventHandler
    public void onAttack(EventAttack event) {
        if (killAuraTriggered) return;
        if (mc.player == null || mc.world == null) return;
        if (event.getEntity() instanceof EndCrystalEntity || cancelCrit) return;

        KillAura aura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
        AutoMace autoMace = Onetap.getInstance().getModuleStorage().get(AutoMace.class);

        // Funsky Auto: блокируем крит на простых ударах только пока KillAura держит цель;
        // без таргета ручной клик должен критовать как обычно
        if (isFunskyActive() && !funskyWaitingSmash && aura != null && aura.isEnabled() && aura.getTarget() != null) return;

        boolean autoMaceActive = aura != null && aura.isEnabled() && autoMace != null && autoMace.isEnabled();
        if (!autoMaceActive) {
            ItemStack mainHand = mc.player.getMainHandStack();
            boolean isMace = mainHand.isOf(Items.MACE);
            boolean isCustomMace = mainHand.getName().getString().contains("1.21 Mace");
            if (!isMace && !isCustomMace) return;
        }

        doCrit();
    }

    public void doCrit() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        if (mc.player.isInLava() || mc.player.isSubmergedInWater()) return;

        int height = determineHeight();
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        switch (mode.getValue()) {
            case "Default" -> {
                sendTpPacket(x, y + height, z, false);
                sendTpPacket(x, y, z, false);
            }
            case "Custom" -> runIterationMode(x, y, z, height, packetCount.getIntValue(), 0);
            case "Calculate" -> runIterationMode(x, y, z, height, calcIterations(height), 0);
            case "Sample" -> runIterationMode(x, y, z, height, calcIterations(height), 1);
            case "Matrix" -> {
                if (height > 10) {
                    int times = (int) Math.ceil(Math.abs(height / 10.0));
                    for (int i = 0; i < times; i++) {
                        sendTpPacket(x, y, z, false);
                    }
                } else {
                    for (int i = 0; i < 2; i++) {
                        sendTpPacket(x, y, z, mc.player.isOnGround());
                    }
                }
                sendTpPacket(x, y + height, z, false);
                sendTpPacket(x, y, z, false);
            }
            case "NCP" -> {
                double targetY = y + height;
                double distance = Math.abs(y - targetY);
                int packets = (int) (distance / heightSeparator.getValue());
                for (int i = 0; i < packets; i++) {
                    sendTpPacket(x, y, z, false);
                }
                sendTpPacket(x, targetY, z, false);
                for (int i = 0; i < packets; i++) {
                    sendTpPacket(x, y, z, false);
                }
            }
        }
    }

    private int calcIterations(int height) {
        return height < 11 ? 1 : height < 31 ? 2 : (int) Math.ceil(height / 10.0f);
    }

    private void runIterationMode(double x, double y, double z, int height, int iterations, int decrement) {
        int count = iterations - decrement;
        if (!mc.player.isOnGround()) {
            for (int i = 0; i < count; i++) {
                sendTpPacket(x, y, z, false);
            }
            sendTpPacket(x, y + height, z, false);
            for (int i = 0; i < count; i++) {
                sendTpPacket(x, y, z, false);
            }
        } else {
            float rise = 0.5f;
            for (int i = 0; i < count; i++) {
                sendTpPacket(x, y + rise, z, false);
                mc.player.setPosition(x, y + rise, z);
            }
            sendTpPacket(x, y + height, z, false);
            for (int i = 0; i < count; i++) {
                sendTpPacket(x, y + rise, z, false);
                mc.player.setPosition(x, y + rise, z);
            }
        }
    }

    private int determineHeight() {
        Box boundingBox = mc.player.getBoundingBox();
        for (int i = fallHeight.getIntValue(); i >= 1; i--) {
            Box newBB = boundingBox.offset(0, i, 0);
            boolean noCollision = true;
            for (VoxelShape shape : mc.world.getCollisions(mc.player, newBB)) {
                if (shape.isEmpty()) continue;
                noCollision = false;
                break;
            }
            if (noCollision) return i;
        }
        return 0;
    }

    private void sendTpPacket(double x, double y, double z, boolean onGround) {
        NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, mc.player.horizontalCollision));
    }
}
