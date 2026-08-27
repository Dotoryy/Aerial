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
    if (Radius < 0.5) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    vec2 sampleStep = (1.0 / InSize) * BlurDir;

    float sigma = Radius * 0.5;
    float twoSigmaSq = 2.0 * sigma * sigma;

    float stride = Radius < 4.0 ? 1.0 : 2.0;

    vec4 sum = vec4(0.0);
    float weightSum = 0.0;
    for (float a = 0.5; a <= Radius; a += stride) {
        float weight = exp(-(a * a) / twoSigmaSq);
        sum += texture(InSampler, texCoord + sampleStep * a) * weight;
        sum += texture(InSampler, texCoord - sampleStep * a) * weight;
        weightSum += 2.0 * weight;
    }

    fragColor = sum / weightSum;
}
