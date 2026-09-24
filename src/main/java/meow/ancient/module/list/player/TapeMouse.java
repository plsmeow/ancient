package meow.ancient.module.list.player;

import meteordevelopment.orbit.EventHandler;
import meow.ancient.event.list.EventTick;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.settings.BooleanSetting;
import meow.ancient.module.settings.SliderSetting;
import meow.ancient.util.bot.BotSessionManager;

@ModuleInformation(moduleName = "Tape Mouse", moduleDesc = "Автоматические клики для фоновых ботов", moduleCategory = ModuleCategory.PLAYER)
public class TapeMouse extends Module {
    private final SliderSetting cps = new SliderSetting("CPS", 1, 0, 2, 0.05f);
    private final BooleanSetting rightClick = new BooleanSetting("Правая кнопка", false);
    private final BooleanSetting onlyBots = new BooleanSetting("Только боты", true);

    private long lastClick;

    @EventHandler
    private void onTick(EventTick eventTick) {
        long delay = (long) (1000.0f / cps.getValue());
        long now = System.currentTimeMillis();

        if (now - lastClick < delay) {
            return;
        }

        if (!onlyBots.getValue()) {
            clickForLocalPlayer();
        }

        if (!BotSessionManager.getConnections().isEmpty()) {
            BotSessionManager.pulseBots(rightClick.getValue());
        }

        lastClick = now;
    }

    private void clickForLocalPlayer() {
        if (mc.player == null || mc.currentScreen != null) return;

        if (rightClick.getValue()) {
            mc.doItemUse();
        } else {
            mc.doAttack();
        }
    }
}