package meow.ancient.ui.celestial;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LegacyHudShadow {
    private static final Map<Integer, CachedMask> CACHE = new HashMap<>();

    private LegacyHudShadow() {
    }

    static Batch batch() {
        return new Batch();
    }

    static final class Batch {
        private final List<ShadowQuad> quads = new ArrayList<>();
        private CachedMask lastMask;

        void add(
                float x, float y, float width, float height,
                int blurRadius, int color
        ) {
            if (width <= 0.0f || height <= 0.0f || blurRadius <= 0
                    || (color >>> 24) == 0) {
                return;
            }
            double expandedWidth = (double) width + blurRadius * 2.0;
            double expandedHeight = (double) height + blurRadius * 2.0;
            int pixelWidth = (int) expandedWidth;
            int pixelHeight = (int) expandedHeight;
            if (pixelWidth <= 0 || pixelHeight <= 0) return;

            lastMask = cachedMask(expandedWidth, expandedHeight, blurRadius,
                    pixelWidth, pixelHeight);
            quads.add(new ShadowQuad(
                    x - blurRadius - 0.25f,
                    y - blurRadius + 0.25f,
                    pixelWidth, pixelHeight, color, lastMask));
        }

        void submit(DrawContext context) {
            if (quads.isEmpty()) return;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);
            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

            for (ShadowQuad quad : quads) {
                if (quad.mask == null) continue;
                RenderSystem.setShaderTexture(0, quad.mask.id);
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                buffer.vertex(matrix, quad.x, quad.y, 0.0f).texture(0.0f, 0.0f).color(quad.color);
                buffer.vertex(matrix, quad.x, quad.y + quad.height, 0.0f).texture(0.0f, 1.0f).color(quad.color);
                buffer.vertex(matrix, quad.x + quad.width, quad.y + quad.height, 0.0f).texture(1.0f, 1.0f).color(quad.color);
                buffer.vertex(matrix, quad.x + quad.width, quad.y, 0.0f).texture(1.0f, 0.0f).color(quad.color);
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
            RenderSystem.setShaderTexture(0, 0);
        }
    }

    private static CachedMask cachedMask(
            double expandedWidth, double expandedHeight, int radius,
            int pixelWidth, int pixelHeight
    ) {
        double legacyKeyValue = expandedWidth * expandedHeight
                + expandedWidth * radius + radius;
        int legacyKey = String.valueOf(legacyKeyValue).hashCode();
        CachedMask cached = CACHE.get(legacyKey);
        if (cached != null) return cached;

        int[] firstPass = new int[pixelWidth * pixelHeight];
        int innerWidth = pixelWidth - radius * 2;
        int innerHeight = pixelHeight - radius * 2;
        if (innerWidth > 0 && innerHeight > 0) {
            for (int y = radius; y < radius + innerHeight; y++) {
                int offset = y * pixelWidth;
                for (int x = radius; x < radius + innerWidth; x++) {
                    firstPass[offset + x] = 255;
                }
            }
        }

        float[] kernel = makeKernel(radius);
        int[] transposed = new int[firstPass.length];
        convolveAndTranspose(kernel, firstPass, transposed,
                pixelWidth, pixelHeight, null);
        boolean[] nonZeroRaw = new boolean[firstPass.length];
        convolveAndTranspose(kernel, transposed, firstPass,
                pixelHeight, pixelWidth, nonZeroRaw);

        NativeImage image = new NativeImage(pixelWidth, pixelHeight, false);
        for (int y = 0; y < pixelHeight; y++) {
            int offset = y * pixelWidth;
            for (int x = 0; x < pixelWidth; x++) {
                int index = offset + x;
                int rgb = nonZeroRaw[index] ? 0x00FFFFFF : 0;
                image.setColorArgb(x, y, firstPass[index] << 24 | rgb);
            }
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        texture.upload();
        Identifier id = Identifier.of("celestial", "dynamic/legacy_hud_shadow/"
                + Integer.toUnsignedString(legacyKey, 16));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        cached = new CachedMask(id, texture);
        CACHE.put(legacyKey, cached);
        return cached;
    }

    private static float[] makeKernel(float radius) {
        int kernelRadius = (int) Math.ceil(radius);
        float[] matrix = new float[kernelRadius * 2 + 1];
        float sigma = radius / 3.0f;
        float sigma22 = 2.0f * sigma * sigma;
        float sigmaPi2 = 2.0f * (float) Math.PI * sigma;
        float sqrtSigmaPi2 = (float) Math.sqrt(sigmaPi2);
        float radius2 = radius * radius;
        float total = 0.0f;
        int index = 0;
        for (int row = -kernelRadius; row <= kernelRadius; row++) {
            float distance = row * row;
            matrix[index] = distance > radius2 ? 0.0f
                    : (float) Math.exp(-distance / sigma22) / sqrtSigmaPi2;
            total += matrix[index++];
        }
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] /= total;
        }
        return matrix;
    }

    private static void convolveAndTranspose(
            float[] kernel, int[] input, int[] output,
            int width, int height, boolean[] nonZeroRaw
    ) {
        int radius = kernel.length / 2;
        for (int y = 0; y < height; y++) {
            int outputIndex = y;
            int inputOffset = y * width;
            for (int x = 0; x < width; x++) {
                float alpha = 0.0f;
                for (int column = -radius; column <= radius; column++) {
                    float weight = kernel[radius + column];
                    if (weight == 0.0f) continue;
                    int sampleX = x + column;
                    if (sampleX < 0) sampleX = 0;
                    else if (sampleX >= width) sampleX = width - 1;
                    alpha += weight * input[inputOffset + sampleX];
                }
                if (nonZeroRaw != null) nonZeroRaw[outputIndex] = alpha != 0.0f;
                output[outputIndex] = Math.max(0, Math.min(255,
                        (int) (alpha + 0.5f)));
                outputIndex += height;
            }
        }
    }

    private record CachedMask(
            Identifier id,
            NativeImageBackedTexture texture
    ) {
    }

    private record ShadowQuad(float x, float y, float width, float height, int color, CachedMask mask) {
    }
}
