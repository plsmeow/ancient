package meow.ancient.client.render.main.builders;

import meow.ancient.client.render.main.ChromaLoader;
import meow.ancient.client.render.main.compile.GlobalChromaCompiler;
import meow.ancient.client.render.main.compile.GlslFileEntry;
import meow.ancient.client.render.main.exceptions.ExceptionPrinter;
import meow.ancient.client.render.main.exceptions.impl.DoubleShaderAdditionException;
import meow.ancient.client.render.main.exceptions.impl.DoubleUniformAdditionException;
import meow.ancient.client.render.main.exceptions.impl.IllegalProgramBuilderArgumentException;
import meow.ancient.client.render.main.program.GlProgram;
import meow.ancient.client.render.main.program.GlProgramSnippet;
import meow.ancient.client.render.main.program.shader.GlShader;
import meow.ancient.client.render.main.program.shader.ShaderType;
import meow.ancient.client.render.main.program.uniform.GlUniform;
import meow.ancient.client.render.main.program.uniform.UniformType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GlProgramBuilder<T> {
    private String name;
    private final HashMap<ShaderType, GlslFileEntry> shaders = new HashMap<>();
    private final HashMap<String, UniformType<?>> uniforms = new HashMap<>();
    private final GlProgramSnippet[] snippets;
    private final ChromaLoader<T> loader;

    public GlProgramBuilder(ChromaLoader<T> loader, GlProgramSnippet... snippets) {
        for (GlProgramSnippet snippet : snippets)
            snippet.applyTo(this);

        this.loader = loader;
        this.snippets = snippets;
    }

    public GlProgramBuilder<T> name(String name) {
        this.name = name;
        return this;
    }

    public GlProgramBuilder<T> shader(String name, T shaderPath, ShaderType type) {
        if (this.shaders.containsKey(type))
            ExceptionPrinter.printAndExit(new DoubleShaderAdditionException(name, type, this.shaders.get(type).name()));

        this.shaders.put(type, loader.createGlslFileEntry(name, shaderPath));
        return this;
    }

    public GlProgramBuilder<T> shader(GlslFileEntry shaderEntry, ShaderType type) {
        if (this.shaders.containsKey(type))
            ExceptionPrinter.printAndExit(new DoubleShaderAdditionException(shaderEntry.name(), type, this.shaders.get(type).name()));

        this.shaders.put(type, shaderEntry);
        return this;
    }

    public <S extends GlUniform> GlProgramBuilder<T> uniform(String name, UniformType<S> uniformType) {
        if (this.uniforms.containsKey(name))
            ExceptionPrinter.printAndExit(new DoubleUniformAdditionException(name));

        this.uniforms.put(name, uniformType);
        return this;
    }

    
    public GlProgramBuilder<T> sampler(String name) {
        uniform(name, UniformType.SAMPLER);
        return this;
    }

    public GlProgram build() {
        if (this.name == null)
            ExceptionPrinter.printAndExit(new IllegalProgramBuilderArgumentException("Missing name in program builder."));

        if (!this.shaders.containsKey(ShaderType.Compute)) {
            if (!this.shaders.containsKey(ShaderType.Vertex))
                ExceptionPrinter.printAndExit(new IllegalProgramBuilderArgumentException(String.format("Missing vertex shader in program '%s'.", this.name)));
            if (!this.shaders.containsKey(ShaderType.Fragment))
                ExceptionPrinter.printAndExit(new IllegalProgramBuilderArgumentException(String.format("Missing fragment shader in program '%s'.", this.name)));
        }

        List<GlShader> shaderList = new ArrayList<>();
        this.shaders.forEach((type, glslFileEntry) ->
            shaderList.add(GlobalChromaCompiler.compileShader(glslFileEntry, type))
        );

        return GlobalChromaCompiler.compileProgram(
                this.name,
                shaderList,
                this.snippets,
                this.uniforms
        );
    }
    
    public GlProgramSnippet buildSnippet() {
        return new GlProgramSnippet(this.shaders, this.uniforms);
    }
}
