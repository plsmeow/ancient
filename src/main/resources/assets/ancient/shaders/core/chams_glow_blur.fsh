#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform GlowBlurConfig {
    vec2 SampleStep;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    float alpha = texture(InSampler, texCoord).a * 0.2270270270;
    alpha += texture(InSampler, texCoord + SampleStep * 1.3846153846).a * 0.3162162162;
    alpha += texture(InSampler, texCoord - SampleStep * 1.3846153846).a * 0.3162162162;
    alpha += texture(InSampler, texCoord + SampleStep * 3.2307692308).a * 0.0702702703;
    alpha += texture(InSampler, texCoord - SampleStep * 3.2307692308).a * 0.0702702703;
    fragColor = vec4(alpha);
}
