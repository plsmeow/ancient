package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.MutableText;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventHUD;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.misc.ScoreboardHealth;

import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.party.PartyPlayerPos;
import tech.onetap.util.party.connection.PartyApiClient;
import tech.onetap.util.parse.ParseTextUtil;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.math.ProjectionUtil;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.msdf.MsdfFont;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.replace.ReplaceUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInformation(moduleName = "Tags", moduleDesc = "Теги над игроками", moduleCategory = ModuleCategory.RENDER)
public class Tags extends Module {

    private final ModeListSetting entityTypes = new ModeListSetting("Типы",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Предметы", true)
    );

    private final BooleanSetting displayPartyFriends = new BooleanSetting("Участники пати", false);
    private final ModeSetting style = new ModeSetting("Стиль", "Дефолт", "Дефолт", "Nursultan");

    private final Map<UUID, Text> normalizedNames = new ConcurrentHashMap<>();
    private int clearCacheTicker = 0;

    private final List<ItemStack> equipmentCache = new ArrayList<>();

    public static Text normalizeSmallCaps(Text text) {
        String[] from = {"ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ", "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "x", "ʏ", "ᴢ", "◆", "┃ "};
        String[] to = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "●", ""};
        for (int i = 0; i < from.length; i++) {
            text = ReplaceUtil.replace(text, from[i], to[i]);
        }
        return text;
    }

    private Text processNameInternal(PlayerEntity entity) {
        Text name = entity.getDisplayName();

        // AnimatedName отключен для Tags

// --- ЛОГИКА ДЛЯ НОВОГО СЕРВЕРА (Парсинг через кастомный шрифт) ---
        String detectedFont = findGroupFont(name);
        if (detectedFont != null) {
            // Извлекаем имя группы из пути шрифта (например, "custom:groups/hydra" -> "hydra")
            String groupName = detectedFont.substring(detectedFont.lastIndexOf('/') + 1);

            // Сопоставляем имя файла с нужным текстом и цветом привилегии
            String prefixReplacement = switch (groupName) {
                case "default"     -> "§7Игрок";
                case "hydra"       -> "§2Гидра";
                case "ares"        -> "§cAрес";
                case "aristocrat"  -> "§6Аристократ";
                case "cerberus"    -> "§4Цербер";
                case "kronos"      -> "§5Кронос";
                case "pandar"      -> "§eПандар";
                case "phobos"      -> "§bФобос";
                case "phoenix"     -> "§dФеникс";
                case "cold"        -> "§bХолод";
                case "heat"        -> "§cЖара";
                case "helper"      -> "§eHELPER";
                case "moder"       -> "§9MODER";
                case "shelper"     -> "§6ST.HELPER";
                case "smoder"      -> "§1ST.MODER";
                case "summer"      -> "§aЛето";
                case "triton"      -> "§3Тритон";
                case "winter"      -> "§aЗима";
                case "youtuber"    -> "§cYT";
                case "admin"       -> "§4ADMIN";
                default            -> null;
            };

            if (prefixReplacement != null) {
                // Безопасно заменяем букву "a" ТОЛЬКО внутри компонента с этим шрифтом
                name = replaceOnlyInFont(name, detectedFont, prefixReplacement);
            }
        }

        // --- СТАРАЯ ЛОГИКА ДЛЯ ДРУГОГО СЕРВЕРА (НЕ ТРОГАЕМ) ---
        String s = name.getString();
        if (s.contains("ꔲ")) name = ReplaceUtil.replace(name, "ꔲ", "§5BULL");
        if (s.contains("ꕓ")) name = ReplaceUtil.replace(name, "ꕓ", "§8GHOST");
        if (s.contains("ꔨ")) name = ReplaceUtil.replace(name, "ꔨ", "§dDRAGON");
        if (s.contains("ꔂ")) name = ReplaceUtil.replace(name, "ꔂ", "§9D.MODER");
        if (s.contains("ꔦ")) name = ReplaceUtil.replace(name, "ꔦ", "§9D.ML.ADMIN");
        if (s.contains("ꕀ")) name = ReplaceUtil.replace(name, "ꕀ", "§2HYDRA");
        if (s.contains("ꕖ")) name = ReplaceUtil.replace(name, "ꕖ", "§7BUNNY");
        if (s.contains("ꕒ")) name = ReplaceUtil.replace(name, "ꕒ", "§fRABBIT");
        if (s.contains("ꕈ")) name = ReplaceUtil.replace(name, "ꕈ", "§aCOBRA");
        if (s.contains("ꔶ")) name = ReplaceUtil.replace(name, "ꔶ", "§6TIGER");
        if (s.contains("ꕠ")) name = ReplaceUtil.replace(name, "ꕠ", "§eD.HELPER");
        if (s.contains("ꔉ")) name = ReplaceUtil.replace(name, "ꔉ", "§eHELPER");
        if (s.contains("ꔆ")) name = ReplaceUtil.replace(name, "ꔆ", "§7D.MODER");
        if (s.contains("ꕄ")) name = ReplaceUtil.replace(name, "ꕄ", "§4DRACULA");
        if (s.contains("ꔰ")) name = ReplaceUtil.replace(name, "ꔰ", "§7D.ML.ADMIN");
        if (s.contains("ꔐ")) name = ReplaceUtil.replace(name, "ꔐ", "§1D.GL.MODER");
        if (s.contains("ꔔ")) name = ReplaceUtil.replace(name, "ꔔ", "§7D.GL.MODER");
        if (s.contains("ꔢ")) name = ReplaceUtil.replace(name, "ꔢ", "§7D.ST.MODER");
        if (s.contains("ꕡ")) name = ReplaceUtil.replace(name, "ꕡ", "§6ST.HELPER");
        if (s.contains("ꕅ")) name = ReplaceUtil.replace(name, "ꕅ", "§5MEDIA+");
        if (s.contains("ꔗ")) name = ReplaceUtil.replace(name, "ꔗ", "§9MODER");
        if (s.contains("ꕗ")) name = ReplaceUtil.replace(name, "ꕗ", "§4D.ADMIN");
        if (s.contains("ꔘ")) name = ReplaceUtil.replace(name, "ꔘ", "§9D.ST.MODER");
        if (s.contains("ꔳ")) name = ReplaceUtil.replace(name, "ꔳ", "§bML.ADMIN");
        if (s.contains("ꔁ")) name = ReplaceUtil.replace(name, "ꔁ", "§5MEDIA");
        if (s.contains("ꔅ")) name = ReplaceUtil.replace(name, "ꔅ", "§cYT");
        if (s.contains("ꕁ")) name = ReplaceUtil.replace(name, "ꕁ", "§6LEGENDA");

        return normalizeSmallCaps(name);
    }

