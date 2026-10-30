package meow.ancient.client.render.main.program;

import meow.ancient.client.render.main.builders.GlProgramBuilder;
import meow.ancient.client.render.main.compile.GlslFileEntry;
import meow.ancient.client.render.main.program.shader.ShaderType;
import meow.ancient.client.render.main.program.uniform.UniformType;

import java.util.HashMap;

public record GlProgramSnippet(HashMap<ShaderType, GlslFileEntry> shaders, HashMap<String, UniformType<?>> uniforms) {
    public <T> void applyTo(GlProgramBuilder<T> builder) {
        shaders.forEach((type, glslFileEntry) -> builder.shader(glslFileEntry, type));
        uniforms.forEach(builder::uniform);
    }
}