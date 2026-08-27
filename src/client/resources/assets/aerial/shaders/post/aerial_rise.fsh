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
    if (Radius < 1.0) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    vec2 texelSize = 1.0 / InSize;
    float sigma = Radius / 2.0;

    vec4 sum = texture(InSampler, texCoord);
    float weightSum = 1.0;

    for (float f = 1.0; f <= Radius; f++) {
        float multiplier = f / sigma;
        float weight = exp(-0.5 * multiplier * multiplier);
        vec2 offset = f * texelSize * BlurDir;
        sum += texture(InSampler, texCoord - offset) * weight;
        sum += texture(InSampler, texCoord + offset) * weight;
        weightSum += 2.0 * weight;
    }

    fragColor = sum / weightSum;
}
