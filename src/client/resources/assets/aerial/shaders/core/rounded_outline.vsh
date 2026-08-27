#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 vUv;
flat out vec2 vHalfSize;
flat out float vRadius;
flat out float vThickness;
out vec4 vColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    vUv = UV0;
    vHalfSize = abs(UV0);
    vColor = Color;

    vRadius = floor(Position.z);
    vThickness = (Position.z - vRadius) * 256.0;
}
