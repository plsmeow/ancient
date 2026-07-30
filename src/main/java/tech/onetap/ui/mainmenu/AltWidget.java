package tech.onetap.ui.mainmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.session.Session;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import tech.onetap.mixin.IMinecraftClientAccessor;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AltWidget implements IMinecraft {

    public final List<Alt> alts = new ArrayList<>();

    private float x;
    private final float y;

    public AltWidget() {
        y = 10;
    }

    public boolean open;

    private String altName = "";
    private boolean typing;
    private float scrollPre;
    private float scroll;

    public void updateScroll(int mouseX, int mouseY, float delta) {
        if (HoverUtil.isHovered(mouseX, mouseY, this.x, this.y, 145, 100) && open) {
            scrollPre += delta * 10;
        }
    }

    public void render(DrawContext context, int mx, int my) {
        scroll = scroll + (scrollPre - scroll) * 0.3f;

        this.x = mc.getWindow().getScaledWidth() - 110 - 45;
        float width = 145;

        float height = Math.min(20 + (open ? 10 + (alts.size() + 1) * 17 : 0), 100);

        DrawUtil.drawRoundBlur(this.x, this.y, width, height, new Vector4f(4, 4, 4, 4), ColorProvider.rgba(0, 0, 0, 160), 12f);
        DrawUtil.drawRound(this.x, this.y, width, height, new Vector4f(4, 4, 4, 4), ColorProvider.rgba(15, 15, 22, 220));

        Scissor.push();
        Scissor.setFromComponentCoordinates(this.x, this.y, width - 16, height);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), mc.getSession().getUsername(), this.x + 6, this.y + 6, ColorProvider.rgb(255, 255, 255), 7);
        Scissor.unset();
        Scissor.pop();

        DrawUtil.drawText(Fonts.ICONS.get(), open ? "C" : "B", this.x + width - 6 - Fonts.ICONS.get().getWidth(open ? "C" : "B", 7), this.y + 6.5f, -1, 7);

        if (open) {
            DrawUtil.drawRound(this.x, this.y + 20, width, 5f, ColorProvider.rgba(30, 30, 36, 200), ColorProvider.rgba(30, 30, 36, 0));
            DrawUtil.drawRound(this.x, this.y + 20, width, 0.5f, 0, ColorProvider.rgba(64, 64, 64, 255));

            Scissor.push();
            Scissor.setFromComponentCoordinates(this.x, this.y + 20, width, 100f - 20);
            float i = 0;
            for (Alt alt : alts) {
                DrawUtil.drawRound(this.x + 5, this.y + 26 + i * 17 + scroll, width - 10, 15, 3, mc.getSession().getUsername().equals(alt.name) ? ColorProvider.rgba(40, 40, 50, 200) : ColorProvider.rgba(30, 30, 36, 100));
                DrawUtil.drawText(Fonts.SFREGULAR.get(), alt.name, this.x + 10, this.y + 26 + i * 17 + 4 + scroll, -1, 6);
                i++;
            }
            if (!alts.isEmpty() && 20 + (open ? 10 + (alts.size() + 1) * 17 : 0) > 100)
                scrollPre = MathHelper.clamp(scrollPre, -i * 17 + 50, 0);
            else {
                scrollPre = 0;
            }
            String textToDraw = altName;

            if (!typing && altName.isEmpty()) {
                textToDraw = "nickname";
            }

            DrawUtil.drawRound(this.x + 5, this.y + 26 + i * 17 + scroll, width - 10, 15, 3, ColorProvider.rgba(30, 30, 36, 100));
            DrawUtil.drawText(Fonts.SFREGULAR.get(), textToDraw + (typing ? (System.currentTimeMillis() % 1000 > 500 ? "_" : "") : ""),
                    this.x + 10, this.y + 26 + i * 17 + 4 + scroll, ColorProvider.rgba(255, 255, 255, 100), 6);
            DrawUtil.drawRound(this.x + 5 + 2, this.y + 26 + i * 17 + 2 + scroll,
                    Fonts.SFREGULAR.get().getWidth(textToDraw + (typing ? (System.currentTimeMillis() % 1000 > 500 ? "_" : "") : ""), 6) + 7, 15 - 4, 2, ColorProvider.rgba(40, 40, 50, 100));
            DrawUtil.drawText(Fonts.ICONS.get(), "+", this.x + width - 18,
                    this.y + 26 + i * 17 + 2 + scroll, -1, 10);
            Scissor.unset();
            Scissor.pop();
        }
    }

    public void onChar(char typed) {
        if (typing) {
            if (Fonts.SFREGULAR.get().getWidth(altName, 6f) < 145 - 50) {
                altName += typed;
            }
        }
    }

    public void onKey(int key) {
        boolean ctrlDown = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (typing) {
            if (ctrlDown && key == GLFW.GLFW_KEY_V) {
                try {
                    altName += GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!altName.isEmpty()) {
                    altName = altName.substring(0, altName.length() - 1);
                }
            }
            if (key == GLFW.GLFW_KEY_ENTER) {
                if (altName.length() >= 3) {
                    alts.add(new Alt(altName));
                    AltConfig.updateFile();
                }
                typing = false;
            }
        }
    }

    public void click(int mouseX, int mouseY, int button) {
        float width = 145;

        if (HoverUtil.isHovered(mouseX, mouseY, this.x, this.y, width, 20)) {
            open = !open;
            if (!open) {
                typing = false;
            }
        }
        if (!HoverUtil.isHovered(mouseX, mouseY, this.x, this.y, width, 50)) {
            typing = false;
        }
        List<Alt> toRemove = new ArrayList<>();
        if (open) {
            float i = 0;
            for (Alt alt : alts) {
                if (HoverUtil.isHovered(mouseX, mouseY, this.x + 5, this.y + 26 + i * 17 + scroll, width - 10, 15)) {
                    if (button == 0) {
                        AltConfig.updateFile();
                        ((IMinecraftClientAccessor) mc).setSession(new Session(alt.name, UUID.randomUUID(), "", Optional.empty(), Optional.empty(), Session.AccountType.MOJANG));
                    } else {
                        toRemove.add(alt);
                        AltConfig.updateFile();
                    }
                }
                i++;
            }
            alts.removeAll(toRemove);
        }

        if (HoverUtil.isHovered(mouseX, mouseY, this.x + 82, this.y + 26 + alts.size() * 17 + 2 + scroll, 10, 10)) {
            if (altName.length() >= 3) {
                alts.add(new Alt(altName));
                AltConfig.updateFile();
            }
            typing = false;
        }
        if (HoverUtil.isHovered(mouseX, mouseY, this.x + 5, this.y + 26 + alts.size() * 17 + scroll, width - 10, 15)) {
            typing = !typing;
        }
    }
}
