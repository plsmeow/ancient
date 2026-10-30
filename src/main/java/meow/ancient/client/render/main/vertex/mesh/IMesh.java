package meow.ancient.client.render.main.vertex.mesh;

import meow.ancient.client.render.main.buffer.GpuBuffer;
import meow.ancient.client.render.main.vertex.DrawMode;
import meow.ancient.client.render.main.vertex.format.VertexFormat;

public interface IMesh extends AutoCloseable {
    int getVertexCount();
    int getIndexCount();
    
    GpuBuffer getVertexBuffer();
    GpuBuffer getIndexBuffer();
    DrawMode getDrawMode();
    VertexFormat getVertexFormat();

    @Override void close();
}
