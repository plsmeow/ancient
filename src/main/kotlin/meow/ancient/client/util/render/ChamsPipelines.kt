package meow.ancient.client.util.render

import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.UniformType
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object ChamsPipelines {
    val COMPOSITE: RenderPipeline by lazy {
        RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("ancient", "pipeline/chams_outline"))
                .withVertexShader(Identifier.fromNamespaceAndPath("ancient", "core/kawase"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("ancient", "core/chams_outline"))
                .withBindGroupLayout(
                    BindGroupLayout.builder()
                        .withUniform("MaskSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("ChamsConfig", UniformType.UNIFORM_BUFFER)
                        .build()
                )
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .build()
        )
    }

    val GLOW_BLUR: RenderPipeline by lazy {
        RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("ancient", "pipeline/chams_glow_blur"))
                .withVertexShader(Identifier.fromNamespaceAndPath("ancient", "core/kawase"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("ancient", "core/chams_glow_blur"))
                .withBindGroupLayout(
                    BindGroupLayout.builder()
                        .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("GlowBlurConfig", UniformType.UNIFORM_BUFFER)
                        .build()
                )
                .withColorTargetState(ColorTargetState(
                    java.util.Optional.empty(),
                    com.mojang.renderpearl.api.GpuFormat.RGBA16_FLOAT,
                    ColorTargetState.WRITE_ALL
                ))
                .withCull(false)
                .build()
        )
    }

    val GLOW_COMPOSITE: RenderPipeline by lazy {
        RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("ancient", "pipeline/chams_glow"))
                .withVertexShader(Identifier.fromNamespaceAndPath("ancient", "core/kawase"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("ancient", "core/chams_glow"))
                .withBindGroupLayout(
                    BindGroupLayout.builder()
                        .withUniform("MaskSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("GlowSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("ChamsConfig", UniformType.UNIFORM_BUFFER)
                        .build()
                )
                .withColorTargetState(ColorTargetState(BlendFunction.ADDITIVE))
                .withCull(false)
                .build()
        )
    }
}
