package tech.onetap.module.list.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import tech.onetap.event.list.EventHUD;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.EventUseItem;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.msdf.MsdfFont;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

@ModuleInformation(moduleName = "GapFix", moduleDesc = "Не даёт съесть золотое яблоко раньше серверной задержки", moduleCategory = ModuleCategory.PLAYER)
public class GapFix extends Module {

    private final SliderSetting delay = new SliderSetting("Задержка", 10, 0, 40, 1);
    private final BooleanSetting enchanted = new BooleanSetting("Зачарованное яблоко", true);
    private final BooleanSetting counter = new BooleanSetting("Счётчик", true);

    private int cooldownTicks;
    private boolean consumedThisUse;

    @EventHandler
    private void onUpdate(EventPlayerUpdate ignored) {
        if (mc.player == null) {
            reset();
            return;
        }

        boolean usingGapple = mc.player.isUsingItem() && isGapple(mc.player.getActiveItem());

        if (usingGapple) {
            if (!consumedThisUse && mc.player.getItemUseTimeLeft() <= 0) {
                cooldownTicks = delay.getIntValue();
                consumedThisUse = true;
            }
        } else {
            consumedThisUse = false;
        }

        if (cooldownTicks > 0) cooldownTicks--;
    }

    @EventHandler
    private void onUseItem(EventUseItem e) {
        if (mc.player == null || cooldownTicks <= 0) return;
        if (isGapple(mc.player.getStackInHand(e.getHand()))) e.cancelEvent();
    }

    @EventHandler
    private void onRender(EventHUD ignored) {
        if (!counter.getValue() || cooldownTicks <= 0) return;
        if (mc.player == null || mc.options.hudHidden) return;

        MsdfFont font = Fonts.SFBOLD.get();
        String text = String.valueOf(cooldownTicks);
        float size = 9f;
        float width = font.getWidth(text, size);
        float x = (mc.getWindow().getScaledWidth() - width) / 2f;
        float y = mc.getWindow().getScaledHeight() / 2f - 22f;

        DrawUtil.drawText(font, text, x, y, ColorProvider.rgba(255, 255, 255, 255), size);
    }

    private boolean isGapple(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isOf(Items.GOLDEN_APPLE) || (enchanted.getValue() && stack.isOf(Items.ENCHANTED_GOLDEN_APPLE));
    }

    private void reset() {
        cooldownTicks = 0;
        consumedThisUse = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }
}
