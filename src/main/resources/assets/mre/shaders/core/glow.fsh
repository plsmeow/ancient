#version 150

#moj_import <mre:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 Size;        // outer quad size
uniform vec4 Radius;      // inner rect radius (TL, TR, BR, BL)
uniform float GlowRadius; // padding == falloff distance (px)
uniform float Softness;   // edge AA (px)
uniform float Intensity;

out vec4 OutColor;

float sdRoundRect(vec2 p, vec2 halfSize, float r) {
    vec2 d = abs(p) - (halfSize - vec2(r));
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

void main() {
    vec2 outer = Size;

    // centered pixel coords in outer quad
    vec2 p = FragCoord * outer - outer * 0.5;

    // inner rect half-size (outer minus padding)
    vec2 innerHalf = outer * 0.5 - vec2(GlowRadius);
    innerHalf = max(innerHalf, vec2(0.0));

    // choose corner radius by quadrant (approx, но выглядит топ для одинаковых радиусов)
    float r;
    if (p.x < 0.0) {
        r = (p.y < 0.0) ? Radius.x : Radius.w; // TL : BL
    } else {
        r = (p.y < 0.0) ? Radius.y : Radius.z; // TR : BR
    }
    r = clamp(r, 0.0, min(innerHalf.x, innerHalf.y));

    float dist = sdRoundRect(p, innerHalf, r); // <0 inside, >0 outside

    // remove fill inside
    float edge = smoothstep(-Softness, 0.0, dist);

    // glow falloff outside
    float outside = max(dist, 0.0);
    float falloff = 1.0 - smoothstep(0.0, GlowRadius, outside);

    float a = edge * falloff * Intensity * FragColor.a;
    if (a <= 0.001) discard;

    OutColor = vec4(FragColor.rgb, a);
}