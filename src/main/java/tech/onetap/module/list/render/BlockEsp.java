package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import org.joml.Matrix4f;
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
        chunkCache.clear();
        scanningChunks.clear();
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
            java.nio.file.Path dir = java.nio.file.Paths.get(".options/configs");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve("blockesp.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadBlocks() {
        blocksLoaded = true;
        java.nio.file.Path file = java.nio.file.Paths.get(".options/configs/blockesp.json");
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
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        ensureBlocksLoaded();
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
        rescanAll();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        saveBlocks();
        chunkCache.clear();
        scanningChunks.clear();
    }

    private void rescanAll() {
        chunkCache.clear();
        scanningChunks.clear();
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

    private void scheduleChunkScan(int cx, int cz) {
        long key = chunkKey(cx, cz);
        if (!scanningChunks.add(key)) return;
        if (chunkCache.containsKey(key)) {
            scanningChunks.remove(key);
            return;
        }

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

                BlockPos.Mutable mutable = new BlockPos.Mutable();
                int bottomY = mc.world.getBottomY();
                int topY = bottomY + mc.world.getHeight();

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = bottomY; y < topY; y++) {
                            mutable.set(cx * 16 + x, y, cz * 16 + z);
                            var state = chunk.getBlockState(mutable);
                            if (!state.isAir() && targetBlocks.contains(state.getBlock())) {
                                found.add(mutable.toImmutable());
                            }
                        }
                    }
                }

                if (!found.isEmpty()) {
                    chunkCache.put(key, found);
                }
            } catch (Exception ignored) {
            } finally {
                scanningChunks.remove(key);
            }
        });
    }

    @Subscribe
    public void onTick(EventTick event) {
        if (mc.world == null || mc.player == null || targetBlocks.isEmpty()) return;

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

    @Subscribe
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
