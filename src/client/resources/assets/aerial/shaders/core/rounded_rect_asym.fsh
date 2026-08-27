#version 330
#moj_import <minecraft:dynamictransforms.glsl>

in vec2 vUv;
flat in vec2 vHalfSize;
flat in float vRadius;
in vec4 vColor;

out vec4 fragColor;

uniform sampler2D Sampler0;

float median(vec3 v) {
    return max(min(v.r, v.g), min(max(v.r, v.g), v.b));
}

const float FIELD_RANGE = 0.25;
const float FIELD_DOMAIN = 1.5;

float fieldDistance(vec2 q) {
    return (median(texture(Sampler0, q / FIELD_DOMAIN).rgb) - 0.5) * (2.0 * FIELD_RANGE);
}

float pixelWidth(float dist) {
    return max(length(vec2(dFdx(dist), dFdy(dist))), 1e-5);
}

void main() {

    float r = vUv.y > 0.0 ? vRadius : 0.0;
    vec2 q = abs(vUv) - vHalfSize + r;
    float inside = min(max(q.x, q.y), 0.0);

    float dist = r < 3.0
            ? length(max(q, 0.0)) + inside - r
            : fieldDistance(max(q, 0.0) / r) * r + inside;

    float aa = pixelWidth(dist);
    float alpha = clamp(0.5 - dist / aa, 0.0, 1.0);
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(vColor.rgb, vColor.a * alpha) * ColorModulator;
}
