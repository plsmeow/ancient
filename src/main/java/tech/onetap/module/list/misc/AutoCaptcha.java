package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.MapColor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import ru.bareapi.lib.BareAPI;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@ModuleInformation(moduleName = "Auto Captcha", moduleDesc = "Решает капчу через Bare API", moduleCategory = ModuleCategory.MISC)
public class AutoCaptcha extends Module {

    private final ModeSetting apiVersion = new ModeSetting(
            "Версия API", "V1",
            "V1", "V1.1", "V2", "V3"
    );

    private final SliderSetting delay = new SliderSetting("Задержка (мс)", 1000, 300, 5000, 100);

    private final BooleanSetting resize = new BooleanSetting("Ресайз", true);

    private final AtomicBoolean solving = new AtomicBoolean(false);
    private volatile String lastCaptchaHash = "";
    private volatile long lastSolveFinishedAt = 0L;
    private volatile String rawApiKey = "";

    private BareAPI bareApi;

    public AutoCaptcha() {
        loadKey();
    }

    private void loadKey() {
        try {
            Path keyPath = Path.of(".options", "key.txt");
            if (Files.exists(keyPath)) {
                rawApiKey = Files.readString(keyPath).trim();
            }
        } catch (Exception e) {
            logDirect("Не удалось прочитать ключ: " + e.getMessage(), Formatting.RED);
        }
    }

    private String getFullKey() {
        String version = apiVersion.getValue();
        if (version.equals("V1")) {
            return rawApiKey;
        }
        return version + "_" + rawApiKey;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        loadKey();
        if (rawApiKey.isEmpty()) {
            logDirect("Ключ не найден! Положите ключ в .options/key.txt", Formatting.RED);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        solving.set(false);
        bareApi = null;
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        if (solving.get()) return;
        if (rawApiKey.isEmpty()) return;

        if (!trySolveHand()) trySolveWall();
    }

    private BareAPI getOrCreateApi() {
        if (bareApi == null) {
            bareApi = new BareAPI();
            try {
                if (!bareApi.initBareAPI()) {
                    logDirect("Не удалось инициализировать Bare API", Formatting.RED);
                    bareApi = null;
                    return null;
                }
            } catch (Exception e) {
                logDirect("Ошибка инициализации: " + e.getMessage(), Formatting.RED);
                bareApi = null;
                return null;
            }
        }
        bareApi.setKey(getFullKey());
        bareApi.setDelay((int) delay.getValue());
        bareApi.setResize(resize.getValue());
        return bareApi;
    }

    private boolean trySolveHand() {
        ItemStack main = mc.player.getMainHandStack();
        if (!main.isOf(Items.FILLED_MAP)) return false;

        BufferedImage image = getMapImage(main);
        if (image == null) return false;

        String hash = hashImage(image);
        if (isDuplicateCaptcha(hash)) return true;

        logDirect("Капча в руке найдена. Решение...", Formatting.YELLOW);
        startSolving(image, hash);
        return true;
    }

    private boolean trySolveWall() {
        Box box = mc.player.getBoundingBox().expand(8.0);
        List<ItemFrameEntity> frames = mc.world.getEntitiesByClass(
                ItemFrameEntity.class, box,
                f -> f.getHeldItemStack().isOf(Items.FILLED_MAP)
        );
        if (frames.isEmpty()) return false;

        Map<Integer, List<ItemFrameEntity>> rows = frames.stream()
                .collect(Collectors.groupingBy(f -> (int) Math.round(f.getY() * 10)));

        List<Integer> sortedRowKeys = rows.keySet().stream()
                .sorted(Comparator.reverseOrder()).toList();
        if (sortedRowKeys.isEmpty()) return false;

        float yaw = mc.player.getYaw();
        double rad = Math.toRadians(yaw + 90.0);
        final double rightX = -Math.sin(rad);
        final double rightZ = Math.cos(rad);

        for (List<ItemFrameEntity> rowFrames : rows.values()) {
            rowFrames.sort(Comparator.comparingDouble(f -> f.getX() * rightX + f.getZ() * rightZ));
        }

        int numRows = sortedRowKeys.size();
        int numCols = rows.get(sortedRowKeys.get(0)).size();

        BufferedImage combined = new BufferedImage(numCols * 128, numRows * 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();

        for (int y = 0; y < numRows; y++) {
            List<ItemFrameEntity> currentRow = rows.get(sortedRowKeys.get(y));
            for (int x = 0; x < numCols; x++) {
                if (x < currentRow.size()) {
                    BufferedImage part = getMapImage(currentRow.get(x).getHeldItemStack());
                    if (part != null) g.drawImage(part, x * 128, y * 128, null);
                }
            }
        }
        g.dispose();

        String hash = hashImage(combined);
        if (isDuplicateCaptcha(hash)) return true;

        logDirect("Капча на стене: " + numCols + "x" + numRows + ". Решение...", Formatting.YELLOW);
        startSolving(combined, hash);
        return true;
    }

    private void startSolving(BufferedImage image, String captchaHash) {
        if (!solving.compareAndSet(false, true)) return;
        lastCaptchaHash = captchaHash;

        CompletableFuture.runAsync(() -> {
            try {
                BareAPI api = getOrCreateApi();
                if (api == null) {
                    solving.set(false);
                    return;
                }

                String result = api.solveCaptcha(image);

                if (result == null || result.isEmpty() || result.equals("CAPCHA_NO_READY")) {
                    mc.execute(() -> logDirect("Не удалось решить капчу.", Formatting.RED));
                    return;
                }

                mc.execute(() -> {
                    logDirect("Решено: " + result, Formatting.GREEN);
                    if (mc.getNetworkHandler() != null) {
                        try {
                            mc.getNetworkHandler().sendChatMessage(result);
                        } catch (Exception ex) {
                            logDirect("Ошибка отправки: " + ex.getMessage(), Formatting.RED);
                        }
                    }
                });
            } catch (Exception e) {
                mc.execute(() -> logDirect("Ошибка: " + e.getMessage(), Formatting.RED));
            } finally {
                solving.set(false);
                lastSolveFinishedAt = System.currentTimeMillis();
            }
        });
    }

    private BufferedImage getMapImage(ItemStack stack) {
        MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
        if (mapId == null) return null;

        var state = mc.world.getMapState(mapId);
        if (state == null) return null;

        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 16384; i++) {
            int colorIndex = Byte.toUnsignedInt(state.colors[i]);
            img.setRGB(i % 128, i / 128, MapColor.getRenderColor(colorIndex));
        }
        return img;
    }

    private boolean isDuplicateCaptcha(String hash) {
        if (hash == null || hash.isEmpty()) return false;
        if (solving.get() && hash.equals(lastCaptchaHash)) return true;
        long now = System.currentTimeMillis();
        return hash.equals(lastCaptchaHash) && now - lastSolveFinishedAt < 10_000;
    }

    private String hashImage(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
