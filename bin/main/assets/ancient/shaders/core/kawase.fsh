#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform KawaseConfig {
    vec2 Offset;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord + Offset);
    color += texture(InSampler, texCoord + vec2(-Offset.x, Offset.y));
    color += texture(InSampler, texCoord + vec2(Offset.x, -Offset.y));
    color += texture(InSampler, texCoord - Offset);
    fragColor = color * 0.25;
}
