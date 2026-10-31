package meow.ancient.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import meow.ancient.client.command.CommandManager;
import meow.ancient.client.command.CommandSuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions {
    @Shadow @Final private EditBox input;
    @Shadow @Final private Font font;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private int commandUsagePosition;
    @Shadow private int commandUsageWidth;
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean keepSuggestions;
    @Shadow private boolean allowSuggestions;
    @Shadow private boolean currentParseIsCommand;
    @Shadow private boolean currentParseIsMessage;
    @Shadow private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow public abstract void showSuggestions(boolean narrate);
    @Shadow public abstract void hide();

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void ancient$onUpdateCommandInfo(CallbackInfo ci) {
        String text = this.input.getValue();
        String prefix = CommandManager.INSTANCE.getPrefix();
        if (prefix.isEmpty() || !text.startsWith(prefix)) return;
        ci.cancel();
        if (this.keepSuggestions) return;

        this.currentParse = null;
        this.currentParseIsCommand = false;
        this.currentParseIsMessage = false;
        this.input.setSuggestion(null);
        this.hide();
        this.commandUsage.clear();
        this.commandUsageWidth = 0;
        this.commandUsagePosition = this.input.getScreenX(0);

        String usage = CommandSuggestionProvider.INSTANCE.getUsage(text);
        if (usage != null) {
            FormattedCharSequence seq = Component.literal(usage).withStyle(CommandSuggestions.USAGE_FORMAT).getVisualOrderText();
            this.commandUsage.add(seq);
            this.commandUsageWidth = this.font.width(seq);
        }

        Suggestions suggestions = CommandSuggestionProvider.INSTANCE.getSuggestions(text, this.input.getCursorPosition());
        this.pendingSuggestions = suggestions == null ? Suggestions.empty() : CompletableFuture.completedFuture(suggestions);
        if (this.allowSuggestions && this.minecraft.options.autoSuggestions().get()) {
            this.showSuggestions(false);
        }
    }
}
