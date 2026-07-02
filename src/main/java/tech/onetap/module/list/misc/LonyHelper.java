package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.util.gps.GpsRenderer;
import tech.onetap.util.player.other.InventoryUtil;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInformation(moduleName = "Lony Helper", moduleDesc = "Помощник для LonyGrief", moduleCategory = ModuleCategory.MISC)
public class LonyHelper extends Module {

    private final BindSetting bindTrap = new BindSetting("Трапка", -1);
    private final BindSetting bindDefaultLivalka = new BindSetting("Ливалка", -1);
    private final BooleanSetting prefixesOnPvP = new BooleanSetting("Префиксы при /pvp", true);
    private final BooleanSetting autoGps = new BooleanSetting("Авто гпс при /rtp near", true);
    private final BooleanSetting autoPvP = new BooleanSetting("Авто пвп", true);
    public final BooleanSetting fireworkFix = new BooleanSetting("Фикс ложного феерверка", true);
    private final ModeListSetting autoPvPWhen = new ModeListSetting("Авто пвп если донат",
            new BooleanSetting("Игрок", true),
            new BooleanSetting("Легенда", true),
            new BooleanSetting("Феникс", true),
            new BooleanSetting("Император", true),
            new BooleanSetting("Правитель", true),
            new BooleanSetting("Повелитель", true),
            new BooleanSetting("Д.Админ", true),
            new BooleanSetting("Оверлорд", true),
            new BooleanSetting("Этернити", true)
    );

    private boolean throwTrap;
    private boolean throwLivalk;

    @Subscribe
    public void onKey(EventKeyInput e) {
        if (mc.currentScreen != null || e.getAction() == 0 || e.getAction() == 2) return;
        if (e.getKey() == bindTrap.getValue()) {
            throwTrap = true;
        }
        if (e.getKey() == bindDefaultLivalka.getValue()) {
            throwLivalk = true;
        }
    }

    @Subscribe
    public void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (throwTrap) {
            InventoryUtil.swapAndUseHvH(Items.CRYING_OBSIDIAN);
            throwTrap = false;
        }

