package tech.onetap.util.server;

import lombok.experimental.UtilityClass;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import tech.onetap.Onetap;
import tech.onetap.module.list.misc.ScoreboardHealth;
import tech.onetap.util.IMinecraft;

@UtilityClass
public class Server implements IMinecraft {
    public int getPing(PlayerEntity entity) {
        PlayerListEntry list = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return list != null ? list.getLatency() : 0;
    }

    public float getHealth(LivingEntity entity, boolean gapple) {
        if (Onetap.getInstance().getModuleStorage().get(ScoreboardHealth.class).isEnabled()) {
            if (entity instanceof PlayerEntity player) {
                if (player.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME) == null) return 0f;
                ScoreboardObjective objective = player.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
                if (objective == null) return 0f;
                ReadableScoreboardScore score = player.getScoreboard().getScore(player, objective);
                MutableText text = ReadableScoreboardScore.getFormattedScore(score, objective.getNumberFormatOr(StyledNumberFormat.EMPTY));

                return Float.parseFloat(text.getString().replaceAll("\\D", ""));
            } else return entity.getHealth() + (gapple ? entity.getAbsorptionAmount() : 0f);
        } else return entity.getHealth() + (gapple ? entity.getAbsorptionAmount() : 0f);
    }
}