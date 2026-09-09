package tech.onetap.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.util.math.MatrixStack;
import tech.onetap.util.IMinecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Nested intersecting scissor stack ported from DeltaClient (aethereal.render.ScissorUtil).
 * Each push intersects with the enclosing scissor box; the matrices push/pop pair is kept
 * so callers can translate content while clipped.
 */
public class ScissorUtil implements IMinecraft {
    private static final Deque<Box> stack = new ArrayDeque<>();

    private ScissorUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void push(MatrixStack matrixStack, float x, float y, float width, float height) {
        float scaleFactor = (float) mc.getWindow().getScaleFactor();
        Box scissorBox = new Box((int) (x * scaleFactor),
                (int) (((mc.getWindow().getScaledHeight() - y) - height) * scaleFactor),
                (int) (width * scaleFactor), (int) (height * scaleFactor));
        if (!stack.isEmpty()) {
            scissorBox = scissorBox.intersect(stack.peek());
        }
        stack.push(scissorBox);
        matrixStack.push();
        apply(scissorBox);
    }

    public static void pop(MatrixStack matrixStack) {
        stack.pop();
        if (stack.isEmpty()) {
            RenderSystem.disableScissor();
        } else {
            apply(stack.peek());
        }
        matrixStack.pop();
    }

    private static void apply(Box box) {
        RenderSystem.enableScissor(box.x, box.y, box.width, box.height);
    }

    record Box(int x, int y, int width, int height) {

        Box intersect(Box p) {
            int nx = Math.max(this.x, p.x);
            int ny = Math.max(this.y, p.y);
            return new Box(nx, ny,
                    Math.max(0, Math.min(this.x + this.width, p.x + p.width) - nx),
                    Math.max(0, Math.min(this.y + this.height, p.y + p.height) - ny));
        }
    }
}
