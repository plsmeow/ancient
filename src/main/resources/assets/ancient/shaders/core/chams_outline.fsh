#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D MaskSampler;

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

const vec2 DIRECTIONS[16] = vec2[16](
    vec2(1.0, 0.0), vec2(0.9238795, 0.3826834), vec2(0.7071068, 0.7071068), vec2(0.3826834, 0.9238795),
    vec2(0.0, 1.0), vec2(-0.3826834, 0.9238795), vec2(-0.7071068, 0.7071068), vec2(-0.9238795, 0.3826834),
    vec2(-1.0, 0.0), vec2(-0.9238795, -0.3826834), vec2(-0.7071068, -0.7071068), vec2(-0.3826834, -0.9238795),
    vec2(0.0, -1.0), vec2(0.3826834, -0.9238795), vec2(0.7071068, -0.7071068), vec2(0.9238795, -0.3826834)
);

void main() {
    float mask = texture(MaskSampler, texCoord).a;
    float expanded = mask;
    if (OutlineColor.a > 0.0) {
        for (int i = 0; i < 16; i++) {
            vec2 offset = DIRECTIONS[i] * TexelSize * OutlineWidth;
            expanded = max(expanded, texture(MaskSampler, texCoord + offset).a);
        }
    }
    float outlineAlpha = max(expanded - mask, 0.0) * OutlineColor.a;
    float fillAlpha = mask * FillColor.a;
    float alpha = outlineAlpha + fillAlpha * (1.0 - outlineAlpha);
    if (alpha <= 0.0) {
        discard;
    }
    vec3 color = (OutlineColor.rgb * outlineAlpha + FillColor.rgb * fillAlpha * (1.0 - outlineAlpha)) / alpha;
    fragColor = vec4(color, alpha);
}
