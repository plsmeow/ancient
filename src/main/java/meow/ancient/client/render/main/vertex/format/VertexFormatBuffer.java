package meow.ancient.client.render.main.vertex.format;

import meow.ancient.client.render.main.buffer.GpuBuffer;

import java.util.concurrent.atomic.AtomicReference;

public record VertexFormatBuffer(int glId, VertexFormat vertexFormat, AtomicReference<GpuBuffer> buffer) {}
