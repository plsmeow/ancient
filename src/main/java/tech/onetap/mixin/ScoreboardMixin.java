package tech.onetap.mixin;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Scoreboard.class)
public class ScoreboardMixin {
    @Inject(
            method = "removeScoreHolderFromTeam",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ignoreInvalidRemove(String scoreHolderName, Team team, CallbackInfo ci) {
        if (((Scoreboard)(Object)this).getScoreHolderTeam(scoreHolderName) != team) {
            ci.cancel();
        }
    }
}