package tech.onetap.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.PlayerInput;

import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.rotation.FreeLookComponent;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "ElytraBounce", moduleDesc = "Рекаст элитры при касании земли", moduleCategory = ModuleCategory.MOVEMENT)
public class ElytraBounce extends Module {

    private static final Identifier ELYTRA_FLYING_SOUND = SoundEvents.ITEM_ELYTRA_FLYING.id();
    private static final int ROTATION_PRIORITY = 2;

    private final BooleanSetting autoJump = new BooleanSetting("Автопрыжок", true);
    private final BooleanSetting holdForward = new BooleanSetting("Зажимать W", true);
    private final BooleanSetting keepSprint = new BooleanSetting("Спринт", true);

    private final BooleanSetting pitchLock = new BooleanSetting("Pitch Lock", false);
    private final SliderSetting lockedPitch = new SliderSetting("Pitch", 0, -90, 90, 0.5)
            .setVisible(pitchLock::getValue);
    private final SliderSetting pitchSpeed = new SliderSetting("Скорость Pitch", 180, 1, 180, 1)
            .setVisible(pitchLock::getValue);
    private final BooleanSetting pitchOnlyGliding = new BooleanSetting("Pitch только в полёте", true)
            .setVisible(pitchLock::getValue);
    private final BooleanSetting pitchClientLook = new BooleanSetting("Pitch клиент лук", false)
            .setVisible(pitchLock::getValue);

    private boolean wasGliding;

    @Override
    public void onDisable() {
        super.onDisable();
        wasGliding = false;
    }

    @EventHandler
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null || !hasElytra()) return;

        boolean gliding = mc.player.isGliding();
        if (wasGliding && !gliding) {
            mc.getSoundManager().stopSounds(ELYTRA_FLYING_SOUND, SoundCategory.PLAYERS);
        }

        if (checkConditions()) {
            if (jumpRequested()) {
                if (mc.player.isOnGround()) {
                    mc.player.jump();
                } else if (!gliding) {
                    recast();
                }
            }

            if (keepSprint.getValue()) {
                mc.player.setSprinting(!gliding || mc.player.isOnGround());
            }
        }

        wasGliding = gliding;
        applyPitchLock();
    }

    @EventHandler
    private void onMoveInput(MoveInputEvent e) {
        if (mc.player == null || mc.currentScreen != null || !hasElytra()) return;

        e.jump = false;
        if (holdForward.getValue() && e.forward == 0) e.forward = 1;
    }

    private void recast() {
        PlayerInput input = mc.player.input.playerInput;
        NetworkUtils.sendSilentPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        mc.player.startGliding();
        NetworkUtils.sendSilentPacket(new PlayerInputC2SPacket(
                new PlayerInput(input.forward(), input.backward(), input.left(), input.right(), true, input.sneak(), input.sprint())));
        NetworkUtils.sendSilentPacket(new PlayerInputC2SPacket(
                new PlayerInput(input.forward(), input.backward(), input.left(), input.right(), false, input.sneak(), input.sprint())));
    }

    private void applyPitchLock() {
        if (!pitchLock.getValue()) return;
        if (pitchOnlyGliding.getValue() && !mc.player.isGliding()) return;

        boolean clientLook = pitchClientLook.getValue();
        float yaw = clientLook ? mc.player.getYaw() : FreeLookComponent.getFreeYaw();
        float speed = pitchSpeed.getFloatValue();

        RotationComponent.update(
                new Rotation(yaw, lockedPitch.getFloatValue()),
                360, speed, 360, speed,
                0, ROTATION_PRIORITY, clientLook
        );
    }

    private boolean checkConditions() {
        if (!hasElytra()) return false;
        if (mc.player.getAbilities().flying || mc.player.hasVehicle() || mc.player.isClimbing()) return false;
        if (mc.player.isTouchingWater() || mc.player.isInLava()) return false;
        return !mc.player.hasStatusEffect(StatusEffects.LEVITATION);
    }

    private boolean jumpRequested() {
        return autoJump.getValue() || mc.options.jumpKey.isPressed();
    }

    private boolean hasElytra() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chest.isOf(Items.ELYTRA) && LivingEntity.canGlideWith(chest, EquipmentSlot.CHEST);
    }
}
