#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 vUv;
flat in vec2 vHalfSize;
flat in float vRadius;
flat in float vThickness;
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

float roundedBoxDistance(vec2 p, vec2 halfSize, float radius) {
    vec2 inner = max(halfSize - radius, 0.0);
    vec2 q = max(abs(p) - inner, 0.0);

    return radius < 3.0 ? length(q) - radius : fieldDistance(q / radius) * radius;
}

void main() {
    float dist = roundedBoxDistance(vUv, vHalfSize, vRadius);

    float aa = pixelWidth(dist);
    float outer = clamp(0.5 - dist / aa, 0.0, 1.0);

    float inner = clamp(0.5 - (dist + vThickness) / aa, 0.0, 1.0);
    float alpha = outer - inner;

    if (alpha <= 0.0) {
        discard;
    }

    fragColor = vec4(vColor.rgb, vColor.a * alpha) * ColorModulator;
}
