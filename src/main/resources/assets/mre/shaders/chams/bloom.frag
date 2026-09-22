#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;      // Sharp silhouette
uniform sampler2D u_BlurTexture;  // Horizontally blurred silhouette

uniform float u_LineWidth;        // Line thickness (0.1 to 20.0)
uniform float u_FillOpacity;      // Fill opacity (0.0 to 1.0)
uniform vec4 u_Color1;
uniform vec4 u_Color2;
uniform bool u_UseGradient;
uniform float u_Time;
uniform vec2 u_Size;

out vec4 fragColor;

vec4 getShadedColor(vec2 coord) {
    if (!u_UseGradient) {
        return u_Color1;
    }
    vec2 pixelPos = coord * u_Size;
    float diagonal = (pixelPos.x + pixelPos.y) * 0.003;
    float t = sin(diagonal + u_Time) * 0.5 + 0.5;
    return mix(u_Color1, u_Color2, t);
}

void main() {
    // Static vertical Gaussian blur matching horizontal pass (1 texel spacing)
    vec2 step = vec2(0.0, v_OneTexel.y);

    float totalWeight = 0.0;
    float b = 0.0;

    for (int i = -12; i <= 12; i++) {
        float w = exp(-float(i * i) / 50.8);
        b += texture(u_BlurTexture, v_TexCoord + step * float(i)).a * w;
        totalWeight += w;
    }
    b /= totalWeight;

    // Sharp silhouette alpha (1.0 inside entity, 0.0 outside)
    float s = texture(u_Texture, v_TexCoord).a;

    // Distance metrics from silhouette perimeter (b == 0.5)
    float dOut = clamp(1.0 - b * 2.0, 0.0, 1.0);
    float dIn = clamp(b * 2.0 - 1.0, 0.0, 1.0);
    float distToEdge = mix(dOut, dIn, s);

    // Line thickness (scalable up to thick outlines with anti-aliasing)
    float w = clamp(u_LineWidth * 0.05, 0.005, 1.0);
    float lineAlpha = 1.0 - smoothstep(w * 0.4, w, distToEdge);

    // Natural built-in fade glow radiating outward and inward
    float fadeOut = pow(1.0 - dOut, 1.8) * 0.85;
    float fadeIn = pow(1.0 - dIn, 2.2) * 0.65;
    float glowFade = mix(fadeOut, fadeIn, s);

    // Combine contour and soft glow
    float glowTotal = max(lineAlpha, glowFade);

    // Subtle optional body fill
    float fillAlpha = s * u_FillOpacity;

    float totalAlpha = clamp(glowTotal + fillAlpha, 0.0, 1.0);
    if (totalAlpha <= 0.002) {
        discard;
    }

    vec4 color = getShadedColor(v_TexCoord);
    fragColor = vec4(color.rgb, totalAlpha * color.a);
}
