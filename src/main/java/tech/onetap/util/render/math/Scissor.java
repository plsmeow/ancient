package tech.onetap.util.render.math;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

import java.awt.*;
import java.util.List;

public class Scissor {
    private static class State implements Cloneable {
        public boolean enabled;
        public int transX;
        public int transY;
        public int x;
        public int y;
        public int width;
        public int height;

        @Override
        public State clone() {
            try {
                return (State) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static State state = new State();
    private static final List<State> stateStack = Lists.newArrayList();

    public static void push() {
        stateStack.add(state.clone());
    }

    public static void pop() {
        state = stateStack.remove(stateStack.size() - 1);
    }

    public static void unset() {
        RenderSystem.disableScissor();
        state.enabled = false;
    }

    public static void setFromComponentCoordinates(int x, int y, int width, int height) {
        Window window = MinecraftClient.getInstance().getWindow();
        int scaleFactor = (int) window.getScaleFactor();

        int screenX = x * scaleFactor;
        int screenY = y * scaleFactor;
        int screenWidth = width * scaleFactor;
        int screenHeight = height * scaleFactor;
        screenY = window.getHeight() - screenY - screenHeight;
        set(screenX, screenY, screenWidth, screenHeight);
    }

    public static void setFromComponentCoordinates(double x, double y, double width, double height) {
        Window window = MinecraftClient.getInstance().getWindow();
        int scaleFactor = (int) window.getScaleFactor();

        int screenX = (int) (x * scaleFactor);
        int screenY = (int) (y * scaleFactor);
        int screenWidth = (int) (width * scaleFactor);
        int screenHeight = (int) (height * scaleFactor);
        screenY = window.getHeight() - screenY - screenHeight;
        set(screenX, screenY, screenWidth, screenHeight);
    }

    public static void setFromComponentCoordinates(double x, double y, double width, double height, float scale) {
        Window window = MinecraftClient.getInstance().getWindow();
        float animationValue = scale;
        float halfRest = (1 - animationValue) / 2f;

        double testX = x + (width * halfRest);
        double testY = y + (height * halfRest);
        double testW = width * animationValue;
        double testH = height * animationValue;

        testX = testX * animationValue + ((window.getScaledWidth() - testW) * halfRest);

        int scaleFactor = (int) window.getScaleFactor();

        int screenX = (int) (testX * scaleFactor);
        int screenY = (int) (testY * scaleFactor);
        int screenWidth = (int) (testW * scaleFactor);
        int screenHeight = (int) (testH * scaleFactor);
        screenY = window.getHeight() - screenY - screenHeight;

        set(screenX, screenY, screenWidth, screenHeight);
    }

    public static void scissor(Window window, double x, double y, double width, double height) {
        if (x + width == x || y + height == y || x < 0 || y + height < 0) return;
        double scaleFactor = window.getScaleFactor();
        int sx = (int) Math.round(x * scaleFactor);
        int sy = (int) Math.round((window.getScaledHeight() - (y + height)) * scaleFactor);
        int sw = (int) Math.round(width * scaleFactor);
        int sh = (int) Math.round(height * scaleFactor);
        RenderSystem.enableScissor(sx, sy, sw, sh);
    }

    public static void set(int x, int y, int width, int height) {
        Window window = MinecraftClient.getInstance().getWindow();
        Rectangle screen = new Rectangle(0, 0, window.getWidth(), window.getHeight());

        Rectangle current = state.enabled
                ? new Rectangle(state.x, state.y, state.width, state.height)
                : screen;

        Rectangle target = new Rectangle(x + state.transX, y + state.transY, width, height);
        Rectangle result = current.intersection(target).intersection(screen);

        if (result.width < 0) result.width = 0;
        if (result.height < 0) result.height = 0;

        state.enabled = true;
        state.x = result.x;
        state.y = result.y;
        state.width = result.width;
        state.height = result.height;

        RenderSystem.enableScissor(result.x, result.y, result.width, result.height);
    }

    public static void translate(int x, int y) {
        state.transX = x;
        state.transY = y;
    }

    public static void translateFromComponentCoordinates(int x, int y) {
        Window window = MinecraftClient.getInstance().getWindow();
        int scaleFactor = (int) window.getScaleFactor();
        int screenX = x * scaleFactor;
        int screenY = y * scaleFactor;
        screenY = (window.getScaledHeight() * scaleFactor) - screenY;
        translate(screenX, screenY);
    }
}