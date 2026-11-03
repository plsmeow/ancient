package meow.ancient.client.render.texture.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public enum TextureWrapping {
    DEFAULT(GL12.GL_CLAMP_TO_EDGE),
    REPEAT(GL11.GL_REPEAT);

    public final int id;

    TextureWrapping(int id) {
        this.id = id;
    }
}
