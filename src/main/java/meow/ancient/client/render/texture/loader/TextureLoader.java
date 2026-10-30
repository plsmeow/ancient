package meow.ancient.client.render.texture.loader;

import meow.ancient.client.render.texture.builder.GLTextureBuilder;
import meow.ancient.client.render.texture.builder.GLTextureInfo;
import meow.ancient.client.render.texture.texture.ColorMode;
import meow.ancient.client.render.texture.texture.TextureFiltering;
import meow.ancient.client.render.texture.texture.TextureWrapping;


public abstract class TextureLoader<T> {

    public abstract GLTextureInfo load(T path, ColorMode colorMode, TextureFiltering filtering, TextureWrapping wrapping) throws Exception;

    public GLTextureBuilder<T> createTextureBuilder() {
        return new GLTextureBuilder<>(this);
    }
}
