package tech.onetap.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Sprint", moduleDesc = "Автоматический спринт", moduleCategory = ModuleCategory.MOVEMENT)
public class Sprint extends Module {
    @EventHandler
    public void onUpdate(EventTick event) {
        if (mc.player == null) return;
        mc.options.sprintKey.setPressed(false);

        mc.player.setSprinting((!mc.player.isTouchingWater() || mc.player.isSubmergedInWater()) && !mc.player.isGliding() && mc.player.isWalking() && mc.player.canSprint() && !mc.player.isUsingItem() && !mc.player.isBlind() && (!mc.player.hasVehicle() || (mc.player.getVehicle().canSprintAsVehicle() && mc.player.getVehicle().isLogicalSideForUpdatingMovement()) && !mc.player.isGliding() && (!mc.player.shouldSlowDown() || mc.player.isSubmergedInWater())) && mc.player.input.hasForwardMovement() && (!mc.player.horizontalCollision && !mc.player.collidedSoftly));
    }
}