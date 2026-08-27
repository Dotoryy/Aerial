#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vColor;

out vec4 fragColor;

const float BACKDROP_DOWNSCALE = 2.0;

void main() {
    vec2 uv = gl_FragCoord.xy / (vec2(textureSize(Sampler0, 0)) * BACKDROP_DOWNSCALE);
    vec3 blurred = texture(Sampler0, uv).rgb;
    fragColor = vec4(blurred * vColor.rgb, vColor.a) * ColorModulator;
}
