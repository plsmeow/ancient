package tech.onetap.util.render.stencil;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import tech.onetap.util.IMinecraft;

public class StencilUtil implements IMinecraft {

    public static void checkSetupFBO(Framebuffer fbo) {
        if (fbo == null || !fbo.useDepthAttachment) return;

        int depthId = fbo.getDepthAttachment();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthId);
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);

        if (format != GL30.GL_DEPTH24_STENCIL8) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, fbo.textureWidth, fbo.textureHeight, 0, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, (java.nio.ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, depthId, 0);
            fbo.beginWrite(false);
        }
    }

    public static void push() {
        checkSetupFBO(mc.getFramebuffer());
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        RenderSystem.colorMask(false, false, false, false);
    }

    public static void read(int ref) {
        RenderSystem.colorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, ref, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    public static void pop() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }
}