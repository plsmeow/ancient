package tech.onetap.util.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;

/**
 * Base class for core-shader wrappers ported from DeltaClient (aethereal.ui.shader.Shader).
 * Binds a vanilla {@link ShaderProgramKey} and caches its uniform handles on first bind.
 */
public abstract class Shader {
    protected final ShaderProgramKey key;
    protected ShaderProgram program;

    public Shader(Identifier identifier, VertexFormat vertexFormat) {
        this.key = new ShaderProgramKey(identifier, vertexFormat, Defines.EMPTY);
    }

    protected abstract void bindUniforms();

    public ShaderProgramKey getKey() {
        return this.key;
    }

    public ShaderProgram getProgram() {
        return this.program;
    }

    /**
     * Activates the wrapped shader program and refreshes cached uniform handles.
     */
    public void bind() {
        this.program = RenderSystem.setShader(this.key);
        bindUniforms();
    }

    protected Uniform uniform(String name) {
        if (this.program != null) {
            return this.program.getUniform(name);
        }
        return null;
    }
}
