#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;

out vec4 fragColor;

void main() {
    // Static horizontal Gaussian blur with dense 1-texel spacing (no gaps, zero banding)
    vec2 step = vec2(v_OneTexel.x, 0.0);

    float totalWeight = 0.0;
    float a = 0.0;

    for (int i = -12; i <= 12; i++) {
        float w = exp(-float(i * i) / 50.8);
        a += texture(u_Texture, v_TexCoord + step * float(i)).a * w;
        totalWeight += w;
    }

    a /= totalWeight;
    fragColor = vec4(a, a, a, a);
}
