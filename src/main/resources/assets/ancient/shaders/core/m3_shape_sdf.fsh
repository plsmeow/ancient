#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec2 fragCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 shapeSize;
layout(location = 3) in vec4 shapeParams;
layout(location = 4) in vec4 borderColor;
layout(location = 5) in float borderWidth;

layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530717958647692;

float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
}

float sdScallop(float r, float theta, float radius, int n, float lobeDistFrac, float lobeRadiusFrac, float coreRadiusFrac) {
    float sector = TWO_PI / float(n);
    float a = mod(theta + 0.5 * sector, sector) - 0.5 * sector;
    vec2 p = vec2(cos(a), sin(a)) * r;
    float dLobe = length(p - vec2(lobeDistFrac * radius, 0.0)) - (lobeRadiusFrac * radius);
    float dCore = r - (coreRadiusFrac * radius);
    return min(dLobe, dCore);
}

void main() {
    vec2 pos = fragCoord - shapeSize * 0.5;
    float radius = min(shapeSize.x, shapeSize.y) * 0.5;
    float r = length(pos);
    float theta = (r > 1e-5) ? atan(pos.y, pos.x) : 0.0;

    int type = int(shapeParams.x + 0.5);
    float dist = 0.0;

    // Row 1
    if (type == 0) {
        // Circle
        dist = r - 0.94 * radius;
    } else if (type == 1) {
        // Square
        dist = sdRoundedBox(pos, vec2(0.92 * radius), 0.36 * radius);
    } else if (type == 2) {
        // Slanted
        vec2 pS = vec2(pos.x - 0.22 * pos.y, pos.y);
        dist = sdRoundedBox(pS, vec2(0.72 * radius, 0.88 * radius), 0.30 * radius);
    } else if (type == 3) {
        // Arch
        vec2 pA = pos;
        pA.y -= 0.08 * radius;
        if (pA.y < 0.0) {
            dist = length(pA) - 0.88 * radius;
        } else {
            dist = sdRoundedBox(pA - vec2(0.0, 0.40 * radius), vec2(0.88 * radius, 0.40 * radius), 0.20 * radius);
        }
    } else if (type == 4) {
        // Semicircle
        vec2 pSemi = pos - vec2(0.0, 0.16 * radius);
        if (pSemi.y < 0.0) {
            dist = length(pSemi) - 0.88 * radius;
        } else {
            vec2 q = abs(pSemi) - vec2(0.88 * radius, 0.0);
            dist = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0);
            vec2 q2 = vec2(abs(pSemi.x) - (0.88 - 0.14) * radius, pSemi.y);
            if (q2.x > 0.0) dist = length(q2) - 0.14 * radius;
            else dist = pSemi.y;
        }
    } else if (type == 5) {
        // Oval
        float ca = 0.573576; // cos(55 deg)
        float sa = 0.819152; // sin(55 deg)
        vec2 qOv = vec2(ca * pos.x + sa * pos.y, -sa * pos.x + ca * pos.y);
        dist = (length(qOv / vec2(0.92, 0.56)) - radius) * 0.56;
    } else if (type == 6) {
        // Pill
        float caP = 0.939693; // cos(-20 deg)
        float saP = -0.342020;
        vec2 qPill = vec2(caP * pos.x + saP * pos.y, -saP * pos.x + caP * pos.y);
        qPill.x = max(abs(qPill.x) - 0.32 * radius, 0.0);
        dist = length(qPill) - 0.58 * radius;

    // Row 2
    } else if (type == 7) {
        // Triangle
        vec2 pTri = pos + vec2(0.0, 0.22 * radius);
        const float kTri = 1.7320508;
        pTri.x = abs(pTri.x) - 0.85 * radius;
        pTri.y = pTri.y + (0.85 * radius) / kTri;
        if (pTri.x + kTri * pTri.y > 0.0) pTri = vec2(pTri.x - kTri * pTri.y, -kTri * pTri.x - pTri.y) * 0.5;
        pTri.x -= clamp(pTri.x, -1.70 * radius, 0.0);
        dist = -length(pTri) * sign(pTri.y) - 0.22 * radius;
    } else if (type == 8) {
        // Arrow
        vec2 pArr = pos;
        vec2 pTip = pArr - vec2(0.0, -0.65 * radius);
        float dTip = length(pTip) - 0.24 * radius;
        vec2 pLeftWing = pArr - vec2(-0.55 * radius, 0.52 * radius);
        vec2 pRightWing = pArr - vec2(0.55 * radius, 0.52 * radius);
        float dWings = min(length(pLeftWing), length(pRightWing)) - 0.26 * radius;
        float dBody = max(abs(pArr.x) * 1.15 + pArr.y * 0.65 - 0.75 * radius, pArr.y - 0.75 * radius);
        float dArrowOuter = min(min(dTip, dWings), dBody);
        float dBottomCut = -(length(pArr - vec2(0.0, 1.20 * radius)) - 0.68 * radius);
        dist = max(dArrowOuter, dBottomCut);
    } else if (type == 9) {
        // Fan
        vec2 pFan = pos - vec2(-0.50 * radius, 0.50 * radius);
        float dArc = length(pFan) - 1.32 * radius;
        vec2 qF = vec2(max(-pFan.x, 0.0), max(pFan.y, 0.0));
        dist = max(dArc, length(qF)) - 0.14 * radius;
    } else if (type == 10) {
        // Diamond
        vec2 pD = abs(vec2(pos.x + pos.y, pos.x - pos.y) * 0.70710678);
        dist = sdRoundedBox(pD, vec2(0.68 * radius), 0.28 * radius);
    } else if (type == 11) {
        // Clamshell
        vec2 pC = abs(pos);
        float dTopBot = pC.y - 0.38 * radius;
        float dSides = dot(pC, vec2(0.60, 0.80)) - 0.62 * radius;
        dist = max(dTopBot, dSides) - 0.18 * radius;
    } else if (type == 12) {
        // Pentagon
        vec2 pP = vec2(pos.x, -pos.y);
        const vec3 kP = vec3(0.809016994, 0.587785252, 0.726542528);
        pP.x = abs(pP.x);
        pP -= 2.0 * min(dot(vec2(-kP.x, kP.y), pP), 0.0) * vec2(-kP.x, kP.y);
        pP -= 2.0 * min(dot(vec2(kP.x, kP.y), pP), 0.0) * vec2(kP.x, kP.y);
        pP -= vec2(clamp(pP.x, -0.72 * radius * kP.z, 0.72 * radius * kP.z), 0.72 * radius);
        dist = length(pP) * sign(pP.y) - 0.20 * radius;
    } else if (type == 13) {
        // Gem
        vec2 pG = abs(pos);
        float d1 = max(pG.x - 0.64 * radius, pG.y - 0.64 * radius);
        float d2 = (pG.x + pG.y) * 0.70710678 - 0.70 * radius;
        dist = max(d1, d2) - 0.18 * radius;

    // Row 3
    } else if (type == 14) {
        // Sunny
        float sector = TWO_PI / 8.0;
        float a = mod(theta + 0.5 * sector, sector) - 0.5 * sector;
        vec2 p = vec2(cos(a), sin(a)) * r;
        float dTip = length(p - vec2(0.68 * radius, 0.0)) - 0.28 * radius;
        float dCore = r - 0.70 * radius;
        dist = min(dTip, dCore);
    } else if (type == 15) {
        // Very sunny
        float sector = TWO_PI / 8.0;
        float a = mod(theta + 0.5 * sector, sector) - 0.5 * sector;
        vec2 p = vec2(cos(a), sin(a)) * r;
        float dTip = length(p - vec2(0.60 * radius, 0.0)) - 0.36 * radius;
        float dCore = r - 0.58 * radius;
        dist = min(dTip, dCore);
    } else if (type == 16) {
        // 4-sided cookie
        float sector = TWO_PI / 4.0;
        float a = mod(theta - 0.785398 + 0.5 * sector, sector) - 0.5 * sector;
        vec2 p = vec2(cos(a), sin(a)) * r;
        float dLobe = length(p - vec2(0.52 * radius, 0.0)) - 0.44 * radius;
        float dCore = r - 0.68 * radius;
        dist = min(dLobe, dCore);
    } else if (type == 17) {
        // 6-sided cookie
        dist = sdScallop(r, theta, radius, 6, 0.65, 0.32, 0.72);
    } else if (type == 18) {
        // 7-sided cookie
        dist = sdScallop(r, theta, radius, 7, 0.68, 0.28, 0.75);
    } else if (type == 19) {
        // 9-sided cookie
        dist = sdScallop(r, theta, radius, 9, 0.72, 0.25, 0.78);
    } else if (type == 20) {
        // 12-sided cookie
        dist = sdScallop(r, theta, radius, 12, 0.78, 0.20, 0.83);

    // Row 4
    } else if (type == 21) {
        // 4-leaf clover
        vec2 pCl = abs(pos);
        float dLeaf = length(pCl - vec2(0.42 * radius)) - 0.46 * radius;
        float dCore = r - 0.38 * radius;
        dist = min(dLeaf, dCore);
    } else if (type == 22) {
        // 8-leaf clover
        float sector = TWO_PI / 8.0;
        float a = mod(theta + 0.5 * sector, sector) - 0.5 * sector;
        vec2 p = vec2(cos(a), sin(a)) * r;
        float dLeaf = length(p - vec2(0.54 * radius, 0.0)) - 0.38 * radius;
        float dCore = r - 0.46 * radius;
        dist = min(dLeaf, dCore);
    } else if (type == 23) {
        // Burst
        float sector = TWO_PI / 12.0;
        float a = mod(theta + 0.5 * sector, sector) - 0.5 * sector;
        vec2 p = vec2(cos(a), sin(a)) * r;
        float dRay = max(abs(p.y) * 2.2 + p.x - 0.94 * radius, p.x - 0.94 * radius);
        float dCore = r - 0.60 * radius;
        dist = min(dRay, dCore);
    } else if (type == 24) {
        // Soft burst
        float sector = TWO_PI / 12.0;
        float a = mod(theta + 0.5 * sector, sector) - 0.5 * sector;
        vec2 p = vec2(cos(a), sin(a)) * r;
        float dTip = length(p - vec2(0.74 * radius, 0.0)) - 0.22 * radius;
        float dCore = r - 0.66 * radius;
        dist = min(dTip, dCore);
    } else if (type == 25) {
        // Boom
        float sector = TWO_PI / 16.0;
        float a = mod(theta + 0.5 * sector, sector) - 0.5 * sector;
        vec2 p = vec2(cos(a), sin(a)) * r;
        float dNeedle = max(abs(p.y) * 4.5 + p.x - 0.96 * radius, p.x - 0.96 * radius);
        float dCore = r - 0.36 * radius;
        dist = min(dNeedle, dCore);
    } else if (type == 26) {
        // Soft boom
        float sector = TWO_PI / 16.0;
        float a = mod(theta + 0.5 * sector, sector) - 0.5 * sector;
        vec2 p = vec2(cos(a), sin(a)) * r;
        float dPetal = length(vec2(p.x - clamp(p.x, 0.38 * radius, 0.86 * radius), p.y)) - 0.08 * radius;
        float dCore = r - 0.38 * radius;
        dist = min(dPetal, dCore);
    } else if (type == 27) {
        // Flower
        float sector = TWO_PI / 8.0;
        float a = mod(theta + 0.5 * sector, sector) - 0.5 * sector;
        vec2 p = vec2(cos(a), sin(a)) * r;
        float dPetal = length(p - vec2(0.56 * radius, 0.0)) - 0.38 * radius;
        float dCore = r - 0.44 * radius;
        dist = min(dPetal, dCore);

    // Row 5
    } else if (type == 28) {
        // Puffy
        vec2 pCore = vec2(max(abs(pos.x) - 0.32 * radius, 0.0), pos.y);
        float dCore = length(pCore) - 0.36 * radius;
        vec2 pSym = abs(pos);
        float dTopMid = length(vec2(pos.x, pSym.y - 0.42 * radius)) - 0.32 * radius;
        float dTopSide = length(vec2(pSym.x - 0.40 * radius, pSym.y - 0.36 * radius)) - 0.30 * radius;
        float dEnd = length(vec2(pSym.x - 0.60 * radius, pSym.y - 0.16 * radius)) - 0.28 * radius;
        dist = min(min(min(dTopMid, dTopSide), dEnd), dCore);
    } else if (type == 29) {
        // Puffy diamond
        vec2 pAbs = abs(pos);
        float dCardX = length(vec2(pAbs.x - 0.48 * radius, pos.y)) - 0.42 * radius;
        float dCardY = length(vec2(pos.x, pAbs.y - 0.48 * radius)) - 0.42 * radius;
        float dDiag = length(pAbs - vec2(0.30 * radius)) - 0.28 * radius;
        float dCore = r - 0.46 * radius;
        dist = min(min(min(dCardX, dCardY), dDiag), dCore);
    } else if (type == 30) {
        // Ghost-ish
        vec2 pG = pos;
        float dTop = length(vec2(pG.x, min(pG.y + 0.05 * radius, 0.0))) - 0.85 * radius;
        float dBody = sdRoundedBox(pG - vec2(0.0, 0.32 * radius), vec2(0.85 * radius, 0.38 * radius), 0.18 * radius);
        float dGhost = min(dTop, dBody);
        float dIndent = -(length(vec2(abs(pG.x) - 0.42 * radius, pG.y - 0.72 * radius)) - 0.26 * radius);
        dist = max(dGhost, dIndent);
    } else if (type == 31) {
        // Pixel circle
        float grid = (2.0 * radius) / 11.0;
        vec2 cell = floor(pos / grid + 0.5);
        vec2 local = abs(pos - cell * grid);
        float dCell = max(local.x, local.y) - 0.5 * grid;
        bool inShape = length(cell * grid) <= 0.86 * radius;
        dist = inShape ? dCell : max(-dCell, length(pos) - 0.90 * radius);
    } else if (type == 32) {
        // Pixel triangle
        float grid = (1.8 * radius) / 9.0;
        vec2 pGrid = pos - vec2(-0.80 * radius, 0.80 * radius);
        vec2 cell = floor(vec2(pGrid.x, -pGrid.y) / grid + 0.5);
        vec2 local = abs(vec2(pGrid.x, -pGrid.y) - cell * grid);
        float dCell = max(local.x, local.y) - 0.5 * grid;
        bool inShape = cell.x >= 0.0 && cell.y >= 0.0 && (cell.x + cell.y <= 8.0);
        dist = inShape ? dCell : max(-dCell, max(-pGrid.x, pGrid.y));
    } else if (type == 33) {
        // Bun
        vec2 pB = vec2(pos.x, abs(pos.y) - 0.28 * radius);
        pB.x = max(abs(pB.x) - 0.35 * radius, 0.0);
        dist = length(pB) - 0.36 * radius;
    } else if (type == 34) {
        // Heart
        vec2 pH = vec2(abs(pos.x), -pos.y - 0.20 * radius) / max(radius * 0.92, 0.001);
        if (pH.y + pH.x > 1.0) {
            dist = (sqrt(dot(pH - vec2(0.25, 0.75), pH - vec2(0.25, 0.75))) - 0.35355339) * (radius * 0.92);
        } else {
            dist = (sqrt(min(dot(pH - vec2(0.00, 1.00), pH - vec2(0.00, 1.00)),
                             dot(pH - 0.5 * max(pH.x + pH.y, 0.0), pH - 0.5 * max(pH.x + pH.y, 0.0)))) * sign(pH.x - pH.y)) * (radius * 0.92);
        }
    } else {
        dist = r - radius;
    }

    float aa = max(0.5 * (fwidth(fragCoord.x) + fwidth(fragCoord.y)), 1e-4);
    float fillAlpha = clamp(0.5 - dist / aa, 0.0, 1.0);
    if (fillAlpha <= 0.001) {
        discard;
    }

    vec4 color;
    if (borderWidth <= 0.001) {
        color = vec4(vertexColor.rgb, vertexColor.a * fillAlpha);
    } else {
        float borderEdge = max(dist, -dist - borderWidth);
        float borderAlpha = clamp(0.5 - borderEdge / aa, 0.0, 1.0);
        color = mix(vertexColor, borderColor, borderAlpha);
        color.a *= fillAlpha;
    }

    color *= ColorModulator;
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
