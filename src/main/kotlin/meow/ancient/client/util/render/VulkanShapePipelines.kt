package meow.ancient.client.util.render

import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.UniformType
import com.mojang.renderpearl.api.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object VulkanShapePipelines {
    val RECT_SDF_FORMAT: VertexFormat by lazy {
        VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .addAttribute("UV3", GpuFormat.RG32_FLOAT)
            .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
            .build()
    }

    val BLUR_RECT_SDF_FORMAT: VertexFormat by lazy {
        VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .addAttribute("UV3", GpuFormat.RG32_FLOAT)
            .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
            .addAttribute("Normal", 4, GpuFormat.RGB8_SNORM)
            .build()
    }

    val RECT_SDF: RenderPipeline by lazy {
        val pipeline = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ancient", "pipeline/rect_sdf"))
            .withVertexShader(Identifier.fromNamespaceAndPath("ancient", "core/rect_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ancient", "core/rect_sdf"))
            .withVertexBinding(0, RECT_SDF_FORMAT)
            .build()
        RenderPipelines.register(pipeline)
        pipeline
    }
    val BLUR_RECT_SDF: RenderPipeline by lazy {
        val pipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ancient", "pipeline/blur_rect_sdf"))
            .withVertexShader(Identifier.fromNamespaceAndPath("ancient", "core/blur_rect_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ancient", "core/blur_rect_sdf"))
            .withVertexBinding(0, BLUR_RECT_SDF_FORMAT)
            .build()
        RenderPipelines.register(pipeline)
        pipeline
    }

    val KAWASE_BLUR: RenderPipeline by lazy {
        val pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ancient", "pipeline/kawase_blur"))
            .withVertexShader(Identifier.fromNamespaceAndPath("ancient", "core/kawase"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ancient", "core/kawase"))
            .withBindGroupLayout(
                BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("KawaseConfig", UniformType.UNIFORM_BUFFER)
                    .build()
            )
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build()
        RenderPipelines.register(pipeline)
        pipeline
    }


    val CORNER_SDF: RenderPipeline by lazy {
        val pipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ancient", "pipeline/corner_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ancient", "core/corner_sdf"))
            .build()
        RenderPipelines.register(pipeline)
        pipeline
    }

    val M3_SHAPE_SDF: RenderPipeline by lazy {
        val pipeline = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ancient", "pipeline/m3_shape_sdf"))
            .withVertexShader(Identifier.fromNamespaceAndPath("ancient", "core/m3_shape_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ancient", "core/m3_shape_sdf"))
            .withVertexBinding(0, RECT_SDF_FORMAT)
            .build()
        RenderPipelines.register(pipeline)
        pipeline
    }

    fun init() {
        RECT_SDF
        BLUR_RECT_SDF
        CORNER_SDF
        M3_SHAPE_SDF
        KAWASE_BLUR
    }
}
