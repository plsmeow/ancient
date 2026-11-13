package meow.ancient.client.util.render

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTextureView
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.module.modules.render.ChamsModule
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase
import org.joml.Matrix4f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.IdentityHashMap
import java.util.Optional
import java.util.function.Supplier

object ChamsRenderer {
    private const val UNIFORM_SIZE = 64
    private const val BLUR_UNIFORM_SIZE = 16
    private val maskLabel = Supplier { "Ancient Chams silhouette" }
    private val compositeLabel = Supplier { "Ancient Chams outline and fill" }
    private val blurLabel = Supplier { "Ancient Chams Gaussian glow" }
    private val glowLabel = Supplier { "Ancient Chams additive glow" }
    private val transparent = Vector4f(0f)
    private val discardedSubmits = FeatureRenderPhase.Output { _, _ -> }
    private val worldStorage = SubmitNodeStorage()
    private val handsStorage = SubmitNodeStorage()
    private val worldCollectors = IdentityHashMap<SubmitNodeCollector, ChamsSubmitNodeCollector>()
    private val handsCollectors = IdentityHashMap<SubmitNodeCollector, ChamsSubmitNodeCollector>()
    private val worldModelView = Matrix4f()
    private var worldProjection: GpuBufferSlice? = null
    private var worldProjectionType = ProjectionType.PERSPECTIVE
    private var worldCaptured = false
    private var handsCaptured = false
    private var maskTarget: TextureTarget? = null
    private var glowTargetA: TextureTarget? = null
    private var glowTargetB: TextureTarget? = null
    private var blurUniformBuffer: GpuBuffer? = null
    private var blurUniformSlice: GpuBufferSlice? = null
    private var blurUniformData: ByteBuffer? = null
    private var uniformBuffer: GpuBuffer? = null
    private var uniformData: ByteBuffer? = null
    private var uniformSlice: GpuBufferSlice? = null

    fun beginFrame() {
        clearStorage(worldStorage)
        clearStorage(handsStorage)
        worldCaptured = false
        handsCaptured = false
        worldProjection = null
    }

    fun wrapWorld(collector: SubmitNodeCollector): SubmitNodeCollector {
        if (!worldCaptured) {
            worldModelView.set(RenderSystem.getModelViewStack())
            worldProjection = RenderSystem.getProjectionMatrixBuffer()
            worldProjectionType = RenderSystem.getProjectionType()
            worldCaptured = true
        }
        return worldCollectors[collector] ?: ChamsSubmitNodeCollector(collector, worldStorage).also {
            worldCollectors[collector] = it
        }
    }

    fun wrapHands(collector: SubmitNodeCollector): SubmitNodeCollector {
        handsCaptured = true
        return handsCollectors[collector] ?: ChamsSubmitNodeCollector(collector, handsStorage).also {
            handsCollectors[collector] = it
        }
    }

    fun renderWorld() {
        if (!worldCaptured || !ChamsModule.isEnabled()) return
        worldCaptured = false
        val projection = worldProjection ?: return
        val modelView = RenderSystem.getModelViewStack()
        RenderSystem.backupProjectionMatrix()
        modelView.pushMatrix()
        try {
            modelView.set(worldModelView)
            RenderSystem.setProjectionMatrix(projection, worldProjectionType)
            renderMask(worldStorage)
        } finally {
            modelView.popMatrix()
            RenderSystem.restoreProjectionMatrix()
        }
    }

    fun renderHands() {
        if (!handsCaptured || !ChamsModule.shouldRenderHands()) return
        handsCaptured = false
        renderMask(handsStorage)
    }

