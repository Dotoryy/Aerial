#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AerialBlurConfig {
    vec2 BlurDir;
    float Radius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec2 offset = oneTexel * (0.5 + Radius * 0.2);

    vec4 sum = texture(InSampler, texCoord) * 4.0;
    sum += texture(InSampler, texCoord - offset);
    sum += texture(InSampler, texCoord + offset);
    sum += texture(InSampler, texCoord + vec2(offset.x, -offset.y));
    sum += texture(InSampler, texCoord - vec2(offset.x, -offset.y));

    fragColor = sum * 0.125;
}
