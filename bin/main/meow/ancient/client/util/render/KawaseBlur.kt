package meow.ancient.client.util.render

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.Minecraft
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.Optional
import java.util.function.Supplier

object KawaseBlur {
    private const val UNIFORM_SIZE = 16
    private val passLabel = Supplier { "Ancient Kawase Blur" }
    private var targetA: TextureTarget? = null
    private var targetB: TextureTarget? = null
    private var uniformBuffer: GpuBuffer? = null
    private var uniformSlice: GpuBufferSlice? = null
    private var uniformData: ByteBuffer? = null

    var currentTextureView: GpuTextureView? = null
        private set

    val sampler: GpuSampler
        get() = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    fun hasBlur(): Boolean = currentTextureView != null

    fun updateBlur(radius: Float, iterations: Int) {
        currentTextureView = null
        val mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget()
        val mainView = mainTarget.colorTextureView ?: return
        if (mainTarget.width <= 0 || mainTarget.height <= 0) return

        val blurWidth = (mainTarget.width / 2).coerceAtLeast(1)
        val blurHeight = (mainTarget.height / 2).coerceAtLeast(1)
        var firstTarget = targetA
        var secondTarget = targetB
        if (firstTarget == null || secondTarget == null) {
            firstTarget = TextureTarget("Ancient Kawase A", blurWidth, blurHeight, GpuFormat.RGBA8_UNORM, null)
            secondTarget = TextureTarget("Ancient Kawase B", blurWidth, blurHeight, GpuFormat.RGBA8_UNORM, null)
            targetA = firstTarget
            targetB = secondTarget
        } else if (firstTarget.width != blurWidth || firstTarget.height != blurHeight) {
            firstTarget.resize(blurWidth, blurHeight)
            secondTarget.resize(blurWidth, blurHeight)
        }

        val firstView = firstTarget.colorTextureView ?: return
        val secondView = secondTarget.colorTextureView ?: return
        val device = RenderSystem.getDevice()
        var buffer = uniformBuffer
        if (buffer == null) {
            buffer = device.createBuffer(passLabel, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, UNIFORM_SIZE.toLong())
            uniformBuffer = buffer
            uniformSlice = buffer.slice()
            uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE)
        }
        val data = uniformData!!
        val slice = uniformSlice!!
        val encoder = device.createCommandEncoder()
        val pipeline = RenderSystem.getCompiledPipeline(VulkanShapePipelines.KAWASE_BLUR)
        val linearSampler = sampler
        var source = mainView
        var destination = firstView
        val passCount = iterations.coerceAtLeast(1)
        for (iteration in 0 until passCount) {
            val offset = if (iteration == 0) radius else radius * (iteration + 1) * 0.75f
            data.putFloat(0, offset / source.getWidth(0))
            data.putFloat(4, offset / source.getHeight(0))
            data.putLong(8, 0L)
            encoder.writeToBuffer(slice, data)
            encoder.createRenderPass(passLabel, destination, Optional.empty()).use { pass ->
                pass.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("KawaseConfig", slice)
                pass.setUniform("InSampler", source, linearSampler)
                pass.draw(3, 1, 0, 0)
            }
            source = destination
            destination = if (destination === firstView) secondView else firstView
        }
        currentTextureView = source
    }

    fun close() {
        currentTextureView = null
        targetA?.destroyBuffers()
        targetB?.destroyBuffers()
        targetA = null
        targetB = null
        uniformBuffer?.close()
        uniformBuffer = null
        uniformSlice = null
        uniformData?.let(MemoryUtil::memFree)
        uniformData = null
    }
}