        if (throwLivalk) {
            int hotbarSlotClay = InventoryUtil.searchItemHotbar(Items.CLAY_BALL);
            int inventorySlotClay = InventoryUtil.searchItem(Items.CLAY_BALL);

            if (hotbarSlotClay != -1 && !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getInventory().getStack(hotbarSlotClay))
                    || inventorySlotClay != -1 && !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getInventory().getStack(inventorySlotClay))) {
                InventoryUtil.swapAndUseHvH(Items.CLAY_BALL);
            } else {
                int hotbarSlotMagma = InventoryUtil.searchItemHotbar(Items.MAGMA_CREAM);
                int inventorySlotMagma = InventoryUtil.searchItem(Items.MAGMA_CREAM);

                if (hotbarSlotMagma != -1 && !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getInventory().getStack(hotbarSlotMagma))
                        || inventorySlotMagma != -1 && !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getInventory().getStack(inventorySlotMagma))) {
                    InventoryUtil.swapAndUseHvH(Items.MAGMA_CREAM);
                }
            }
            throwLivalk = false;
        }
    }

    private void throwItem(net.minecraft.item.Item item) {
        int slot = InventoryUtil.searchItem(item);
        if (slot == -1) return;

        if (slot < 9) {
            int prevSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = slot;
            mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);
            mc.player.getInventory().selectedSlot = prevSlot;
        } else {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
            mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.OFF_HAND);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
        }
    }

    @Subscribe
    private void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;
        if (e.getType() != EventPacket.Type.RECEIVE) return;
        if (!(e.getPacket() instanceof GameMessageS2CPacket p)) return;

        String text = p.content().getString();

        Pattern PATTERN = Pattern.compile("Его координаты:\\s*(-?\\d+)\\.\\s*(-?\\d+)\\.\\s*(-?\\d+)");
        Matcher matcher = PATTERN.matcher(text);

        if (text.contains("Вы успешно телепортировались рядом с игроком") && matcher.find() && autoGps.getValue()) {
            double x = Double.parseDouble(matcher.group(1));
            double z = Double.parseDouble(matcher.group(3));
            GpsRenderer.get().setTarget(x, z);
            GpsRenderer.get().setEnabled(true);
        }

        Text original = p.content();
        String raw = original.getString();
        final String prefixKey = "ʟᴏɴʏɢʀɪᴇꜰ › Игрок ";
        final String suffixKey = " ищет себе соперника!";

        if (autoPvP.getValue()) {
            if (mc.currentScreen instanceof GenericContainerScreen screen) {
                if (screen.getTitle().getString().contains("Поиск поединка")) {
                    if (text.contains("ʟᴏɴʏɢʀɪᴇꜰ › Игрок ") && text.contains("ищет себе соперника!")) {
                        String playerName = raw.substring(prefixKey.length(), raw.indexOf(suffixKey)).replaceAll("^\\s+", "");
                        if (!playerName.isEmpty()) {
                            Text prefix = getPrefixTextFor(playerName);
                            if (!(prefix == null || prefix.getString().isEmpty())) {
                                Text replaced = insertPrefixInto(original, prefix, playerName);
                                if (replaced.getString().contains("ᴘʟᴀʏᴇʀ") && autoPvPWhen.isEnabled("Игрок")
                                        || replaced.getString().contains("ʟᴇɢᴇɴᴅᴀ") && autoPvPWhen.isEnabled("Легенда")
                                        || replaced.getString().contains("ᴘʜᴏᴇɴɪx") && autoPvPWhen.isEnabled("Феникс")
                                        || replaced.getString().contains("ɪᴍᴘᴇʀᴀᴛᴏʀ") && autoPvPWhen.isEnabled("Император")
                                        || replaced.getString().contains("ᴘʀᴀᴠɪᴛᴇʟ") && autoPvPWhen.isEnabled("Правитель")
                                        || replaced.getString().contains("ᴘᴏᴠᴇʟɪᴛᴇʟ") && autoPvPWhen.isEnabled("Повелитель")
                                        || replaced.getString().contains("ᴅ.ᴀᴅᴍɪɴ") && autoPvPWhen.isEnabled("Д.Админ")
                                        || replaced.getString().contains("ᴏᴠᴇʀʟᴏʀᴅ") && autoPvPWhen.isEnabled("Оверлорд")
                                        || replaced.getString().contains("ᴇᴛᴇʀɴɪᴛʏ") && autoPvPWhen.isEnabled("Этернити")) {
                                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 20, 0, SlotActionType.PICKUP, mc.player);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!raw.startsWith(prefixKey) || !raw.contains(suffixKey) || !prefixesOnPvP.getValue()) return;
        String playerName = raw.substring(prefixKey.length(), raw.indexOf(suffixKey)).replaceAll("^\\s+", "");
        if (playerName.isEmpty()) return;
        Text prefix = getPrefixTextFor(playerName);
        if (prefix == null || prefix.getString().isEmpty()) return;
        Text replaced = insertPrefixInto(original, prefix, playerName);
        mc.inGameHud.getChatHud().addMessage(replaced);
        e.cancelEvent();
    }


    private Text getPrefixTextFor(String playerName) {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return Text.empty();

        for (PlayerListEntry entry : handler.getPlayerList()) {
            if (!entry.getProfile().getName().equalsIgnoreCase(playerName)) continue;
            Text displayName = entry.getDisplayName();
            if (displayName == null) return Text.empty();

            String dnRaw = displayName.getString();
            int pos = indexOfIgnoreCase(dnRaw, playerName);
            if (pos == -1) return Text.empty();

            MutableText prefix = Text.empty();
            int[] count = new int[]{0};
            AtomicBoolean done = new AtomicBoolean(false);
            appendUntilPrefix(prefix, displayName, pos, count, done);
            return prefix;
        }
        return Text.empty();
    }

    private static int indexOfIgnoreCase(String hay, String needle) {
        return hay.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private void appendUntilPrefix(MutableText result, Text current, int stopPos, int[] count, AtomicBoolean done) {
        if (done.get()) return;
        TextContent content = current.getContent();
        if (content instanceof PlainTextContent.Literal literal) {
            String s = literal.string();
            int len = s.length();
            if (count[0] + len <= stopPos) {
                result.append(Text.literal(s).setStyle(current.getStyle()));
                count[0] += len;
            } else {
                int cut = Math.max(0, stopPos - count[0]);
                if (cut > 0) result.append(Text.literal(s.substring(0, cut)).setStyle(current.getStyle()));
                done.set(true);
                return;
            }
        }
        for (Text sib : current.getSiblings()) {
            appendUntilPrefix(result, sib, stopPos, count, done);
            if (done.get()) return;
        }
    }

    private Text insertPrefixInto(Text original, Text prefix, String playerName) {
        String raw = original.getString();
        int targetPos = indexOfIgnoreCase(raw, playerName);
        if (targetPos == -1) return original;

        MutableText result = Text.empty();
        int[] count = new int[]{0};
        AtomicInteger toSkip = new AtomicInteger(playerName.length());
        AtomicBoolean inserted = new AtomicBoolean(false);
        appendWithInsert(result, original, prefix, playerName, targetPos, count, toSkip, inserted);
        return result;
    }

    private void appendWithInsert(MutableText result, Text current, Text prefix, String playerName,
                                  int targetPos, int[] count, AtomicInteger toSkip, AtomicBoolean inserted) {
        TextContent content = current.getContent();
        if (content instanceof PlainTextContent.Literal literal) {
            String s = literal.string();
            int len = s.length();
            if (!inserted.get()) {
                if (count[0] + len < targetPos) {
                    result.append(Text.literal(s).setStyle(current.getStyle()));
                } else {
                    int localIndex = Math.max(0, targetPos - count[0]);
                    String before = s.substring(0, localIndex);
                    String after = s.substring(localIndex);
                    if (after.startsWith(" ")) {
                        after = after.substring(1);
                    }
                    if (!before.isEmpty()) result.append(Text.literal(before).setStyle(current.getStyle()));
                    result.append(prefix);
                    if (!after.isEmpty()) result.append(Text.literal(after).setStyle(current.getStyle()));
                    inserted.set(true);
                }
            } else {
                result.append(Text.literal(s).setStyle(current.getStyle()));
            }
            count[0] += len;
        }
        for (Text sib : current.getSiblings()) {
            appendWithInsert(result, sib, prefix, playerName, targetPos, count, null, inserted);
        }
    }


    private int extractPower(String s) {
        s = s.replaceAll("\\s+", "");
        if (s.contains("⁰")) return 0;
        if (s.contains("¹")) return 1;
        if (s.contains("²")) return 2;
        if (s.contains("³")) return 3;
        if (s.contains("⁴")) return 4;
        if (s.contains("⁵")) return 5;
        if (s.contains("⁶")) return 6;
        if (s.contains("⁷")) return 7;
        if (s.contains("⁸")) return 8;
        if (s.contains("⁹")) return 9;
        if (s.matches(".*\\^(\\d+).*")) {
            return Integer.parseInt(s.replaceAll(".*\\^(\\d+).*", "$1"));
        }
        return 1;
    }

    private double extractValue(String s) {
        s = s.replaceAll(",", ".");
        String[] parts = s.split("=");
        if (parts.length < 2) return 0;
        return Double.parseDouble(parts[1].replaceAll("[^0-9.\\-]", ""));
    }

    private double evalMathExpression(String expr) {
        expr = expr.replaceAll(",", ".").replaceAll("\\s+", "");
        expr = expr.replace("⁰", "^0").replace("¹", "^1").replace("²", "^2")
                .replace("³", "^3").replace("⁴", "^4").replace("⁵", "^5")
                .replace("⁶", "^6").replace("⁷", "^7").replace("⁸", "^8").replace("⁹", "^9");

        try {
            String finalExpr = expr;
            return new Object() {
                int pos = -1, ch;

                void nextChar() {
                    ch = (++pos < finalExpr.length()) ? finalExpr.charAt(pos) : -1;
                }

                boolean eat(int charToEat) {
                    while (ch == ' ') nextChar();
                    if (ch == charToEat) {
                        nextChar();
                        return true;
                    }
                    return false;
                }

                double parse() {
                    nextChar();
                    double x = parseExpression();
                    if (pos < finalExpr.length()) throw new RuntimeException("Неожиданный символ: " + (char) ch);
                    return x;
                }

                double parseExpression() {
                    double x = parseTerm();
                    for (; ; ) {
                        if (eat('+')) x += parseTerm();
                        else if (eat('-')) x -= parseTerm();
                        else return x;
                    }
                }

                double parseTerm() {
                    double x = parseFactor();
                    for (; ; ) {
                        if (eat('*')) x *= parseFactor();
                        else if (eat('/')) x /= parseFactor();
                        else return x;
                    }
                }

                double parseFactor() {
                    if (eat('+')) return parseFactor();
                    if (eat('-')) return -parseFactor();

                    double x;
                    int startPos = this.pos;
                    if (eat('(')) {
                        x = parseExpression();
                        eat(')');
                    } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = Double.parseDouble(finalExpr.substring(startPos, this.pos));
                    } else if (Character.isLetter(ch)) {
                        while (Character.isLetter(ch)) nextChar();
                        String func = finalExpr.substring(startPos, this.pos);
                        x = parseFactor();
                        switch (func.toLowerCase()) {
                            case "sin" -> x = Math.sin(Math.toRadians(x));
                            case "cos" -> x = Math.cos(Math.toRadians(x));
                            case "tan" -> x = Math.tan(Math.toRadians(x));
                            case "ln" -> x = Math.log(x);
                            case "log" -> x = Math.log10(x);
                            default -> throw new RuntimeException("Неизвестная функция: " + func);
                        }
                    } else {
                        throw new RuntimeException("Неожиданное: " + (char) ch);
                    }

                    if (eat('°')) { }
                    if (eat('^')) x = Math.pow(x, parseFactor());
                    return x;
                }
            }.parse();
        } catch (Exception e) {
            System.out.println("[LonyHelper] Ошибка evalMathExpression: " + e.getMessage());
            return 0;
        }
    }
}
