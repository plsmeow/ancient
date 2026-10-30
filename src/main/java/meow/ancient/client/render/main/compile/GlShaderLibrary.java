package meow.ancient.client.render.main.compile;

import meow.ancient.client.render.main.program.uniform.UniformType;

import java.util.HashMap;

public record GlShaderLibrary(GlslFileEntry libraryEntry, HashMap<String, UniformType<?>> uniforms) {
}
