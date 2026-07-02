package tech.onetap.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import tech.onetap.Onetap;
import tech.onetap.module.list.misc.NameProtect;
import tech.onetap.util.replace.ReplaceUtil;

@Mixin(Team.class)
public class TeamMixin {

    @ModifyArg(
            method = "decorateName(Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/MutableText;append(Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;",
                    ordinal = 0
            ),
            index = 0
    )
    private Text modify(Text original) {
        if (MinecraftClient.getInstance().player == null) return original;
        String me = MinecraftClient.getInstance().player.getNameForScoreboard();
        String replaced = Onetap.getInstance().getModuleStorage().get(NameProtect.class).getCustomName(me);

        if (me.equals(replaced)) return original;

        return ReplaceUtil.replaceLiteral(original, me, replaced);
    }
}