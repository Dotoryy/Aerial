#version 330
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float DISTANCE_RANGE = 10.0;

float median(vec3 v) {
    return max(min(v.r, v.g), min(max(v.r, v.g), v.b));
}

float screenPxRange() {
    vec2 unitRange = vec2(DISTANCE_RANGE) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord0);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

float coverage(vec2 uv, float range) {
    float dist = median(texture(Sampler0, uv).rgb) - 0.5;
    return clamp(dist * range + 0.5, 0.0, 1.0);
}

void main() {
    float range = screenPxRange();

    vec2 dx = dFdx(texCoord0);
    vec2 dy = dFdy(texCoord0);

    vec2 o1 =  0.125 * dx + 0.375 * dy;
    vec2 o2 = -0.375 * dx + 0.125 * dy;
    vec2 o3 = -0.125 * dx - 0.375 * dy;
    vec2 o4 =  0.375 * dx - 0.125 * dy;

    float alpha = 0.25 * (coverage(texCoord0 + o1, range)
                        + coverage(texCoord0 + o2, range)
                        + coverage(texCoord0 + o3, range)
                        + coverage(texCoord0 + o4, range));

    if (alpha <= 0.0) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha) * ColorModulator;
}
