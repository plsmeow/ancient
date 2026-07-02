package tech.onetap.module.list.misc;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Scoreboard Health", moduleDesc = "Используй HP из скорборда", moduleCategory = ModuleCategory.MISC)
public class ScoreboardHealth extends Module {

    public float getRealHp(AbstractClientPlayerEntity player) {
        return getRealHpInternal(player);
    }

    public float getRealHp(PlayerEntity player) {
        return getRealHpInternal(player);
    }

    private float getRealHpInternal(PlayerEntity player) {
        if (player == null || player.getWorld() == null)
            return -1;

        Scoreboard scoreboard = player.getWorld().getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);

        if (objective == null)
            return -1;

        ReadableScoreboardScore score = scoreboard.getScore(player, objective);

        if (score == null)
            return -1;

        int hp = score.getScore();
        return hp > 0 ? hp : -1;
    }
}
