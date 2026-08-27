#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 vUv;
flat in vec2 vHalfSize;
flat in float vRadius;
in vec4 vColor;

out vec4 fragColor;

const vec2 FACE_ORIGIN = vec2(0.125, 0.125);
const vec2 FACE_SIZE = vec2(0.125, 0.125);

uniform sampler2D Sampler1;

float median(vec3 v) {
    return max(min(v.r, v.g), min(max(v.r, v.g), v.b));
}

const float FIELD_RANGE = 0.25;
const float FIELD_DOMAIN = 1.5;

float fieldDistance(vec2 q) {
    return (median(texture(Sampler1, q / FIELD_DOMAIN).rgb) - 0.5) * (2.0 * FIELD_RANGE);
}

float pixelWidth(float dist) {
    return max(length(vec2(dFdx(dist), dFdy(dist))), 1e-5);
}

float roundedBoxDistance(vec2 p, vec2 halfSize, float radius) {
    vec2 inner = max(halfSize - radius, 0.0);
    vec2 q = max(abs(p) - inner, 0.0);

    return radius < 3.0 ? length(q) - radius : fieldDistance(q / radius) * radius;
}

void main() {
    float dist = roundedBoxDistance(vUv, vHalfSize, vRadius);

    float aa = pixelWidth(dist);
    float mask = clamp(0.5 - dist / aa, 0.0, 1.0);

    if (mask <= 0.0) {
        discard;
    }

    vec2 quadUv = vUv / (vHalfSize * 2.0) + 0.5;
    vec4 texel = texture(Sampler0, FACE_ORIGIN + quadUv * FACE_SIZE);

    fragColor = vec4(texel.rgb * vColor.rgb, texel.a * vColor.a * mask) * ColorModulator;
}
