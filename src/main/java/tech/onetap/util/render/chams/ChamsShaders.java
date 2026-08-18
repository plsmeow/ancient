package tech.onetap.util.render.chams;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class ChamsShaders {
    private static final String RESOURCE_ROOT = "/assets/mre/shaders/chams/";
    private static final String[] MODES = {"Solid", "Outline", "Gradient", "Rainbow", "Bloom"};

    private static ChamsShaders instance;

    private final Map<String, Integer> programs = new HashMap<>();
    private final Map<String, Integer> uniformCache = new HashMap<>();

    private SimpleFramebuffer framebuffer;
    private int quadVao = -1;
    private int quadVbo = -1;
    private int whiteTexture = -1;
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
        return !failed;
    }

    private void init() {
        try {
            String vertexSource = readResource("base.vert");
            int vertexShader = compile(GL20.GL_VERTEX_SHADER, vertexSource, "base.vert");

            for (String mode : MODES) {
                String fragmentName = fragmentName(mode);
                int fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, readResource(fragmentName), fragmentName);

                int program = GL20.glCreateProgram();
                GL20.glAttachShader(program, vertexShader);
                GL20.glAttachShader(program, fragmentShader);
                GL20.glBindAttribLocation(program, 0, "pos");
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                    String log = GL20.glGetProgramInfoLog(program);
                    throw new IllegalStateException("failed to link " + fragmentName + ": " + log);
                }
                GL20.glDetachShader(program, vertexShader);
                GL20.glDetachShader(program, fragmentShader);
                GL20.glDeleteShader(fragmentShader);
                programs.put(mode, program);
            }

            GL20.glDeleteShader(vertexShader);
            createQuad();
            createWhiteTexture();
            initialized = true;
        } catch (Throwable throwable) {
            failed = true;
            System.err.println("[Chams] shader initialization failed: " + throwable.getMessage());
        }
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

    private void createWhiteTexture() {
        whiteTexture = GlStateManager._genTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(whiteTexture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        IntBuffer pixel = BufferUtils.createIntBuffer(1);
        pixel.put(0, 0xFFFFFFFF);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        GlStateManager._bindTexture(0);
    }

    public SimpleFramebuffer framebuffer(int width, int height) {
        if (framebuffer == null) {
            framebuffer = new SimpleFramebuffer(width, height, true);
        } else if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            framebuffer.resize(width, height);
        }
        return framebuffer;
    }

    public int program(String mode) {
        Integer program = programs.get(mode);
        return program == null ? 0 : program;
    }

    public int getQuadVao() {
        return quadVao;
    }

    public int getWhiteTexture() {
        return whiteTexture;
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

    private String fragmentName(String mode) {
        return mode.toLowerCase() + ".frag";
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
