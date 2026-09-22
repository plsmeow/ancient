#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;

uniform float u_LineWidth;
uniform float u_FillOpacity;
uniform vec4 u_Color1;
uniform vec4 u_Color2;
uniform bool u_UseGradient;
uniform float u_Time;
uniform vec2 u_Size;

out vec4 fragColor;

float quad(float x) {
    return x * x;
}

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
    float maxSample = max(u_LineWidth, 1.0);
    int iWidth = int(clamp(ceil(maxSample), 1.0, 30.0));
    float divider = maxSample * (5.0 / 3.0);

    vec4 current = texture(u_Texture, v_TexCoord);

    if (current.a > 0.01) {
        if (u_FillOpacity <= 0.001) {
            discard;
        }
        vec4 color = getShadedColor(v_TexCoord);
        fragColor = vec4(color.rgb, u_FillOpacity * color.a);
    } else {
        float alpha = 0.0;
        vec4 color = getShadedColor(v_TexCoord);

        for (int x = -iWidth; x <= iWidth; x++) {
            for (int y = -iWidth; y <= iWidth; y++) {
                float d = length(vec2(float(x), float(y)));
                if (d <= maxSample) {
                    vec4 sampleTex = texture(u_Texture, v_TexCoord + vec2(float(x), float(y)) * v_OneTexel);
                    if (sampleTex.a > 0.01) {
                        alpha += max(0.0, (maxSample - d) / divider);
                    }
                }
            }
        }

        float finalAlpha = clamp(quad(alpha), 0.0, 1.0);
        if (finalAlpha <= 0.002) {
            discard;
        }

        fragColor = vec4(color.rgb, finalAlpha * color.a);
    }
}
