package meow.ancient.client.listener.listeners

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.backend.opengl.GlDevice
import com.mojang.renderpearl.backend.opengl.GlStateManager
import com.mojang.renderpearl.frontend.FrontendGpuDevice
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.interfaces.IRenderable
import meow.ancient.client.listener.Listener
import meow.ancient.client.render.main.ChromaLoaders
import meow.ancient.client.render.main.ChromaRenderer
import meow.ancient.client.render.main.blend.DstFactor
import meow.ancient.client.render.main.blend.SrcFactor
import meow.ancient.client.render.main.compile.GlShaderLibrary
import meow.ancient.client.render.main.compile.GlobalChromaCompiler
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.TextureLoader
import meow.ancient.client.util.render.engine.Renderable
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.LayerControl
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.client.util.render.engine.dispatcher.RenderMetrics
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.client.util.render.font.MsdfTextRenderer
import meow.ancient.client.util.render.rect.RectRenderer
import meow.ancient.event.EventManager
import meow.ancient.event.events.OverlayRenderEvent
import meow.ancient.module.modules.render.HudModule
import meow.ancient.module.modules.render.TagsModule
import meow.ancient.module.modules.other.TestModule
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import meow.ancient.event.events.WorldRenderEvent
object RenderListener : Listener() {
    var loaded = false
        private set

    private var shaderLibsRegistered = false
    private var chromaInitialized = false
    private var cachedOpenGLActive: Boolean? = null

    @JvmField
    val ALL_GUI_PIPELINES = arrayOf(
        ClientRenderPipeline.LOW, ClientRenderPipeline.MEDIUM, ClientRenderPipeline.HIGH,
        ClientRenderPipeline.HUD_RECT, ClientRenderPipeline.HUD_SPECIAL, ClientRenderPipeline.HUD_TEXT,
        ClientRenderPipeline.GUI_RECT, ClientRenderPipeline.GUI_SPECIAL, ClientRenderPipeline.GUI_TEXT,
        ClientRenderPipeline.WINDOW_RECT, ClientRenderPipeline.WINDOW_SPECIAL, ClientRenderPipeline.WINDOW_TEXT,
        ClientRenderPipeline.POPUP_RECT, ClientRenderPipeline.POPUP_SPECIAL, ClientRenderPipeline.POPUP_TEXT,
        ClientRenderPipeline.FIRST_RECT, ClientRenderPipeline.FIRST_SPECIAL, ClientRenderPipeline.FIRST_TEXT
    )

    private val cachedOverlayEvent = OverlayRenderEvent()

    override fun init() {
        if (isOpenGLActive()) {
            ensureChromaInitialized()
            ensureShaderLibsRegistered()
        }
        LevelRenderEvents.END_MAIN.register { context ->
            if (!inGame) return@register
            val camera = mc.gameRenderer.mainCamera()
            val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            EventManager.post(WorldRenderEvent(context.poseStack(), camera, delta, context))
        }
    }
    fun hookRender(partialTicks: Float, vararg pipelines: ClientRenderPipeline) {
        hookRenderArray(partialTicks, pipelines)
    }

    fun renderHud(guiGraphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!inGame) return

        RenderUtils.currentGraphics = guiGraphics
        try {
            val partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false)
            val mouseX = InputListener.mouseX()
            val mouseY = InputListener.mouseY()

            val event = cachedOverlayEvent.update(partialTicks, mouseX, mouseY)
            if (TagsModule.isEnabled()) {
                TagsModule.renderItems(guiGraphics, partialTicks)
            }
            if (isOpenGLActive()) {
                if (HudModule.isEnabled()) HudModule.onOverlay(event)
            } else {
                EventManager.post(event)
            }
            TestModule.renderShapesShowcase(guiGraphics, partialTicks)
        } finally {
            RenderUtils.currentGraphics = null
        }
    }

    fun renderChatHud(guiGraphics: GuiGraphicsExtractor) {
        if (!inGame) return
        val previousGraphics = RenderUtils.currentGraphics
        RenderUtils.currentGraphics = guiGraphics
        try {
            if (HudModule.isEnabled()) HudModule.renderChatOverlay()
            TestModule.renderShapesShowcase(guiGraphics, 0f)
        } finally {
            RenderUtils.currentGraphics = previousGraphics
        }
    }

    fun isOpenGLActive(): Boolean {
        cachedOpenGLActive?.let { return it }

        val device = RenderSystem.tryGetDevice() ?: return false
        val detected = when {
            device is FrontendGpuDevice -> device.backend is GlDevice
            device is GlDevice -> true
            else -> {
                val backendName = runCatching {
                    val info = device.javaClass.getMethod("getDeviceInfo").invoke(device)
                    info?.javaClass?.getMethod("backendName")?.invoke(info)?.toString()
                }.getOrNull()
                backendName?.contains("OpenGL", ignoreCase = true)
                    ?: device.javaClass.simpleName.contains("Gl", ignoreCase = true)
            }
        }
        cachedOpenGLActive = detected
        return detected
    }

    fun hookRenderArray(partialTicks: Float, pipelines: Array<out ClientRenderPipeline>) {
        if (!isOpenGLActive()) return
        if (!inGame && mc.gui.screen() == null) return

        init()

        if (!loaded) {
            LayerControl.loadShaders(MsdfTextRenderer, RectRenderer)
            LayerControl.loadRender()

            TextureLoader.load()
            Fonts.preload()

            loaded = true
            return
        }

        val mouseX = InputListener.mouseX()
        val mouseY = InputListener.mouseY()

        RenderMetrics.onFrameStart()

        MatrixControl.unscaledProjection()
        MatrixControl.reset()
        GlStateManager._disableDepthTest()

        ChromaRenderer.bindMainFramebuffer()
        ChromaRenderer.applyBlend(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA, SrcFactor.ONE, DstFactor.ZERO)

        if (inGame) {
            EventManager.post(cachedOverlayEvent.update(partialTicks, mouseX, mouseY))
        }

        val currentScreen = mc.gui.screen()
        if (currentScreen is IRenderable) {
            currentScreen.render(mouseX, mouseY)
        }

        LayerControl.flushPipelinesArray(pipelines)
        ChromaRenderer.endFrame()
        GlStateManager._enableDepthTest()
    }

    private fun ensureShaderLibsRegistered() {
        if (shaderLibsRegistered) return
        GlobalChromaCompiler.registerShaderLibraries(
            registerShaderLib("shapes"),
            registerShaderLib("math_utils"),
            registerShaderLib("matrices"),
            registerShaderLib("scissor_check")
        )
        shaderLibsRegistered = true
    }

    private fun ensureChromaInitialized() {
        if (chromaInitialized) return
        if (ChromaRenderer.bufferIdGetter != null) {
            chromaInitialized = true
            return
        }

        ChromaRenderer.init(
            { glBuffer -> glBuffer.handle() },
            { mc.window.guiScale }
        )
        chromaInitialized = true
    }

    private fun registerShaderLib(file: String): GlShaderLibrary {
        return ChromaLoaders.IN_JAR.createShaderLibraryBuilder()
            .name(file)
            .library("${Renderable.SHADER_INCLUDE_PATH}$file.glsl")
            .build()
    }
}

