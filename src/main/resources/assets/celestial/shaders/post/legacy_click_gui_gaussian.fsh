#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
in vec2 sampleStep;

out vec4 fragColor;

const float SIGMA = 7.5;
const float NORMALIZER = 0.05319230405352436;

float gaussianWeight(float distance) {
    return NORMALIZER * exp(-(distance * distance) / (2.0 * SIGMA * SIGMA));
}

void main() {
    vec3 blurred = texture(InSampler, texCoord).rgb * gaussianWeight(0.0);
    for (int sampleIndex = 1; sampleIndex <= 15; ++sampleIndex) {
        vec2 offset = sampleStep * float(sampleIndex);
        float weight = gaussianWeight(float(sampleIndex));
        blurred += texture(InSampler, texCoord + offset).rgb * weight;
        blurred += texture(InSampler, texCoord - offset).rgb * weight;
    }
    fragColor = vec4(blurred, 1.0);
}
