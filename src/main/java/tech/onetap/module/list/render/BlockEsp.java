package tech.onetap.module.list.render;

import meteordevelopment.orbit.EventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import org.joml.Matrix4f;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.render.providers.ColorProvider;

import java.util.*;
import java.util.concurrent.*;

@ModuleInformation(moduleName = "BlockEsp", moduleDesc = "Подсвечивает указанные блоки", moduleCategory = ModuleCategory.RENDER)
public class BlockEsp extends Module {
    private final SliderSetting range = new SliderSetting("Дальность", 64, 16, 128, 1);

    private final Set<Block> targetBlocks = new LinkedHashSet<>();

    private final Map<Long, List<BlockPos>> chunkCache = new ConcurrentHashMap<>();
    private final Set<Long> scanningChunks = ConcurrentHashMap.newKeySet();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BlockEsp-Scanner");
        t.setDaemon(true);
        return t;
    });

    // Обновления приходят из сетевого потока (netty), применяются на тике клиента
    private final ConcurrentLinkedQueue<ChunkUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();

    private record ChunkUpdate(long chunkKey, BlockPos pos, BlockState state) {}

    private ClientWorld lastWorld;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    private boolean blocksLoaded;

    public boolean addBlock(String name) {
        ensureBlocksLoaded();
        String id = normalizeBlockName(name);
        for (Block block : Registries.BLOCK) {
            if (matchesBlockName(block, id)) {
                if (targetBlocks.add(block)) {
                    logDirect("§a" + name + " §7добавлен в BlockEsp");
                    saveBlocks();
                    rescanAll();
                    return true;
                }
                logDirect("§c" + name + " §7уже в списке");
                return false;
            }
        }
        logDirect("§cБлок §f" + name + " §cне найден");
        return false;
    }

    public boolean removeBlock(String name) {
        ensureBlocksLoaded();
        String id = normalizeBlockName(name);
        Iterator<Block> it = targetBlocks.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (matchesBlockName(block, id)) {
                it.remove();
                logDirect("§a" + name + " §7удалён из BlockEsp");
                saveBlocks();
                rescanAll();
                return true;
            }
        }
        logDirect("§cБлок §f" + name + " §cне найден в списке");
        return false;
    }

    public void clearBlocks() {
        ensureBlocksLoaded();
        targetBlocks.clear();
        rescanAll();
        saveBlocks();
        logDirect("§aСписок BlockEsp очищен");
    }

    public Set<Block> getTargetBlocks() {
        ensureBlocksLoaded();
        return targetBlocks;
    }

    public void saveBlocks() {
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (Block block : targetBlocks) {
            arr.add(Registries.BLOCK.getId(block).toString());
        }
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.add("blocks", arr);
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(".options");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve("blockesp.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes());
        } catch (Exception e) {
            ;
        }
    }

    public void loadBlocks() {
        blocksLoaded = true;
        java.nio.file.Path file = java.nio.file.Paths.get(".options/blockesp.json");
        if (!java.nio.file.Files.exists(file)) return;
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file)) {
            com.google.gson.JsonObject root = new com.google.gson.Gson().fromJson(reader, com.google.gson.JsonObject.class);
            if (root == null || !root.has("blocks")) return;
            com.google.gson.JsonArray arr = root.getAsJsonArray("blocks");
            targetBlocks.clear();
            for (com.google.gson.JsonElement el : arr) {
                String name = el.getAsString();
                for (Block block : Registries.BLOCK) {
                    if (matchesBlockName(block, normalizeBlockName(name))) {
                        targetBlocks.add(block);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            ;
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        ensureBlocksLoaded();
        lastWorld = null;
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
        rescanAll();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        saveBlocks();
        rescanAll();
    }

    private void rescanAll() {
        chunkCache.clear();
        scanningChunks.clear();
        pendingUpdates.clear();
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
    }

    private void ensureBlocksLoaded() {
        if (!blocksLoaded) loadBlocks();
    }

    private String normalizeBlockName(String name) {
        return name.toLowerCase(Locale.US);
    }

    private boolean matchesBlockName(Block block, String name) {
        var id = Registries.BLOCK.getId(block);
        return id.toString().equals(name) || id.getPath().equals(name);
    }

    private long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32);
    }

    @EventHandler
    public void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.RECEIVE || targetBlocks.isEmpty()) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ChunkDataS2CPacket p) {
            // Чанк полностью (заг)ружен - нужен свежий полный скан
            pendingUpdates.add(new ChunkUpdate(chunkKey(p.getChunkX(), p.getChunkZ()), null, null));
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket p) {
            // Мультиблочное обновление секции
            p.visitUpdates((pos, state) ->
                    pendingUpdates.add(new ChunkUpdate(chunkKey(pos.getX() >> 4, pos.getZ() >> 4), pos.toImmutable(), state)));
        } else if (packet instanceof BlockUpdateS2CPacket p) {
            BlockPos pos = p.getPos();
            pendingUpdates.add(new ChunkUpdate(chunkKey(pos.getX() >> 4, pos.getZ() >> 4), pos.toImmutable(), p.getState()));
        } else if (packet instanceof UnloadChunkS2CPacket p) {
            pendingUpdates.add(new ChunkUpdate(p.pos().toLong(), null, null));
        }
    }

    private void processPendingUpdates() {
        ChunkUpdate update;
        while ((update = pendingUpdates.poll()) != null) {
            if (update.pos() == null && update.state() == null) {
                // RESCAN или UNLOAD - различаем по наличию чанка в кэше после удаления
                List<BlockPos> removed = chunkCache.remove(update.chunkKey());
                scanningChunks.remove(update.chunkKey());

                int cx = (int) (update.chunkKey() & 0xFFFFFFFFL);
                int cz = (int) (update.chunkKey() >>> 32);

                if (removed != null) {
                    // Чанк был закэширован - значит это его полная перезагрузка, сканируем заново
                    scheduleChunkScan(cx, cz);
                }
                // Если removed == null - чанк не был в кэше (выгрузка), ничего не делаем
            } else {
                applyBlockUpdate(update);
            }
        }
    }

    private void applyBlockUpdate(ChunkUpdate update) {
        List<BlockPos> current = chunkCache.get(update.chunkKey());
        if (current == null) return; // Чанк ещё не отсканирован - сканер возьмёт актуальное состояние

        boolean matches = update.state() != null && targetBlocks.contains(update.state().getBlock());
        boolean contained = current.contains(update.pos());
        if (contained == matches) return; // Ничего не изменилось

        // Заменяем список целиком - рендер читает списки без блокировок
        List<BlockPos> next = new ArrayList<>(current);
        if (contained) next.remove(update.pos());
        if (matches) next.add(update.pos());
        chunkCache.put(update.chunkKey(), next);
    }

    private void scheduleChunkScan(int cx, int cz) {
        long key = chunkKey(cx, cz);
        if (!scanningChunks.add(key)) return;

        worker.submit(() -> {
            try {
                if (!isEnabled() || mc.world == null || mc.player == null) {
                    scanningChunks.remove(key);
                    return;
                }

                if (!mc.world.isChunkLoaded(new BlockPos(cx * 16, 0, cz * 16))) {
                    scanningChunks.remove(key);
                    return;
                }

                Chunk chunk = mc.world.getChunk(cx, cz);
                List<BlockPos> found = new ArrayList<>();

                // Итерирует по секциям, пропуская пустые - сильно быстрее полного перебора
                chunk.forEachBlockMatchingPredicate(
                        state -> targetBlocks.contains(state.getBlock()),
                        (pos, state) -> found.add(pos.toImmutable()));

                // Сохраняем даже пустой результат, чтобы чанк не пересканировался повторно
                chunkCache.put(key, found);
            } catch (Exception ignored) {
            } finally {
                scanningChunks.remove(key);
            }
        });
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.world == null || mc.player == null || targetBlocks.isEmpty()) return;

        // Смена мира - сбрасываем всё
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            rescanAll();
        }

        processPendingUpdates();

        int chunkX = mc.player.getBlockX() >> 4;
        int chunkZ = mc.player.getBlockZ() >> 4;

        if (chunkX == lastPlayerChunkX && chunkZ == lastPlayerChunkZ) return;
        lastPlayerChunkX = chunkX;
        lastPlayerChunkZ = chunkZ;

        int viewDist = (int) Math.ceil(range.getValue() / 16.0);

        for (int cx = chunkX - viewDist; cx <= chunkX + viewDist; cx++) {
            for (int cz = chunkZ - viewDist; cz <= chunkZ + viewDist; cz++) {
                if (!chunkCache.containsKey(chunkKey(cx, cz))) {
                    scheduleChunkScan(cx, cz);
                }
            }
        }

        Iterator<Map.Entry<Long, List<BlockPos>>> it = chunkCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, List<BlockPos>> entry = it.next();
            long packed = entry.getKey();
            int cx = (int) (packed & 0xFFFFFFFFL);
            int cz = (int) (packed >>> 32);
            if (Math.abs(cx - chunkX) > viewDist + 1 || Math.abs(cz - chunkZ) > viewDist + 1) {
                it.remove();
            }
        }
    }

    @EventHandler
    public void onWorldRender(EventWorldRender event) {
        if (chunkCache.isEmpty() || mc.player == null) return;

        MatrixStack matrices = event.getMatrixStack();
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();

        int color = ColorProvider.getThemeColor();
        float cr = ((color >> 16) & 0xFF) / 255f;
        float cg = ((color >> 8) & 0xFF) / 255f;
        float cb = (color & 0xFF) / 255f;

        for (List<BlockPos> positions : chunkCache.values()) {
            for (BlockPos pos : positions) {
                drawBox(matrices, camPos, pos, cr, cg, cb);
            }
        }
    }

    private void drawBox(MatrixStack matrices, Vec3d camPos, BlockPos pos, float cr, float cg, float cb) {
        double minX = pos.getX() - camPos.x;
        double minY = pos.getY() - camPos.y;
        double minZ = pos.getZ() - camPos.z;
        double maxX = minX + 1;
        double maxY = minY + 1;
        double maxZ = minZ + 1;

        drawFilled(matrices, minX, minY, minZ, maxX, maxY, maxZ, cr, cg, cb);
        drawOutline(matrices, minX, minY, minZ, maxX, maxY, maxZ, cr, cg, cb);
    }

    private void drawOutline(MatrixStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float r, float g, float b) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(2.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, 1f);

        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, 1f);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, 1f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private void drawFilled(MatrixStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float r, float g, float b) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float a = 130 / 500f;

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
