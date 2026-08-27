#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vColor;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    float alpha = texture(Sampler0, uv).a * vColor.a;
    fragColor = vec4(0.0, 0.0, 0.0, alpha) * ColorModulator;
}