    /**
     * Рекурсивно пересобирает текстовый компонент, заменяя "a" на префикс
     * исключительно в том блоке текста, где установлен целевой шрифт группы.
     */
    private Text replaceOnlyInFont(Text text, String targetFont, String replacement) {
        if (text == null) return null;

        MutableText result;
        Style style = text.getStyle();

        // Проверяем, принадлежит ли текущий фрагмент текста нашему шрифту
        if (style != null && style.getFont() != null && style.getFont().toString().equals(targetFont)) {
            // Делаем замену "a" только в этой части
            String updatedContent = text.getString().replace("a", replacement);
            result = Text.literal(updatedContent).setStyle(style);
        } else {
            // Если шрифт не тот (например, это сам ник игрока), оставляем текст нетронутым
            if (text instanceof MutableText) {
                result = ((MutableText) text).copyContentOnly().setStyle(style);
            } else {
                result = Text.literal(text.getString()).setStyle(style);
            }
        }

        // Рекурсивно обрабатываем и добавляем все вложенные элементы (siblings)
        for (Text sibling : text.getSiblings()) {
            result.append(replaceOnlyInFont(sibling, targetFont, replacement));
        }

        return result;
    }

    /**
     * Рекурсивный метод для поиска кастомного шрифта группы внутри структуры Text
     */
    private String findGroupFont(Text text) {
        if (text == null) return null;

        // Проверяем стиль текущего компонента
        Style style = text.getStyle();
        if (style != null && style.getFont() != null) {
            String fontPath = style.getFont().toString(); // Например "custom:groups/hydra"
            if (fontPath.startsWith("custom:groups/")) {
                return fontPath;
            }
        }

        // Если в корневом элементе шрифт дефолтный, проверяем все вложенные элементы (siblings/extra)
        for (Text sibling : text.getSiblings()) {
            String found = findGroupFont(sibling);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private Text getNormalizedName(PlayerEntity entity) {
        return normalizedNames.computeIfAbsent(entity.getUuid(), uuid -> processNameInternal(entity));
    }

    @Subscribe
    private void onRender(EventHUD e) {
        if (clearCacheTicker++ > 100) {
            normalizedNames.clear();
            clearCacheTicker = 0;
        }

        if (mc.world == null || mc.player == null) return;

        MsdfFont font = Fonts.SFREGULAR.get();
        float tickDelta = e.getRenderTickCounter().getTickDelta(true);

        if (entityTypes.isEnabled("Игроки")) {
            renderPlayerTags(font, tickDelta, e);
        }

        if (entityTypes.isEnabled("Предметы")) {
            renderItemTags(font, tickDelta, e);
        }
    }

    private void renderPlayerTags(MsdfFont font, float tickDelta, EventHUD e) {
        List<AbstractClientPlayerEntity> worldPlayers = mc.world.getPlayers();

        for (PlayerEntity entity : worldPlayers) {
            if (entity == mc.player && !mc.getEntityRenderDispatcher().camera.isThirdPerson()) continue;

            double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) + entity.getHeight() + 0.5;
            double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

            Vector2f pos = ProjectionUtil.project(x, y, z);
            if (pos.getX() == Float.MAX_VALUE || pos.getY() == Float.MAX_VALUE) continue;

            final float screenYOffset = 5.7f;
            float posY = pos.getY() - screenYOffset;

            Text baseName = getNormalizedName(entity);


            float currentHp = (int) entity.getHealth();
            ScoreboardHealth sbh = Onetap.getInstance().getModuleStorage().get(ScoreboardHealth.class);
            if (sbh != null && sbh.isEnabled() && entity != null) {
                float scoreboardHp = sbh.getRealHp(entity);
                if (scoreboardHp != -1) {
                    currentHp = scoreboardHp;
                }
            }

            int hpColor;
            if (currentHp > 14) {
                hpColor = 0x55FF55; // Яркий зеленый
            } else if (currentHp > 6) {
                hpColor = 0xFFFF55; // Яркий желтый
            } else {
                hpColor = 0xFF5555; // Яркий красный
            }

            boolean nursultanStyle = style.is("Nursultan");

            MutableText name = baseName.copy();

            ItemStack offHandStack = entity.getOffHandStack();
            if (offHandStack.getItem() == net.minecraft.item.Items.PLAYER_HEAD) {
                name.append(Text.literal(" [").setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                        .append(offHandStack.getName())
                        .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
            }

            name.append(Text.literal(" [").setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                    .append(Text.literal(String.valueOf(currentHp)).setStyle(Style.EMPTY.withColor(hpColor)))
                    .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)));


            float textWidth = nursultanStyle ? font.getWidth(name.getString(), 8.3f) : mc.textRenderer.getWidth(name);
            float paddingX = nursultanStyle ? 3f : 4f;
            float totalWidth = nursultanStyle ? textWidth + (paddingX * 2) - 4 : textWidth + (paddingX * 2) - 3;
            float tagHeight = nursultanStyle ? 12.5f : 11.5f;
            float tagY = nursultanStyle ? posY - 2 : posY - 1.5f;

            float defaultTextWidth = mc.textRenderer.getWidth(name);
            float centerX = pos.getX();
            float bgX = nursultanStyle ? centerX - totalWidth / 2.0f : centerX - (defaultTextWidth / 2.0f) - paddingX;
            float bgY = nursultanStyle ? tagY : tagY;

            DrawUtil.drawRound(bgX, bgY, totalWidth, tagHeight, 0, FriendRepository.isFriend(entity.getNameForScoreboard()) ? ColorProvider.rgba(35, 166, 0, 144) : ColorProvider.rgba(0, 0, 0, 125));

            if (nursultanStyle) {
                boolean isSpeaking = false;

                List<MsdfFont.ColoredGlyph> glyphs = ParseTextUtil.parseTextToColoredGlyphs(entity.getDisplayName());
                if (!glyphs.isEmpty()) {
                    int firstCharColor = glyphs.get(0).color();
                    if ((firstCharColor & 0xFFFFFF) == 0x55FF55) {
                        isSpeaking = true;
                    }
                }
                int colorSpeaking = ColorProvider.rgba(50, 215, 50, 255);
                int colorSilent = ColorProvider.rgba(215, 50, 50, 255);

                int voiceColor = isSpeaking ? colorSpeaking : colorSilent;
                float barWidth = 1.5f;

                DrawUtil.drawRound(bgX, tagY, barWidth, tagHeight, 0.5f, voiceColor);
                DrawUtil.drawRound(bgX + totalWidth - barWidth, tagY, barWidth, tagHeight, 0.5f, voiceColor);

                DrawUtil.drawText(font, name, bgX + paddingX, posY + 0.25f, 8, 255);
            } else {
                MatrixStack matrices = e.getDrawContext().getMatrices();
                matrices.push();
                matrices.translate(centerX - defaultTextWidth / 2.0f, posY + 0.5f, 0);
                e.getDrawContext().drawText(mc.textRenderer, name, 0, 0, -1, false);
                matrices.pop();
            }

            equipmentCache.clear();
            equipmentCache.add(entity.getEquippedStack(EquipmentSlot.HEAD));
            equipmentCache.add(entity.getEquippedStack(EquipmentSlot.CHEST));
            equipmentCache.add(entity.getEquippedStack(EquipmentSlot.LEGS));
            equipmentCache.add(entity.getEquippedStack(EquipmentSlot.FEET));
            equipmentCache.add(entity.getMainHandStack());
            equipmentCache.add(entity.getOffHandStack());
            equipmentCache.removeIf(ItemStack::isEmpty);

            if (!equipmentCache.isEmpty()) {
                float iconSize = 16;
                float spacing = 0;
                float itemsTotalWidth = equipmentCache.size() * iconSize + (equipmentCache.size() - 1) * spacing;
                float startX = pos.getX() - itemsTotalWidth / 2.0f + 13.5f;
                float iconY = posY - 10;

                MatrixStack matrices = e.getDrawContext().getMatrices();

                for (int i = 0; i < equipmentCache.size(); i++) {
                    ItemStack stack = equipmentCache.get(i);
                    float x2 = startX + i * (iconSize + spacing - 2);
                    float scale = 0.7f;
                    float half = -18;

                    matrices.push();
                    matrices.translate(x2 + half, iconY + half, 0);
                    matrices.scale(scale, scale, 1);
                    e.getDrawContext().drawItem(stack, (int) (-half), (int) (-half));
                    e.getDrawContext().drawStackOverlay(mc.textRenderer, stack, (int) (-half), (int) (-half));
                    matrices.pop();
                }
            }
        }

        if (!displayPartyFriends.getValue()) return;

        for (PartyPlayerPos player : PartyApiClient.getCached()) {
            boolean contains = false;
            for (PlayerEntity playerEntity : worldPlayers) {
                if (playerEntity.getNameForScoreboard().equals(player.playerId())) {
                    contains = true;
                    break;
                }
            }
            if (contains) continue;
            double x = player.x();
            double y = player.y() + 3;
            double z = player.z();

            Vector2f pos = ProjectionUtil.project(x, y, z);
            if (pos.getX() == Float.MAX_VALUE || pos.getY() == Float.MAX_VALUE) continue;

            final float screenYOffset = 5.7f;
            float posY = pos.getY() - screenYOffset;

            String name = player.playerId();

            float textWidth = font.getWidth(name, 8.3f);
            float totalWidth = textWidth + 6;
            float bgX = pos.getX() - totalWidth / 2.0f;

            DrawUtil.drawRoundBlur(bgX, posY - 2, totalWidth, 12.5f, 3, ColorProvider.rgba(0, 0, 0, 90), 8f);

            DrawUtil.drawText(font, name, bgX + 3, posY + 0.25f, -1, 8);
        }
    }
    private void renderItemTags(MsdfFont font, float tickDelta, EventHUD e) {
        MsdfFont sfBold = Fonts.SFBOLD.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) + entity.getHeight() + 0.5;
            double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

