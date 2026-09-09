package tech.onetap.module.list.render;

import meteordevelopment.orbit.EventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
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
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.misc.ScoreboardHealth;

import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.staff.StaffManager;
import tech.onetap.util.target.TargetRepository;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.Draw2D;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.font.DeltaFonts;
import tech.onetap.util.render.font.Font;
import tech.onetap.util.render.math.ProjectionUtil;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.msdf.MsdfFont;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.replace.ReplaceUtil;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@ModuleInformation(moduleName = "Tags", moduleDesc = "Теги над игроками", moduleCategory = ModuleCategory.RENDER)
public class Tags extends Module {

    private final ModeListSetting entityTypes = new ModeListSetting("Типы",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Предметы", true)
    );

    private final ModeSetting tagMode = new ModeSetting("Режим", "Default", "Default", "Modern");

    private static final float MODERN_RADIUS = 3.0f;
    private static final float MODERN_HEIGHT = 12.5f;
    private static final float MODERN_HEAD_SIZE = 8.0f;
    private static final float MODERN_HEAD_RADIUS = 2.0f;
    private static final float MODERN_FONT_SIZE = 7.0f;
    private static final float MODERN_TEXT_X = 19.0f;
    private static final float MODERN_PADDING = 5.0f;
    private static final float MODERN_VALUE_GAP = 8.0f;

    private final BooleanSetting totemCounter = new BooleanSetting("Счетчик тотемов", false);
    private static final Pattern STAFF_PREFIX_PATTERN = Pattern.compile(
            ".*(ꔷ|ꔳ|ꔩ|ꔥ|ꔡ|ꔗ|ꔓ|\\bmod\\b|\\badm\\b|\\bhelp\\b|\\bwne\\b|модер|мод|хелп|помощ|помо|админ|адм|владел|владе|отриц|отри|\\btaf\\b|\\bcurat\\b|куратор|курато|\\bdev\\b|разраб|раз|\\bsupp\\b|\\bꜱupp\\b|саппорт|сапп|\\bder\\b|\\byt\\b|\\[yt\\]|ютуб|стажер|сотрудник).*");


    private final Map<UUID, Text> normalizedNames = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> totemPops = new ConcurrentHashMap<>();
    private int clearCacheTicker = 0;

    private final List<ItemStack> equipmentCache = new ArrayList<>();

    public boolean hidesPlayerNametags() {
        return entityTypes.isEnabled("Игроки");
    }

    public static Text normalizeSmallCaps(Text text) {
        String[] from = {"ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ", "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "ʏ", "ᴢ", "◆", "┃ "};
        String[] to = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "Y", "Z", "●", ""};
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

    private boolean isStaffTag(PlayerEntity entity) {
        if (StaffManager.isStaff(entity.getGameProfile().getName())) return true;

        if (mc.getNetworkHandler() == null) return false;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        if (entry == null || entry.getDisplayName() == null) return false;

        String displayName = entry.getDisplayName().getString().toLowerCase(Locale.ROOT);
        return STAFF_PREFIX_PATTERN.matcher(displayName).matches();
    }

    private int statusOutlineColor(PlayerEntity entity) {
        if (isStaffTag(entity)) return ColorProvider.rgba(255, 235, 0, 255);
        if (TargetRepository.isTarget(entity.getNameForScoreboard())) return ColorProvider.rgba(255, 25, 25, 255);
        if (FriendRepository.isFriend(entity.getNameForScoreboard())) return ColorProvider.rgba(45, 235, 15, 255);
        return 0;
    }

    @EventHandler
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

    @EventHandler
    private void onPacket(EventPacket event) {
        if (mc.world == null || event.getType() != EventPacket.Type.RECEIVE) return;
        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet)) return;

