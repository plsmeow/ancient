#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D MaskSampler;
uniform sampler2D GlowSampler;

layout(std140) uniform ChamsConfig {
    vec4 OutlineColor;
    vec4 FillColor;
    vec4 GlowColor;
    vec2 TexelSize;
    float OutlineWidth;
    float GlowStrength;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    float mask = texture(MaskSampler, texCoord).a;
    float blurred = texture(GlowSampler, texCoord).a;
    float halo = blurred * (1.0 - mask);
    float alpha = clamp(halo * GlowStrength * 1.65, 0.0, 1.0) * GlowColor.a;
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(GlowColor.rgb * alpha, 0.0);
}
