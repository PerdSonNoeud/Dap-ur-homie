#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    // Discard fully transparent pixels (same as vanilla entity_cutout)
    if (color.a < 0.1) discard;
    // Force black, preserve alpha so entity silhouette shape is correct
    fragColor = linear_fog(vec4(0.0, 0.0, 0.0, color.a), vertexDistance, FogStart, FogEnd, FogColor);
}