            Vector2f pos = ProjectionUtil.project(x, y, z);
            if (pos.getX() == Float.MAX_VALUE || pos.getY() == Float.MAX_VALUE) continue;

            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty()) continue;

            int rarityOrdinal = stack.getRarity().ordinal();
            Formatting rarityColor = switch (rarityOrdinal) {
                case 0 -> Formatting.WHITE;
                case 1 -> Formatting.YELLOW;
                case 2 -> Formatting.AQUA;
                case 3 -> Formatting.LIGHT_PURPLE;
                default -> Formatting.WHITE;
            };

            String itemName = stack.getName().getString();
            Text nameText = Text.literal(itemName).setStyle(Style.EMPTY.withColor(rarityColor));
            if (!stack.getName().getSiblings().isEmpty()) nameText = stack.getName();

            Text countComponent = stack.getCount() > 1
                    ? Text.literal(" [").setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                    .append(Text.literal(String.valueOf(stack.getCount())).setStyle(Style.EMPTY.withColor(Formatting.RED)))
                    .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                    : Text.empty();

            Text textComponent = nameText.copy().append(countComponent);
            Text normalized = normalizeSmallCaps(textComponent);

            float textWidth = sfBold.getWidth(normalized.getString(), 8.3f);

            float totalWidth = textWidth + 1;

            float bgX = pos.getX() - (totalWidth / 2.0f);

            DrawUtil.drawRoundBlur(bgX, pos.getY() - 2f, totalWidth, 12.5f, 0, ColorProvider.rgba(0, 0, 0, 144), 8f);
            DrawUtil.drawText(sfBold, normalized, bgX + 2, pos.getY() + 0.5f, 8, 255);
        }
    }
}
