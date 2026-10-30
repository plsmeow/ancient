package meow.ancient.client.render.texture.loader;

import meow.ancient.client.render.texture.builder.GLGifInfo;
import meow.ancient.client.render.texture.gif.DecompileMode;


public interface GifLoader {

    GLGifInfo load(DecompileMode decompileMode) throws Exception;
}
