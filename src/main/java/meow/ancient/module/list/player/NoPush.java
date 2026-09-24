package meow.ancient.module.list.player;

import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.settings.BooleanSetting;
import meow.ancient.module.settings.ModeListSetting;

@ModuleInformation(moduleName = "No Push", moduleDesc = "Отключает толкание игроками и блоками", moduleCategory = ModuleCategory.PLAYER)
public class NoPush extends Module {
    public final ModeListSetting objects = new ModeListSetting("Обьекты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Блоки", true)
    );
}