        if (packet.getStatus() == 35) { // Активация тотема
            if (packet.getEntity(mc.world) instanceof PlayerEntity player) {
                totemPops.merge(player.getUuid(), 1, Integer::sum);
            }
        } else if (packet.getStatus() == 3) { // Смерть сущности
            if (packet.getEntity(mc.world) instanceof PlayerEntity player) {
                totemPops.remove(player.getUuid());
            }
        }
    }

    private void renderPlayerTags(MsdfFont font, float tickDelta, EventHUD e) {
        boolean modern = tagMode.is("Modern");
        List<AbstractClientPlayerEntity> worldPlayers = mc.world.getPlayers();

        for (AbstractClientPlayerEntity entity : worldPlayers) {
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


            if (modern) {
                renderModernTag(entity, e.getDrawContext().getMatrices(), pos.getX(), posY, baseName, currentHp, hpColor);
            } else {
                renderDefaultTag(entity, font, pos.getX(), posY, baseName, currentHp, hpColor);
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

    }

    private void renderDefaultTag(PlayerEntity entity, MsdfFont font, float centerX, float posY, Text baseName, float currentHp, int hpColor) {
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

        if (totemCounter.getValue()) {
            int pops = totemPops.getOrDefault(entity.getUuid(), 0);
            if (pops > 0) {
                name.append(Text.literal(" [").setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                        .append(Text.literal("-" + pops).setStyle(Style.EMPTY.withColor(0xFF5555)))
                        .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
            }
        }

        float textWidth = font.getWidth(name.getString(), 8.3f);
        float paddingX = 3f;
        float totalWidth = textWidth + (paddingX * 2) - 4;
        float tagHeight = 12.5f;
        float tagY = posY - 2;

        float bgX = centerX - totalWidth / 2.0f;
        float bgY = tagY;

        DrawUtil.drawRound(bgX, bgY, totalWidth, tagHeight, 2, ColorProvider.rgba(0, 0, 0, 125));

        int outlineColor = statusOutlineColor(entity);
        if (outlineColor != 0) {
            Builder.border()
                    .size(new SizeState(totalWidth + 1.5f, tagHeight + 1.25f))
                    .radius(new QuadRadiusState(2f))
                    .color(new QuadColorState(outlineColor))
                    .thickness(1f)
                    .smoothness(1f, 0.5f)
                    .build()
                    .render(bgX - 0.5f, bgY - 0.5f);
        }

        DrawUtil.drawText(font, name, bgX + paddingX, posY + 0.25f, 8, 255);
    }

    private void renderModernTag(AbstractClientPlayerEntity entity, MatrixStack matrices, float centerX, float posY, Text baseName, float currentHp, int hpColor) {
        Font font = DeltaFonts.GT_REGULAR.get();
        Draw2D draw = Delta2DHolder.get();

        MutableText name = baseName.copy();

        ItemStack offHandStack = entity.getOffHandStack();
        if (offHandStack.getItem() == net.minecraft.item.Items.PLAYER_HEAD) {
            name.append(Text.literal(" [").setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                    .append(offHandStack.getName())
                    .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
        }

        MutableText value = Text.literal(String.valueOf((int) currentHp)).setStyle(Style.EMPTY.withColor(hpColor));
        if (totemCounter.getValue()) {
            int pops = totemPops.getOrDefault(entity.getUuid(), 0);
            if (pops > 0) {
                value.append(Text.literal(" -" + pops).setStyle(Style.EMPTY.withColor(0xFF5555)));
            }
        }

        float nameWidth = font.getWidth(name, MODERN_FONT_SIZE);
        float valueWidth = font.getWidth(value, MODERN_FONT_SIZE);
        float totalWidth = MODERN_TEXT_X + nameWidth + MODERN_VALUE_GAP + valueWidth + MODERN_PADDING;
        float tagY = posY - 2.0f;
        float bgX = centerX - totalWidth / 2.0f;

        int background = ColorUtil.applyAlphaToColor(
                ColorUtil.lerpColor(DeltaThemeInfo.BACKGROUND_HUD.resolve(), DeltaThemeInfo.PRIMARY.resolve(),
                        DeltaThemeInfo.PRIMARY.alphaFloat() / 6.0f),
                DeltaThemeInfo.BACKGROUND_HUD.alphaFloat());
        draw.drawShadowBlur(matrices, bgX, tagY, totalWidth, MODERN_HEIGHT, MODERN_RADIUS, background, 1.0f);

        int outlineColor = statusOutlineColor(entity);
        if (outlineColor != 0) {
            draw.drawOutline(matrices, bgX, tagY, totalWidth, MODERN_HEIGHT, MODERN_RADIUS, 1.0f, outlineColor);
        }

        float headY = tagY + (MODERN_HEIGHT - MODERN_HEAD_SIZE) / 2.0f;
        try {
            draw.drawSkinHead(matrices, entity.getSkinTextures().texture(),
                    bgX + MODERN_PADDING, headY, MODERN_HEAD_SIZE, MODERN_HEAD_SIZE, MODERN_HEAD_RADIUS, 1.0f);
        } catch (Exception ignored) {
            DeltaFonts.ICONS.get().drawText(matrices, "y",
                    bgX + MODERN_PADDING + (MODERN_HEAD_SIZE - 8.0f) / 2.0f,
                    tagY + (MODERN_HEIGHT - 8.0f) / 2.0f, 8.0f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 1.0f));
        }

        draw.drawRounded(matrices, bgX + 15.0f, tagY + ((MODERN_HEIGHT - MODERN_HEIGHT / 2.0f) / 2.0f),
                0.75f, MODERN_HEIGHT / 2.0f, 0.0f,
                ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(200, 200, 200, 255), 0.5f));

        float textY = (tagY + (MODERN_HEIGHT - font.getHeight(MODERN_FONT_SIZE)) / 2.0f) - 0.5f;
        font.drawText(matrices, name, bgX + MODERN_TEXT_X, textY, MODERN_FONT_SIZE);
        font.drawText(matrices, value, bgX + totalWidth - MODERN_PADDING - valueWidth, textY, MODERN_FONT_SIZE);
    }
    private void renderItemTags(MsdfFont font, float tickDelta, EventHUD e) {
        boolean modern = tagMode.is("Modern");
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

            if (modern) {
                renderModernItemTag(e, stack, nameText, pos.getX(), pos.getY());
            } else {
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

    private void renderModernItemTag(EventHUD e, ItemStack stack, Text nameText, float centerX, float posY) {
        Font font = DeltaFonts.GT_REGULAR.get();
        Draw2D draw = Delta2DHolder.get();
        MatrixStack matrices = e.getDrawContext().getMatrices();

        Text name = normalizeSmallCaps(nameText.copy());
        String countText = stack.getCount() > 1 ? String.valueOf(stack.getCount()) : null;

        float nameWidth = font.getWidth(name, MODERN_FONT_SIZE);
        float countWidth = countText != null ? font.getWidth(countText, MODERN_FONT_SIZE) : 0.0f;
        float totalWidth = MODERN_TEXT_X + nameWidth + MODERN_PADDING
                + (countText != null ? MODERN_VALUE_GAP + countWidth : 0.0f);
        float tagY = posY - 2.0f;
        float bgX = centerX - totalWidth / 2.0f;

        int background = ColorUtil.applyAlphaToColor(
                ColorUtil.lerpColor(DeltaThemeInfo.BACKGROUND_HUD.resolve(), DeltaThemeInfo.PRIMARY.resolve(),
                        DeltaThemeInfo.PRIMARY.alphaFloat() / 6.0f),
                DeltaThemeInfo.BACKGROUND_HUD.alphaFloat());
        draw.drawShadowBlur(matrices, bgX, tagY, totalWidth, MODERN_HEIGHT, MODERN_RADIUS, background, 1.0f);

        matrices.push();
        matrices.translate(bgX + MODERN_PADDING, tagY + (MODERN_HEIGHT - MODERN_HEAD_SIZE) / 2.0f, 0.0f);
        float itemScale = MODERN_HEAD_SIZE / 16.0f;
        matrices.scale(itemScale, itemScale, 1.0f);
        e.getDrawContext().drawItem(stack, 0, 0);
        matrices.pop();

        draw.drawRounded(matrices, bgX + 15.0f, tagY + ((MODERN_HEIGHT - MODERN_HEIGHT / 2.0f) / 2.0f),
                0.75f, MODERN_HEIGHT / 2.0f, 0.0f,
                ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(200, 200, 200, 255), 0.5f));

        float textY = (tagY + (MODERN_HEIGHT - font.getHeight(MODERN_FONT_SIZE)) / 2.0f) - 0.5f;
        font.drawText(matrices, name, bgX + MODERN_TEXT_X, textY, MODERN_FONT_SIZE);
        if (countText != null) {
            font.drawText(matrices, countText, bgX + totalWidth - MODERN_PADDING - countWidth, textY, MODERN_FONT_SIZE,
                    ColorUtil.rgb(255, 85, 85));
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        totemPops.clear();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        totemPops.clear();
    }
}
