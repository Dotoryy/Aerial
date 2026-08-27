#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 vUv;
flat in vec2 vHalfExtent;
in vec4 vColor;

out vec4 fragColor;

uniform sampler2D Sampler1;

float median(vec3 v) {
    return max(min(v.r, v.g), min(max(v.r, v.g), v.b));
}

const float FIELD_RANGE = 0.25;
const float FIELD_DOMAIN = 1.5;

float fieldDistance(vec2 q) {
    return (median(texture(Sampler1, q / FIELD_DOMAIN).rgb) - 0.5) * (2.0 * FIELD_RANGE);
}

const float BACKDROP_DOWNSCALE = 2.0;

float pixelWidth(float dist) {
    return max(length(vec2(dFdx(dist), dFdy(dist))), 1e-5);
}

void main() {
    vec2 q = max(abs(vUv) - vHalfExtent, 0.0);
    float radiusPx = 1.0 / max(length(vec2(dFdx(vUv.x), dFdy(vUv.x))), 1e-6);

    float dist = radiusPx < 3.0 ? length(q) - 1.0 : fieldDistance(q);

    float aa = pixelWidth(dist);
    float alpha = clamp(0.5 - dist / aa, 0.0, 1.0);

    if (alpha <= 0.0) {
        discard;
    }

    vec2 uv = gl_FragCoord.xy / (vec2(textureSize(Sampler0, 0)) * BACKDROP_DOWNSCALE);
    vec3 blurred = texture(Sampler0, uv).rgb;

    fragColor = vec4(blurred * vColor.rgb, vColor.a * alpha) * ColorModulator;
}
