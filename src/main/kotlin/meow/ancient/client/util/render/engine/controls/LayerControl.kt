package meow.ancient.client.util.render.engine.controls

import meow.ancient.client.util.render.engine.Renderable
import meow.ancient.client.util.render.engine.dispatcher.RenderDispatcher
import meow.ancient.client.render.main.vertex.mesh.MeshBuilder

object LayerControl {
    private val renderers: MutableList<Renderable> = ArrayList()
    private val dispatcher = RenderDispatcher()

    fun loadShaders(vararg renderable: Renderable) {
        renderers.addAll(renderable)
    }

    fun loadRender() {
        renderers.forEach { it.load() }
    }

    fun flushAll() {
        dispatcher.flushAll()
    }

    fun flushPipelines(vararg pipelines: ClientRenderPipeline) {
        dispatcher.flushPipelinesArray(pipelines)
    }

    fun flushPipelinesArray(pipelines: Array<out ClientRenderPipeline>) {
        dispatcher.flushPipelinesArray(pipelines)
    }
    fun getBuilder(
        pipeline: ClientRenderPipeline,
        renderer: Renderable,
        state: Any?
    ): MeshBuilder? {
        return dispatcher.getBuilder(pipeline, renderer, state)
    }
}