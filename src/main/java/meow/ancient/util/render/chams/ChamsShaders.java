package meow.ancient.util.render.chams;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class ChamsShaders {
    private static final String RESOURCE_ROOT = "/assets/mre/shaders/chams/";

    private static ChamsShaders instance;

    private int blurHProgram = -1;
    private int bloomProgram = -1;
    private int sydneyProgram = -1;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    private SimpleFramebuffer worldFramebuffer;
    private SimpleFramebuffer handFramebuffer;
    private SimpleFramebuffer blurFramebuffer;

    private int quadVao = -1;
    private int quadVbo = -1;
    private boolean initialized;
    private boolean failed;

    private ChamsShaders() {
    }

    public static ChamsShaders getInstance() {
        if (instance == null) {
            instance = new ChamsShaders();
        }
        return instance;
    }

    public boolean isReady() {
        if (failed) {
            return false;
        }
        if (!initialized) {
            init();
        }
        return !failed && bloomProgram > 0 && blurHProgram > 0 && sydneyProgram > 0;
    }

    private void init() {
        try {
            String vertexSource = readResource("base.vert");
            int vertexShader = compile(GL20.GL_VERTEX_SHADER, vertexSource, "base.vert");

            // Horizontal blur pass
            int blurHFragmentShader = compile(GL20.GL_FRAGMENT_SHADER, readResource("blur_h.frag"), "blur_h.frag");
            blurHProgram = linkProgram(vertexShader, blurHFragmentShader, "blur_h.frag");
            GL20.glDeleteShader(blurHFragmentShader);

            // Vertical blur + composite pass
            int bloomFragmentShader = compile(GL20.GL_FRAGMENT_SHADER, readResource("bloom.frag"), "bloom.frag");
            bloomProgram = linkProgram(vertexShader, bloomFragmentShader, "bloom.frag");
            GL20.glDeleteShader(bloomFragmentShader);

            // Sydney direct outline pass
            int sydneyFragmentShader = compile(GL20.GL_FRAGMENT_SHADER, readResource("sydney.frag"), "sydney.frag");
            sydneyProgram = linkProgram(vertexShader, sydneyFragmentShader, "sydney.frag");
            GL20.glDeleteShader(sydneyFragmentShader);

            GL20.glDeleteShader(vertexShader);

            createQuad();
            initialized = true;
        } catch (Throwable throwable) {
            failed = true;
            System.err.println("[Chams] shader initialization failed: " + throwable.getMessage());
        }
    }

    private int linkProgram(int vert, int frag, String name) {
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vert);
        GL20.glAttachShader(program, frag);
        GL20.glBindAttribLocation(program, 0, "pos");
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            throw new IllegalStateException("failed to link " + name + ": " + log);
        }

        GL20.glDetachShader(program, vert);
        GL20.glDetachShader(program, frag);
        return program;
    }

    private int compile(int type, String source, String name) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("failed to compile " + name + ": " + log);
        }
        return shader;
    }

    private void createQuad() {
        quadVao = GL30.glGenVertexArrays();
        quadVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(quadVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        float[] vertices = {
                -1f, -1f, 0f, 1f,
                1f, -1f, 0f, 1f,
                -1f, 1f, 0f, 1f,
                1f, 1f, 0f, 1f
        };
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 4, GL11.GL_FLOAT, false, 0, 0L);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public SimpleFramebuffer worldFramebuffer(int width, int height) {
        if (worldFramebuffer == null) {
            worldFramebuffer = new SimpleFramebuffer(width, height, true);
            worldFramebuffer.setTexFilter(GL11.GL_LINEAR);
        } else if (worldFramebuffer.textureWidth != width || worldFramebuffer.textureHeight != height) {
            worldFramebuffer.resize(width, height);
            worldFramebuffer.setTexFilter(GL11.GL_LINEAR);
        }
        return worldFramebuffer;
    }

    public SimpleFramebuffer handFramebuffer(int width, int height) {
        if (handFramebuffer == null) {
            handFramebuffer = new SimpleFramebuffer(width, height, true);
            handFramebuffer.setTexFilter(GL11.GL_LINEAR);
        } else if (handFramebuffer.textureWidth != width || handFramebuffer.textureHeight != height) {
            handFramebuffer.resize(width, height);
            handFramebuffer.setTexFilter(GL11.GL_LINEAR);
        }
        return handFramebuffer;
    }

    public SimpleFramebuffer blurFramebuffer(int width, int height) {
        if (blurFramebuffer == null) {
            blurFramebuffer = new SimpleFramebuffer(width, height, false);
            blurFramebuffer.setTexFilter(GL11.GL_LINEAR);
        } else if (blurFramebuffer.textureWidth != width || blurFramebuffer.textureHeight != height) {
            blurFramebuffer.resize(width, height);
            blurFramebuffer.setTexFilter(GL11.GL_LINEAR);
        }
        return blurFramebuffer;
    }

    public int getBlurHProgram() {
        return blurHProgram;
    }

    public int getBloomProgram() {
        return bloomProgram;
    }

    public int getSydneyProgram() {
        return sydneyProgram;
    }

    public int getQuadVao() {
        return quadVao;
    }

    private int location(int program, String name) {
        String key = program + ":" + name;
        Integer cached = uniformCache.get(key);
        if (cached != null) {
            return cached;
        }
        int location = GL20.glGetUniformLocation(program, name);
        uniformCache.put(key, location);
        return location;
    }

    public void set1i(int program, String name, int value) {
        int location = location(program, name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    public void set1f(int program, String name, float value) {
        int location = location(program, name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    public void set2f(int program, String name, float x, float y) {
        int location = location(program, name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    public void set4f(int program, String name, float x, float y, float z, float w) {
        int location = location(program, name);
        if (location != -1) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }

    private String readResource(String name) throws Exception {
        try (InputStream stream = ChamsShaders.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            if (stream == null) {
                throw new IllegalStateException("missing shader resource " + RESOURCE_ROOT + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
