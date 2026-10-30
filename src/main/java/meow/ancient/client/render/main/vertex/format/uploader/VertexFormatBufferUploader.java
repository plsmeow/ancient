package meow.ancient.client.render.main.vertex.format.uploader;

import meow.ancient.client.render.main.buffer.GpuBuffer;
import meow.ancient.client.render.main.vertex.format.VertexFormat;
import meow.ancient.client.render.main.vertex.format.VertexFormatBuffer;

public abstract class VertexFormatBufferUploader {
    public abstract void applyFormatToBuffer(GpuBuffer vertexBuffer, VertexFormat vertexFormat);
    public abstract VertexFormatBuffer createVertexFormatBuffer(VertexFormat vertexFormat);
}
