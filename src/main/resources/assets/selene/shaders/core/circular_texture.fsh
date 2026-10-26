#version 150

in vec2 localPosition;

uniform sampler2D Sampler0;
uniform float Diameter;
uniform vec4 UvBounds;
uniform vec4 TintColor;
uniform vec4 ColorModulator;

out vec4 fragColor;

void main() {
    float radius = Diameter * 0.5;
    float distance = length(localPosition - vec2(radius)) - radius;
    float coverage = clamp(0.5 - distance / max(fwidth(distance), 0.0001), 0.0, 1.0);
    // The antialiasing fringe extends past the quad's nominal UV range; never wrap the texture.
    vec2 halfTexel = min(0.5 / vec2(textureSize(Sampler0, 0)), (UvBounds.zw - UvBounds.xy) * 0.5);
    vec2 uv = clamp(mix(UvBounds.xy, UvBounds.zw, localPosition / Diameter),
                    UvBounds.xy + halfTexel, UvBounds.zw - halfTexel);
    vec4 color = texture(Sampler0, uv) * TintColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
