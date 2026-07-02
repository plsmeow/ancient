package tech.onetap.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.awt.Color;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ThemeManagerWindow implements IMinecraft {
    private float x, y, width = 140, height;
    private boolean open;
    private final Animation openAnim = new Animation(Easing.QUINTIC_OUT, 400);
    private final Animation scrollAnim = new Animation(Easing.CUBIC_IN_OUT, 200);

    private float scroll;
    private float maxScroll;

    private final List<CustomTheme> defaultThemes = new ArrayList<>();
    private final List<CustomTheme> customThemes = new ArrayList<>();
    private CustomTheme activeTheme;

    private final ClickGuiFrame parent;

    private static final File THEME_DIR = new File(".options");
    private static final File THEME_FILE = new File(THEME_DIR, "themes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ThemeManagerWindow(ClickGuiFrame parent) {
        this.parent = parent;

        defaultThemes.add(new CustomTheme("Crimson", new Color(220, 20, 60, 255).getRGB(), new Color(30, 0, 0, 255).getRGB(), false));
        defaultThemes.add(new CustomTheme("Cyber Blue", new Color(0, 200, 255, 255).getRGB(), new Color(10, 10, 20, 255).getRGB(), false));
        defaultThemes.add(new CustomTheme("Violet Void", new Color(180, 0, 255, 255).getRGB(), new Color(30, 0, 40, 255).getRGB(), false));
        defaultThemes.add(new CustomTheme("Abyss Blue", new Color(0, 102, 204, 255).getRGB(), new Color(10, 10, 30, 255).getRGB(), false));
        defaultThemes.add(new CustomTheme("Obsidian Glow", new Color(200, 200, 255, 255).getRGB(), new Color(10, 10, 15, 230).getRGB(), false));
        defaultThemes.add(new CustomTheme("Quantum Shift", new Color(100, 255, 230, 255).getRGB(), new Color(0, 20, 25, 220).getRGB(), false));
        defaultThemes.add(new CustomTheme("White-Black", new Color(255, 255, 255, 255).getRGB(), new Color(0, 0, 0, 255).getRGB(), false));
        defaultThemes.add(new CustomTheme("Serenity", new Color(137, 159, 255, 255).getRGB(), new Color(20, 20, 35, 255).getRGB(), false));

        activeTheme = defaultThemes.get(0);

        loadThemes();

        updateClientTheme(activeTheme.color1, activeTheme.color2);
    }

    public void saveThemes() {
        try {
            if (!THEME_DIR.exists()) THEME_DIR.mkdirs();

            JsonObject json = new JsonObject();
            json.addProperty("activeTheme", activeTheme.name);

            JsonArray customThemesArray = new JsonArray();
            for (CustomTheme theme : customThemes) {
                JsonObject themeObj = new JsonObject();
                themeObj.addProperty("name", theme.name);
                themeObj.addProperty("color1", theme.color1);
                themeObj.addProperty("color2", theme.color2);
                customThemesArray.add(themeObj);
            }
            json.add("customThemes", customThemesArray);

            Files.writeString(THEME_FILE.toPath(), GSON.toJson(json));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadThemes() {
        if (!THEME_FILE.exists()) return;

        try {
            String content = Files.readString(THEME_FILE.toPath());
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            if (json.has("customThemes")) {
                customThemes.clear();
                JsonArray customArray = json.getAsJsonArray("customThemes");
                for (JsonElement el : customArray) {
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    int c1 = obj.get("color1").getAsInt();
                    int c2 = obj.get("color2").getAsInt();
                    customThemes.add(new CustomTheme(name, c1, c2, true));
                }
            }

            if (json.has("activeTheme")) {
                String activeName = json.get("activeTheme").getAsString();
                boolean found = false;

                for (CustomTheme theme : defaultThemes) {
                    if (theme.name.equals(activeName)) {
                        activeTheme = theme;
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    for (CustomTheme theme : customThemes) {
                        if (theme.name.equals(activeName)) {
                            activeTheme = theme;
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Ошибка при загрузке тем Onetap!");
        }
    }

    private void updateClientTheme(int c1, int c2) {
        tech.onetap.module.settings.impl.ThemeManager.getInstance()
                .getCurrentTheme()
                .setColors(c1, c2);
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        openAnim.run(open);
        if (openAnim.getValue() < 0.01f) return;

        float animValue = (float) openAnim.getValue();
        float currentX = x - (width * (1 - animValue));
        float alphaRatio = animValue;
        float cornerRadius = 8f;
        float headerHeight = 20f;
        float bottomBarHeight = 26f;

        DrawUtil.drawRoundBlur(currentX, y, width, height, cornerRadius, ColorProvider.rgba(75, 75, 75, (int)(255 * alphaRatio)), 20f);
        DrawUtil.drawRound(currentX, y, width, height, cornerRadius, ColorProvider.rgba(14, 14, 16, (int)(130 * alphaRatio)));

        String title = "Theme Editor";
        float titleWidth = Fonts.SFREGULAR.get().getWidth(title, 8.5f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), title, currentX + width / 2f - titleWidth / 2f, y + 6f, ColorProvider.rgba(255, 255, 255, (int)(255 * alphaRatio)), 8.5f);

        float addX = currentX + width - 16;
        float addY = y + 5;
        boolean hoverAdd = HoverUtil.isHovered(mouseX, mouseY, addX, addY, 10, 10);
        if (hoverAdd) CursorManager.requestHand();
        DrawUtil.drawText(Fonts.SFREGULAR.get(), "+", addX, addY + 1.5f, 
            hoverAdd ? ColorProvider.rgba(200, 200, 200, (int)(255 * alphaRatio)) : ColorProvider.rgba(255, 255, 255, (int)(255 * alphaRatio)), 9f);

        clampScroll();
        scrollAnim.run(scroll);

        float offset = 0;
        Scissor.push();
        Scissor.setFromComponentCoordinates(currentX, y + headerHeight, width, height - headerHeight - bottomBarHeight);

        for (CustomTheme theme : customThemes) {
            float themeY = y + headerHeight + offset + (float) scrollAnim.getValue();
            theme.render(matrixStack, currentX + 5.2f, themeY, width - 10.4f, mouseX, mouseY, alphaRatio);
            offset += theme.getHeight() + 0.75f;
        }

        maxScroll = Math.max(0, offset - (height - headerHeight - bottomBarHeight));
        Scissor.unset();
        Scissor.pop();

        float circleSize = 10f;
        float spacing = 4.5f;
        float totalW = defaultThemes.size() * circleSize + (defaultThemes.size() - 1) * spacing;

        float circleStartX = currentX + (width / 2f) - (totalW / 2f);
        float circleY = y + height - 17f;

        for (CustomTheme defaultTheme : defaultThemes) {
            boolean isHovered = HoverUtil.isHovered(mouseX, mouseY, circleStartX, circleY, circleSize, circleSize);
            if (isHovered) CursorManager.requestHand();

            DrawUtil.drawRound(circleStartX, circleY, circleSize, circleSize, circleSize / 2f, ColorProvider.setAlpha(defaultTheme.color1, (int)(255 * alphaRatio)));

            if (activeTheme == defaultTheme) {
                float innerSize = 4f;
                DrawUtil.drawRound(circleStartX + (circleSize / 2f) - (innerSize / 2f), circleY + (circleSize / 2f) - (innerSize / 2f), innerSize, innerSize, innerSize / 2f, ColorProvider.rgba(20, 20, 25, (int)(255 * alphaRatio)));
            }
            circleStartX += circleSize + spacing;
        }
    }

    private void clampScroll() {
        if (maxScroll > 0) scroll = MathHelper.clamp(scroll, -maxScroll, 0);
        else scroll = 0;
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || openAnim.getValue() < 0.9f) return;

        float currentX = x;
        float addX = currentX + width - 16;
        float addY = y + 5;

        if (HoverUtil.isHovered(mouseX, mouseY, addX - 2, addY - 2, 14, 14) && button == 0) {
            customThemes.add(new CustomTheme("Custom " + (customThemes.size() + 1), 0xFFFF0000, 0xFF00FF00, true));
            maxScroll += 40;
            scroll = -maxScroll;
            saveThemes();
            return;
        }

        float bottomBarHeight = 26f;
        if (HoverUtil.isHovered(mouseX, mouseY, currentX, y + 20, width, height - 20 - bottomBarHeight)) {
            for (CustomTheme theme : customThemes) {
                theme.mouseClicked(mouseX, mouseY, button);
            }
        }

        float circleSize = 10f;
        float spacing = 4.5f;
        float totalW = defaultThemes.size() * circleSize + (defaultThemes.size() - 1) * spacing;
        float circleStartX = currentX + (width / 2f) - (totalW / 2f);
        float circleY = y + height - 17f;

        for (CustomTheme defaultTheme : defaultThemes) {
            if (HoverUtil.isHovered(mouseX, mouseY, circleStartX, circleY, circleSize, circleSize) && button == 0) {
                activeTheme = defaultTheme;
                updateClientTheme(defaultTheme.color1, defaultTheme.color2);
                saveThemes();
            }
            circleStartX += circleSize + spacing;
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (!open) return;
        for (CustomTheme theme : customThemes) {
            theme.mouseReleased(mouseX, mouseY, button);
        }
    }

    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!open) return;
        float currentX = x - (width * (1 - (float)openAnim.getValue()));
        float bottomBarHeight = 26f;
        if (HoverUtil.isHovered(mouseX, mouseY, currentX, y + 20, width, height - 20 - bottomBarHeight)) {
            scroll += (float) (verticalAmount * 30f);
            clampScroll();
        }
    }

    public class CustomTheme {
        public String name;
        public int color1, color2;
        public boolean isCustom;

        private boolean isEditing = false;
        private final Animation editAnim = new Animation(Easing.QUINTIC_OUT, 320);
        private final Animation hoverAnim = new Animation(Easing.QUINTIC_OUT, 300);
        private final Animation activeAnim = new Animation(Easing.QUINTIC_OUT, 400);

        private float renderX, renderY, renderW, renderH;

        private float[] hsv1 = new float[3];
        private boolean draggingSV1, draggingH1;

        private float[] hsv2 = new float[3];
        private boolean draggingSV2, draggingH2;

        public CustomTheme(String name, int color1, int color2, boolean isCustom) {
            this.name = name;
            this.color1 = color1;
            this.color2 = color2;
            this.isCustom = isCustom;
            updateHSV(1, color1);
            updateHSV(2, color2);
        }

        private void updateHSV(int id, int rgb) {
            Color c = new Color(rgb, true);
            if (id == 1) Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsv1);
            else Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsv2);
        }

        public void render(MatrixStack matrices, float itemX, float itemY, float itemW, int mouseX, int mouseY, float alphaMod) {
            float baseHeight = 15f;
            boolean isHovered = HoverUtil.isHovered(mouseX, mouseY, itemX, itemY, itemW, baseHeight) || (activeTheme == this);

            hoverAnim.run(isHovered);
            editAnim.run(isEditing);
            activeAnim.run(activeTheme == this);

            float expandedHeight = 55f;
            float currentHeight = baseHeight + (expandedHeight * (float)editAnim.getValue());

            this.renderX = itemX;
            this.renderY = itemY;
            this.renderW = itemW;
            this.renderH = currentHeight;

            if (HoverUtil.isHovered(mouseX, mouseY, itemX, itemY, itemW, baseHeight)) CursorManager.requestHand();

            int outlineAlpha = (int) ((25 + (40 * hoverAnim.getValue())) * alphaMod);
            int outlineColor = ColorProvider.rgba(255, 255, 255, outlineAlpha);
            int innerColor = ColorProvider.rgba(44, 44, 44, (int)(140 * alphaMod));

            DrawUtil.drawRound(itemX - 0.5f, itemY - 0.5f, itemW + 1f, currentHeight + 0.5f, 3.5f, outlineColor);
            DrawUtil.drawRoundBlur(itemX, itemY, itemW, currentHeight - 0.5f, 3f, innerColor, 20f);

            int textColor = ColorProvider.interpolateColor(
                    ColorProvider.rgba(170, 170, 170, (int)(255 * alphaMod)),
                    ColorProvider.rgba(255, 255, 255, (int)(255 * alphaMod)),
                    activeAnim.getValue()
            );
            DrawUtil.drawText(Fonts.SFREGULAR.get(), name, itemX + 4.5f, itemY + 3.75f, textColor, 7.5f);

            float previewSize = 7f;
            float previewY = itemY + (baseHeight / 2f) - (previewSize / 2f);
            DrawUtil.drawRound(itemX + itemW - 20, previewY, previewSize, previewSize, 3.5f, color1);
            DrawUtil.drawRound(itemX + itemW - 10, previewY, previewSize, previewSize, 3.5f, color2);

            if (isCustom) {
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), "...", itemX + itemW - 32, itemY + 1.75f, textColor, 7.5f);
            }

            if (isCustom && editAnim.getValue() > 0.01f) {
                float pickerY = itemY + baseHeight + 2;
                float animAlpha = (float) editAnim.getValue() * alphaMod;

                Scissor.push();
                Scissor.setFromComponentCoordinates(itemX, pickerY, itemW, expandedHeight);

                DrawUtil.drawRound(itemX + 1f, itemY + baseHeight, itemW - 2f, currentHeight - baseHeight, 0f, ColorProvider.rgba(0, 0, 0, (int)(30 * animAlpha)));

                float halfW = itemW / 2f;
                renderModernPicker(1, hsv1, itemX + 4, pickerY, mouseX, mouseY, animAlpha);
                renderModernPicker(2, hsv2, itemX + halfW + 2, pickerY, mouseX, mouseY, animAlpha);

                Scissor.unset();
                Scissor.pop();
            }
        }

        private void renderModernPicker(int id, float[] hsv, float px, float py, int mouseX, int mouseY, float alphaAnim) {
            float svSize = 42f;
            float hueW = 5f;
            int alphaInt = (int)(255 * alphaAnim);

            int cHue        = ColorProvider.setAlpha(Color.HSBtoRGB(hsv[0], 1F, 1F), alphaInt);
            int cWhite      = ColorProvider.rgba(255, 255, 255, alphaInt);
            int cClearWhite = ColorProvider.rgba(255, 255, 255, 0);
            int cBlack      = ColorProvider.rgba(0, 0, 0, alphaInt);
            int cClearBlack = ColorProvider.rgba(0, 0, 0, 0);

            DrawUtil.drawRound(px, py, svSize, svSize, 2f, cHue);
            DrawUtil.drawRound(px, py, svSize, svSize, 2f, cWhite, cWhite, cClearWhite, cClearWhite);
            DrawUtil.drawRound(px, py, svSize, svSize, 2f, cClearBlack, cBlack, cBlack, cClearBlack);

            float svCursorX = px + hsv[1] * svSize;
            float svCursorY = py + (1 - hsv[2]) * svSize;
            DrawUtil.drawRound(svCursorX - 2.5f, svCursorY - 2.5f, 5f, 5f, 2.5f, ColorProvider.rgba(0, 0, 0, (int)(180 * alphaAnim)));
            DrawUtil.drawRound(svCursorX - 1.5f, svCursorY - 1.5f, 3f, 3f, 1.5f, ColorProvider.rgba(255, 255, 255, alphaInt));

            float hueX = px + svSize + 4;
            for (float i = 0; i <= svSize; i += 0.5f) {
                int color = ColorProvider.setAlpha(Color.HSBtoRGB(i / svSize, 1F, 1F), alphaInt);
                DrawUtil.drawRound(hueX, py + i, hueW, 1f, 0f, color);
            }

            float hueCursorY = py + hsv[0] * svSize;
            DrawUtil.drawRound(hueX - 1.5f, hueCursorY - 2.5f, hueW + 3f, 5f, 2f, ColorProvider.rgba(0, 0, 0, (int)(180 * alphaAnim)));
            DrawUtil.drawRound(hueX - 0.5f, hueCursorY - 1.5f, hueW + 1f, 3f, 1f, ColorProvider.rgba(255, 255, 255, alphaInt));

            if (id == 1 && draggingSV1) {
                hsv[1] = Math.max(0, Math.min(1, (mouseX - px) / svSize));
                hsv[2] = 1F - Math.max(0, Math.min(1, (mouseY - py) / svSize));
                color1 = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) | 0xFF000000;
                if (activeTheme == this) updateClientTheme(color1, color2);
            } else if (id == 1 && draggingH1) {
                hsv[0] = Math.max(0, Math.min(1, (mouseY - py) / svSize));
                color1 = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) | 0xFF000000;
                if (activeTheme == this) updateClientTheme(color1, color2);
            }

            if (id == 2 && draggingSV2) {
                hsv[1] = Math.max(0, Math.min(1, (mouseX - px) / svSize));
                hsv[2] = 1F - Math.max(0, Math.min(1, (mouseY - py) / svSize));
                color2 = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) | 0xFF000000;
                if (activeTheme == this) updateClientTheme(color1, color2);
            } else if (id == 2 && draggingH2) {
                hsv[0] = Math.max(0, Math.min(1, (mouseY - py) / svSize));
                color2 = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) | 0xFF000000;
                if (activeTheme == this) updateClientTheme(color1, color2);
            }
        }

        public void mouseClicked(double mouseX, double mouseY, int button) {
            if (HoverUtil.isHovered(mouseX, mouseY, renderX, renderY, renderW, 15f)) {
                if (button == 0) {
                    activeTheme = this;
                    updateClientTheme(color1, color2);
                    saveThemes();
                } else if (button == 1 && isCustom) {
                    isEditing = !isEditing;
                }
            }

            if (isEditing && isCustom) {
                float pickerY = renderY + 15f + 2;
                float halfW = renderW / 2f;
                float svSize = 42f;

                float px1 = renderX + 4;
                if (HoverUtil.isHovered(mouseX, mouseY, px1, pickerY, svSize, svSize)) draggingSV1 = true;
                if (HoverUtil.isHovered(mouseX, mouseY, px1 + svSize + 4, pickerY, 10, svSize)) draggingH1 = true;

                float px2 = renderX + halfW + 2;
                if (HoverUtil.isHovered(mouseX, mouseY, px2, pickerY, svSize, svSize)) draggingSV2 = true;
                if (HoverUtil.isHovered(mouseX, mouseY, px2 + svSize + 4, pickerY, 10, svSize)) draggingH2 = true;
            }
        }

        public void mouseReleased(double mouseX, double mouseY, int button) {
            if (draggingSV1 || draggingH1 || draggingSV2 || draggingH2) {
                saveThemes();
            }

            draggingSV1 = false; draggingH1 = false;
            draggingSV2 = false; draggingH2 = false;
        }

        public float getHeight() {
            return 15f + (55f * (float)editAnim.getValue());
        }
    }
}