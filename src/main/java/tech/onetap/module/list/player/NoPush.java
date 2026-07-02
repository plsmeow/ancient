package tech.onetap.module.list.player;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;

@ModuleInformation(moduleName = "No Push", moduleCategory = ModuleCategory.PLAYER)
public class NoPush extends Module {
    public final ModeListSetting objects = new ModeListSetting("Обьекты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Блоки", true)
    );
}