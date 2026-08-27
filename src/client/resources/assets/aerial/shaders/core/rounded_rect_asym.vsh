#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 vUv;
flat out vec2 vHalfSize;

flat out float vRadius;
out vec4 vColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    vUv = UV0;
    vHalfSize = abs(UV0);
    vRadius = Position.z;
    vColor = Color;
}
