package tech.onetap.module.list.misc;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.util.friend.Friend;
import tech.onetap.util.friend.FriendRepository;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "Animated Name", moduleCategory = ModuleCategory.MISC)
public class NameProtect extends Module {

    public final BooleanSetting hideFriends = new BooleanSetting("Скрыть друзей", false);

    /**
     * Генерирует текущий кадр анимации для строки на основе времени.
     */
    private String getAnimatedName(String name) {
        if (name == null || name.isEmpty()) return "";

        // Строим полную последовательность шагов анимации (туда и обратно)
        List<String> frames = new ArrayList<>();

        // Шаг 1: Идем вперед (p, pl, pls...)
        for (int i = 1; i <= name.length(); i++) {
            frames.add(name.substring(0, i));
        }
        // Шаг 2: Идем назад (plsmeo, plsme, plsm...)
        for (int i = name.length() - 1; i > 1; i--) {
            frames.add(name.substring(0, i));
        }

        // Скорость анимации: смена кадра каждые 200 миллисекунд (можно настроить)
        long speed = 400L;
        int currentFrameIndex = (int) ((System.currentTimeMillis() / speed) % frames.size());

        // Возвращаем покрашенный кусок никнейма
        return frames.get(currentFrameIndex) + "|";
    }

    public String getCustomName() {
        if (!isEnabled() || mc.player == null) {
            return mc.player != null ? mc.player.getNameForScoreboard() : "";
        }

        // Берем реальный ник игрока
        String myRealName = mc.player.getNameForScoreboard();
        return getAnimatedName(myRealName);
    }

    public String getCustomName(String originalName) {
        if (!isEnabled() || mc.player == null) {
            return originalName;
        }

        String myRealName = mc.player.getNameForScoreboard();

        // Генерируем текущий кадр анимации для нашего ника
        String animatedMe = getAnimatedName(myRealName);

        // Подменяем наш ник в табе/скорборде на анимированный
        if (originalName.contains(myRealName)) {
            return originalName.replace(myRealName, animatedMe);
        }

        // Если включено скрытие друзей, их тоже можно анимировать их же никами
        if (hideFriends.getValue()) {
            var friends = FriendRepository.getFriends();

            for (Friend friend : friends) {
                if (originalName.contains(friend.name())) {
                    String animatedFriend = getAnimatedName(friend.name());
                    return originalName.replace(friend.name(), animatedFriend);
                }
            }
        }

        return originalName;
    }
}