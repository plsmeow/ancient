package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.text.TextColor;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.render.hud.Interface;
import tech.onetap.util.base.Instance;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInformation(moduleName = "UseTracker", moduleDesc = "Показывает кто подобрал/использовал предмет", moduleCategory = ModuleCategory.MISC)
public class UseTracker extends Module {

    private final List<PickupLog> logs = new CopyOnWriteArrayList<>();

    private static final String[] ALLOWED_PICKUP_KEYWORDS = {
            "незерит", "шар", "талисман",
            "зелье", "арбалет", "элитры", "фейерверк", "трезубец"
    };

    public UseTracker() {
        HudRenderCallback.EVENT.register(this::onRenderHUD);
    }

    @Override
    public void onDisable() {
        logs.clear();
        super.onDisable();
    }


    private String getProtectedName(PlayerEntity player) {
        String originalName = player.getName().getString();

        NameProtect nameProtect = Instance.get(NameProtect.class);
        boolean isNameProtectEnabled = nameProtect != null && nameProtect.isEnabled();

        return originalName;
    }

    @Subscribe
    public void onPacketReceive(EventPacket event) {
        if (mc.world == null || mc.player == null) return;

        if (event.getPacket() instanceof ItemPickupAnimationS2CPacket packet) {
            Entity itemEntity = mc.world.getEntityById(packet.getEntityId());
            Entity collectorEntity = mc.world.getEntityById(packet.getCollectorEntityId());

            if (itemEntity instanceof ItemEntity item && collectorEntity instanceof PlayerEntity player) {
                if (player == mc.player) return;

                ItemStack stack = item.getStack().copy();

                // о нет селфкод

                // Получаем "сырое" имя предмета
                String rawName = stack.getName().getString();

                // Очищаем строку от серверных цветовых кодов
                String cleanName = rawName.replaceAll("(?i)§[0-9a-fk-orx]", "");

                // Переводим в нижний регистр безопасно для кириллицы
                String itemName = cleanName.toLowerCase(Locale.ROOT);

                // Проверяем, содержит ли имя одно из разрешенных ключевых слов
                boolean shouldLog = false;
                for (String keyword : ALLOWED_PICKUP_KEYWORDS) {
                    if (itemName.contains(keyword)) {
                        shouldLog = true;
                        break;
                    }
                }

                // Если предмет прошел фильтр, добавляем его в лог
                if (shouldLog) {
                    stack.setCount(packet.getStackAmount());
                    String playerName = getProtectedName(player); // ИСПОЛЬЗУЕМ ЗАЩИЩЕННЫЙ НИК
                    logs.add(new PickupLog(playerName, stack, 3000, "Подобрал:"));
                }
            }
        }

        // 2. Отслеживание ИСПОЛЬЗОВАНИЯ предметов и ТОТЕМОВ
        if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket) {

            // Статус 9 означает завершение использования предмета (доел/допил)
            if (statusPacket.getStatus() == 9) {
                Entity entity = statusPacket.getEntity(mc.world);

                if (entity instanceof PlayerEntity player) {
                    // Игнорируем использование предметов самим собой
                    if (player == mc.player) return;

                    ItemStack usedStack = player.getMainHandStack();
                    if (usedStack.isEmpty() || (!usedStack.contains(DataComponentTypes.FOOD) && usedStack.getItem() != Items.POTION)) {
                        usedStack = player.getOffHandStack();
                    }

                    if (!usedStack.isEmpty()) {
                        String playerName = getProtectedName(player); // ИСПОЛЬЗУЕМ ЗАЩИЩЕННЫЙ НИК
                        logs.add(new PickupLog(playerName, usedStack.copy(), 3000, "Использовал:"));
                    }
                }
            }

            // Статус 35 означает срабатывание Тотема бессмертия
            else if (statusPacket.getStatus() == 35) {
                Entity entity = statusPacket.getEntity(mc.world);

                if (entity instanceof PlayerEntity player) {
                    // Игнорируем потерю тотема самим собой
                    if (player == mc.player) return;

                    String playerName = getProtectedName(player); // ИСПОЛЬЗУЕМ ЗАЩИЩЕННЫЙ НИК
                    ItemStack totemStack = Items.TOTEM_OF_UNDYING.getDefaultStack();

                    logs.add(new PickupLog(playerName, totemStack, 3000, "Потерял:"));
                }
            }
        }
    }

    private void onRenderHUD(DrawContext context, RenderTickCounter tickCounter) {
        if (logs.isEmpty() || !this.isEnabled()) return;

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        float startY = (screenHeight / 2f) + 20f;
        float currentY = startY;

        Interface hudModule = Instance.get(Interface.class);
        boolean isOldStyle = hudModule != null && hudModule.hudStyle.is("Old");

        for (PickupLog log : logs) {
            log.update();
            float animValue = (float) log.animation.getValue();

            if (log.isRemoving && animValue <= 0.01f) {
                logs.remove(log);
                continue;
            }

            if (isOldStyle) {
                renderLogOld(context, log, screenWidth, currentY);
                currentY += 10f;
            } else {
                renderLog(context, log, screenWidth, currentY, animValue, hudModule);
                currentY += (13.0f + 3f) * animValue;
            }
        }
    }

    private void renderLogOld(DrawContext context, PickupLog log, int screenWidth, float y) {
        String actionText = log.playerName + " " + log.actionText;
        String itemName = log.stack.getName().getString();

        float fontSize = 7.5f;
        float actionWidth = Fonts.SFMEDIUM.get().getWidth(actionText, fontSize);
        float itemWidth = Fonts.SFMEDIUM.get().getWidth(itemName, fontSize);

        float gap = 4.5f;
        float totalWidth = actionWidth + gap + itemWidth;
        float x = (screenWidth - totalWidth) / 2f;

        int itemColorRgb = 0xFFAA00;
        TextColor styleColor = log.stack.getName().getStyle().getColor();
        if (styleColor != null) {
            itemColorRgb = styleColor.getRgb();
        }

        int itemColor = ColorProvider.rgba(
                (itemColorRgb >> 16) & 0xFF,
                (itemColorRgb >> 8) & 0xFF,
                itemColorRgb & 0xFF,
                255
        );

        DrawUtil.drawText(Fonts.SFMEDIUM.get(), actionText, x, y, ColorProvider.rgba(255, 255, 255, 255), fontSize);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), itemName, x + actionWidth + gap, y, itemColor, fontSize);
    }

    private void renderLog(DrawContext context, PickupLog log, int screenWidth, float y, float animValue, Interface hud) {
        String actionText = log.playerName + " " + log.actionText;
        String itemName = log.stack.getName().getString();

        float fontSize = 6.5f;
        float actionWidth = Fonts.SFMEDIUM.get().getWidth(actionText, fontSize);
        float itemWidth = Fonts.SFMEDIUM.get().getWidth(itemName, fontSize);

        float gap = 4.5f;

        float height = 13.0f;
        float totalWidth = 20f + actionWidth + gap + itemWidth + 5f;

        float x = (screenWidth - totalWidth) / 2f;
        int alphaInt = (int) (255 * Math.max(0, Math.min(1, animValue)));

        // --- Анимация ---
        context.getMatrices().push();
        context.getMatrices().translate(x + totalWidth / 2f, y + height / 2f, 0);
        context.getMatrices().scale(animValue, animValue, 1f);
        context.getMatrices().translate(-(x + totalWidth / 2f), -(y + height / 2f), 0);

        // --- Фон ---
        if (hud != null && hud.isEnabled()) {
            hud.drawBackground(x, y, totalWidth, height, 3, alphaInt);
        } else {
            DrawUtil.drawRound(x, y, totalWidth, height, 3, ColorProvider.rgba(25, 25, 25, (int)(150 * animValue)));
        }

        // --- Иконка предмета ---
        context.getMatrices().push();
        float iconScale = 0.6f;
        context.getMatrices().translate(x + 3.5f, y + 1.5f, 0);
        context.getMatrices().scale(iconScale, iconScale, 1f);
        context.drawItem(log.stack, 0, 0);
        context.getMatrices().pop();

        // --- Разделительная полоска ---
        DrawUtil.drawRound(x + 16f, y + 2f, 0.5f, height - 4f, 0, ColorProvider.rgba(125, 125, 125, alphaInt));

        // --- Цвета ---
        int actionColor = ColorProvider.rgba(255, 255, 255, alphaInt);

        int itemColorRgb = 0xFFAA00; // Желтый цвет по умолчанию
        TextColor styleColor = log.stack.getName().getStyle().getColor();
        if (styleColor != null) {
            itemColorRgb = styleColor.getRgb();
        }

        int itemColor = ColorProvider.rgba(
                (itemColorRgb >> 16) & 0xFF,
                (itemColorRgb >> 8) & 0xFF,
                itemColorRgb & 0xFF,
                alphaInt
        );

        // --- Отрисовка текста ---
        float textX = x + 20f;
        float textY = y + 2.75f;

        DrawUtil.drawText(Fonts.SFMEDIUM.get(), actionText, textX, textY, actionColor, fontSize);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), itemName, textX + actionWidth + gap, textY, itemColor, fontSize);

        context.getMatrices().pop();
    }

    // --- ЛОГИКА АНИМАЦИИ ---
    private static class PickupLog {
        public final String playerName;
        public final ItemStack stack;
        public final String actionText;
        private final long startTime;
        private final long maxLifeTime;

        public boolean isRemoving = false;
        public final Animation animation;

        public PickupLog(String playerName, ItemStack stack, long maxLifeTime, String actionText) {
            this.playerName = playerName;
            this.stack = stack;
            this.maxLifeTime = maxLifeTime;
            this.actionText = actionText;
            this.startTime = System.currentTimeMillis();

            this.animation = new Animation(Easing.BACK_OUT, 300);
        }

        public void update() {
            long timeAlive = System.currentTimeMillis() - startTime;

            if (timeAlive > maxLifeTime && !isRemoving) {
                isRemoving = true;
            }

            animation.run(isRemoving ? 0.0f : 1.0f);
        }
    }
}