package tech.onetap.util.render.renderers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.msdf.MsdfFont;
import tech.onetap.util.render.msdf.MsdfRenderer;
import tech.onetap.util.render.renderers.impl.BuiltBlur;
import tech.onetap.util.render.renderers.impl.BuiltRectangle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static tech.onetap.util.render.renderers.IRenderer.DEFAULT_MATRIX;

public class DrawUtil implements IMinecraft {
    public static final List<Line> LINE = new ArrayList<>();
    public static final List<Line> LINE_DEPTH = new ArrayList<>();

    public static void onRender3D(MatrixStack matrix) {
        if (!LINE.isEmpty()) {
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
            Set<Float> widths = LINE.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_CONSTANT_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
            widths.forEach(width -> {
                RenderSystem.lineWidth(width);
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                LINE.stream().filter(line -> line.width == width).forEach(line -> vertexLine(matrix, buffer, line.start, line.end, line.colorStart, line.colorEnd));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            });
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            LINE.clear();
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }
        if (!LINE_DEPTH.isEmpty()) {
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
            Set<Float> widths = LINE_DEPTH.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_CONSTANT_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
            widths.forEach(width -> {
                RenderSystem.lineWidth(width);
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                LINE_DEPTH.stream().filter(line -> line.width == width).forEach(line -> vertexLine(matrix, buffer, line.start, line.end, line.colorStart, line.colorEnd));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            });
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            LINE_DEPTH.clear();
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }
    }

    public static void vertexLine(@NotNull MatrixStack matrices, @NotNull VertexConsumer buffer, Vec3d start, Vec3d end, int lineColor) {
        vertexLine(matrices, buffer, start.toVector3f(), end.toVector3f(), lineColor, lineColor);
    }

    public static void vertexLine(@NotNull MatrixStack matrices, @NotNull VertexConsumer buffer, Vec3d start, Vec3d end, int startColor, int endColor) {
        vertexLine(matrices, buffer, start.toVector3f(), end.toVector3f(), startColor, endColor);
    }

    public static void vertexLine(@NotNull MatrixStack matrices, @NotNull VertexConsumer buffer, Vector3f start, Vector3f end, int startColor, int endColor) {
        matrices.push();
        MatrixStack.Entry entry = matrices.peek();

        Vector3f vec = getNormal(start.x, start.y, start.z, end.x, end.y, end.z);
        buffer.vertex(entry, start).color(startColor).normal(entry, vec.x(), vec.y(), vec.z());
        buffer.vertex(entry, end).color(endColor).normal(entry, vec.x(), vec.y(), vec.z());
        matrices.pop();
    }

