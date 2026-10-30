package meow.ancient.client.render.texture.gif;

import meow.ancient.client.render.texture.utils.gif.GifData;

import javax.imageio.stream.ImageInputStream;


public interface GifDecompiler {

    GifData decompile(ImageInputStream iis) throws Exception;
}