    private fun renderMask(storage: SubmitNodeStorage) {
        val main = mc.gameRenderer.mainRenderTarget()
        val mainView = main.colorTextureView ?: return
        if (main.width <= 0 || main.height <= 0) return
        for (collection in storage.submitsPerOrder.values) {
            for (phase in collection.allPhases()) {
                if (phase !== collection.outline) clearPhase(phase)
            }
        }
        if (storage.submitsPerOrder.values.none { !it.outline.isEmpty }) return
        var target = maskTarget
        if (target == null) {
            target = TextureTarget("Ancient Chams mask", main.width, main.height, GpuFormat.RGBA8_UNORM, null)
            maskTarget = target
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height)
        }
        val maskView = target.colorTextureView ?: return
        mc.gameRenderer.featureRenderDispatcher().prepareFrame(storage).use { frame ->
            RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(maskLabel, maskView, Optional.of(transparent)).use { pass ->
                    RenderSystem.bindDefaultUniforms(pass)
                    frame.executeOutline(pass)
                }
        }
        val device = RenderSystem.getDevice()
        var buffer = uniformBuffer
        if (buffer == null) {
            buffer = device.createBuffer(compositeLabel, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, UNIFORM_SIZE.toLong())
            uniformBuffer = buffer
            uniformSlice = buffer.slice()
            uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE)
        }
        val data = uniformData!!
        writeColor(data, 0, ChamsModule.outlineColor())
        writeColor(data, 16, ChamsModule.fillColor())
        writeColor(data, 32, ChamsModule.glowColor())
        data.putFloat(48, 1f / main.width)
        data.putFloat(52, 1f / main.height)
        data.putFloat(56, ChamsModule.lineWidth.toFloat())
        data.putFloat(60, ChamsModule.glowStrength.toFloat())
        val encoder = device.createCommandEncoder()
        val slice = uniformSlice!!
        encoder.writeToBuffer(slice, data)
        val pipeline = RenderSystem.getCompiledPipeline(ChamsPipelines.COMPOSITE)
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        if (ChamsModule.glow) {
            val glowView = blurGlow(maskView, main.width, main.height)
            val glowPipeline = RenderSystem.getCompiledPipeline(ChamsPipelines.GLOW_COMPOSITE)
            encoder.createRenderPass(glowLabel, mainView, Optional.empty()).use { pass ->
                pass.setPipeline(glowPipeline)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("MaskSampler", maskView, sampler)
                pass.setUniform("GlowSampler", glowView, sampler)
                pass.setUniform("ChamsConfig", slice)
                pass.draw(3, 1, 0, 0)
            }
        }
        encoder.createRenderPass(compositeLabel, mainView, Optional.empty()).use { pass ->
            pass.setPipeline(pipeline)
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("MaskSampler", maskView, sampler)
            pass.setUniform("ChamsConfig", slice)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun blurGlow(source: GpuTextureView, width: Int, height: Int): GpuTextureView {
        val blurWidth = (width / 2).coerceAtLeast(1)
        val blurHeight = (height / 2).coerceAtLeast(1)
        var first = glowTargetA
        var second = glowTargetB
        if (first == null || second == null) {
            first = TextureTarget("Ancient Chams glow A", blurWidth, blurHeight, GpuFormat.RGBA16_FLOAT, null)
            second = TextureTarget("Ancient Chams glow B", blurWidth, blurHeight, GpuFormat.RGBA16_FLOAT, null)
            glowTargetA = first
            glowTargetB = second
        } else if (first.width != blurWidth || first.height != blurHeight) {
            first.resize(blurWidth, blurHeight)
            second.resize(blurWidth, blurHeight)
        }
        val firstView = checkNotNull(first.colorTextureView)
        val secondView = checkNotNull(second.colorTextureView)
        val device = RenderSystem.getDevice()
        var buffer = blurUniformBuffer
        if (buffer == null) {
            buffer = device.createBuffer(blurLabel, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, BLUR_UNIFORM_SIZE.toLong())
            blurUniformBuffer = buffer
            blurUniformSlice = buffer.slice()
            blurUniformData = MemoryUtil.memAlloc(BLUR_UNIFORM_SIZE)
        }
        val data = blurUniformData!!
        val slice = blurUniformSlice!!
        val pipeline = RenderSystem.getCompiledPipeline(ChamsPipelines.GLOW_BLUR)
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        val radius = ChamsModule.glowRadius.toFloat() / 3.2307692f
        val encoder = device.createCommandEncoder()
        var input = source
        for (axis in 0..1) {
            data.putFloat(0, if (axis == 0) radius / width else 0f)
            data.putFloat(4, if (axis == 1) radius / height else 0f)
            data.putLong(8, 0L)
            encoder.writeToBuffer(slice, data)
            val destination = if (axis == 0) firstView else secondView
            encoder.createRenderPass(blurLabel, destination, Optional.empty()).use { pass ->
                pass.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("InSampler", input, sampler)
                pass.setUniform("GlowBlurConfig", slice)
                pass.draw(3, 1, 0, 0)
            }
            input = destination
        }
        return secondView
    }

    private fun writeColor(data: ByteBuffer, offset: Int, color: Color4b) {
        data.putFloat(offset, color.r / 255f)
        data.putFloat(offset + 4, color.g / 255f)
        data.putFloat(offset + 8, color.b / 255f)
        data.putFloat(offset + 12, color.a / 255f)
    }

    private fun clearPhase(phase: FeatureRenderPhase<*>) {
        if (phase is SimpleFeatureRenderPhase) phase.clear()
        else if (!phase.isEmpty) phase.sortInto(discardedSubmits)
    }

    private fun clearStorage(storage: SubmitNodeStorage) {
        for (collection in storage.submitsPerOrder.values) {
            for (phase in collection.allPhases()) clearPhase(phase)
        }
        clearPhase(storage.seeThrough())
    }

    fun close() {
        beginFrame()
        worldCollectors.clear()
        handsCollectors.clear()
        maskTarget?.destroyBuffers()
        maskTarget = null
        glowTargetA?.destroyBuffers()
        glowTargetB?.destroyBuffers()
        glowTargetA = null
        glowTargetB = null
        blurUniformBuffer?.close()
        blurUniformBuffer = null
        blurUniformSlice = null
        blurUniformData?.let(MemoryUtil::memFree)
        blurUniformData = null
        uniformBuffer?.close()
        uniformSlice = null
        uniformBuffer = null
        uniformData?.let(MemoryUtil::memFree)
        uniformData = null
    }
}
