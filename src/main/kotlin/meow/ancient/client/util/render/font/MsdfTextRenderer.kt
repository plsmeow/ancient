package meow.ancient.client.util.render.font

import meow.ancient.client.render.main.ChromaLoaders
import meow.ancient.client.render.main.ChromaRenderer
import meow.ancient.client.render.main.program.shader.ShaderType
import meow.ancient.client.render.main.program.uniform.UniformType
import meow.ancient.client.render.main.program.uniform.uniforms.FloatUniform
import meow.ancient.client.render.main.program.uniform.uniforms.sampler.SamplerUniform
import meow.ancient.client.render.main.vertex.DrawMode
import meow.ancient.client.render.main.vertex.element.VertexElementType
import meow.ancient.client.render.main.vertex.format.VertexFormat
import meow.ancient.client.render.main.vertex.mesh.IMesh
import meow.ancient.client.render.texture.texture.GLTexture
import meow.ancient.client.util.render.engine.Renderable

class MsdfBatchState(val texture: GLTexture, val range: Float)

object MsdfTextRenderer : Renderable() {
    val TextFormats: VertexFormat = VertexFormat.builder()
        .element("UV0", VertexElementType.FLOAT, 2)
        .element("Color", VertexElementType.FLOAT, 4)
        .element("Style0", VertexElementType.FLOAT, 3)
        .element("OutlineColor0", VertexElementType.FLOAT, 4)
        .element("Fade0", VertexElementType.FLOAT, 4)
        .element("Scissor0", VertexElementType.FLOAT, 4)
        .build()

    private var rangeUniform: FloatUniform? = null
    private var textureUniform: SamplerUniform? = null

    override fun name(): String = "msdf_text"
    override fun shader(): String = "font/text"
    override fun drawMode(): DrawMode = DrawMode.QUADS
    override fun vertexFormat(): VertexFormat = TextFormats

    override fun load() {
        val program = ChromaLoaders.IN_JAR.createProgramBuilder(ChromaRenderer.matrixSnippet)
            .name(name())
            .shader(ChromaLoaders.IN_JAR.createGlslFileEntry("text-vsh", "${SHADER_CORE_PATH}font/text.vsh"), ShaderType.Vertex)
            .shader(ChromaLoaders.IN_JAR.createGlslFileEntry("text-fsh", "${SHADER_CORE_PATH}font/text.fsh"), ShaderType.Fragment)
            .sampler("uTexture")
            .uniform("uRange", UniformType.FLOAT)
            .build() ?: return

        glProgram = program
        rangeUniform = program.getUniform("uRange", UniformType.FLOAT)
        textureUniform = program.getUniform("uTexture", UniformType.SAMPLER)
    }

    override fun isBatchCompatible(oldState: Any?, newState: Any?): Boolean {
        val o = oldState as? MsdfBatchState ?: return false
        val n = newState as? MsdfBatchState ?: return false
        return o.texture.texId == n.texture.texId && o.range.toBits() == n.range.toBits()
    }

    override fun renderBatch(mesh: IMesh?, state: Any?) {
        val batch = state as? MsdfBatchState ?: return
        val program = glProgram ?: return
        setGlobalProgram(program)
        initMatrix()
        rangeUniform?.set(batch.range)
        textureUniform?.set(batch.texture)
        ChromaRenderer.draw(mesh)
    }
}