    public static @NotNull Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = MathHelper.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);
        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }

    public static void drawLine(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, float width, boolean depth) {
        drawLine(new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ), color, width, depth);
    }

    public static void drawLine(Vec3d start, Vec3d end, int color, float width, boolean depth) {
        drawLine(start, end, color, color, width, depth);
    }

    public static void drawLine(Vec3d start, Vec3d end, int colorStart, int colorEnd, float width, boolean depth) {
        Vec3d cameraPos = mc.getEntityRenderDispatcher().camera.getPos();
        Line line = new Line(start.subtract(cameraPos), end.subtract(cameraPos), colorStart, colorEnd, width);
        if (depth) LINE_DEPTH.add(line);
        else LINE.add(line);
    }

    public static void drawRound(float x, float y, float width, float height, float radius, int color) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(radius))
                .smoothness(1)
                .build();
        rectangle.render(x, y);
    }

    public static void drawRound(float x, float y, float width, float height, float radius, int color, int color2) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color, color2, color2))
                .radius(new QuadRadiusState(radius))
                .smoothness(1)
                .build();
        rectangle.render(x, y);
    }

    public static void drawRound(float x, float y, float width, float height, float radius, int color, int color2, int color3, int color4) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color2, color3, color4))
                .radius(new QuadRadiusState(radius))
                .smoothness(1)
                .build();
        rectangle.render(x, y);
    }

    public static void drawRound(float x, float y, float width, float height, float radius, float smoothness, int color) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(radius))
                .smoothness(smoothness)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRound(float x, float y, float width, float height, float radius, float smoothness, int color, int color2) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color, color2, color2))
                .radius(new QuadRadiusState(radius))
                .smoothness(smoothness)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRound(float x, float y, float width, float height, float radius, float smoothness, int color, int color2, int color3, int color4) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color2, color3, color4))
                .radius(new QuadRadiusState(radius))
                .smoothness(smoothness)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRound(float x, float y, float width, float height, Vector4f vector4f, int color) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .smoothness(1)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRound(float x, float y, float width, float height, Vector4f vector4f, int color, int color2) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color, color2, color2))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .smoothness(1)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRound(float x, float y, float width, float height, Vector4f vector4f, int color, int color2, int color3, int color4) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color2, color3, color4))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .smoothness(1)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRound(float x, float y, float width, float height, Vector4f vector4f, float smoothness, int color) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .smoothness(smoothness)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRound(float x, float y, float width, float height, Vector4f vector4f, float smoothness, int color, int color2) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color, color2, color2))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .smoothness(smoothness)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRound(float x, float y, float width, float height, Vector4f vector4f, float smoothness, int color, int color2, int color3, int color4) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color2, color3, color4))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .smoothness(smoothness)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, float radius, int color, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(radius))
                .blurRadius(blurIntensivity)
                .smoothness(1)
                .build();
        rectangle.render(x, y);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, float radius, int color, int color2, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color, color2, color2))
                .radius(new QuadRadiusState(radius))
                .blurRadius(blurIntensivity)
                .smoothness(1)
                .build();
        rectangle.render(x, y);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, float radius, int color, int color2, int color3, int color4, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color2, color3, color4))
                .radius(new QuadRadiusState(radius))
                .blurRadius(blurIntensivity)
                .smoothness(1)
                .build();
        rectangle.render(x, y);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, float radius, int color, float smoothness, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(radius))
                .blurRadius(blurIntensivity)
                .smoothness(smoothness)
                .build();
        rectangle.render(x, y);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, float radius, int color, int color2, float smoothness, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color, color2, color2))
                .radius(new QuadRadiusState(radius))
                .blurRadius(blurIntensivity)
                .smoothness(smoothness)
                .build();
        rectangle.render(x, y);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, float radius, int color, int color2, int color3, int color4, float smoothness, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color2, color3, color4))
                .radius(new QuadRadiusState(radius))
                .blurRadius(blurIntensivity)
                .smoothness(smoothness)
                .build();
        rectangle.render(x, y);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, Vector4f vector4f, int color, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .blurRadius(blurIntensivity)
                .smoothness(1)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, Vector4f vector4f, int color, int color2, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color, color2, color2))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .blurRadius(blurIntensivity)
                .smoothness(1)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, Vector4f vector4f, int color, int color2, int color3, int color4, float blurIntensivity) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color2, color3, color4))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .smoothness(1)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, Vector4f vector4f, int color, float smoothness, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .blurRadius(blurIntensivity)
                .smoothness(smoothness)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, Vector4f vector4f, int color, int color2, float smoothness, float blurIntensivity) {
        BuiltBlur rectangle = Builder.blur()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color, color2, color2))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .blurRadius(blurIntensivity)
                .smoothness(smoothness)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawRoundBlur(float x, float y, float width, float height, Vector4f vector4f, int color, int color2, int color3, int color4, float smoothness, float blurIntensivity) {
        BuiltRectangle rectangle = Builder.rectangle()
                .size(new SizeState(width + 0.5f, height + 0.25f))
                .color(new QuadColorState(color, color2, color3, color4))
                .radius(new QuadRadiusState(vector4f.x, vector4f.y, vector4f.z, vector4f.w))
                .smoothness(smoothness)
                .build();
        rectangle.render(x - 0.5f,y - 0.5f);
    }

    public static void drawText(MsdfFont font, String text, float x, float y, int color, float size) {
        MsdfRenderer.renderText(font, text, size, color, DEFAULT_MATRIX, x, y + 2, 0);
    }

    public static void drawText(MsdfFont font, String text, float x, float y, int color, float size, float fadeoutStart, float fadeoutEnd, float maxWidth) {
        MsdfRenderer.renderText(font, text, size, color, DEFAULT_MATRIX, x, y, 0, true, fadeoutStart, fadeoutEnd, maxWidth);
    }

    public static void drawText(MsdfFont font, Text text, float x, float y, float size, int alpha) {
        MsdfRenderer.renderText(font, text, size, DEFAULT_MATRIX, x, y + 2, 0, alpha);
    }

    public record Line(Vec3d start, Vec3d end, int colorStart, int colorEnd, float width) {}
}