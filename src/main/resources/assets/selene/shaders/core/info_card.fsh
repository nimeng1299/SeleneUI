#version 150

in vec2 localPosition;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 RectSize;
uniform float Radius;
uniform float FooterHeight;
uniform float FooterRadius;
uniform vec4 TopColor;
uniform vec4 BottomColor;
uniform vec4 FooterColor;
uniform float Grain;
uniform float HasImage;
uniform float HasBackground;
uniform float ImageScale;
uniform float Opacity;
uniform vec4 ColorModulator;

out vec4 fragColor;

vec4 premultiply(vec4 color) {
    return vec4(color.rgb * color.a, color.a);
}

vec4 over(vec4 foreground, vec4 background) {
    return premultiply(foreground) + background * (1.0 - foreground.a);
}

vec4 sampleClamped(sampler2D image, vec2 uv) {
    vec2 halfTexel = 0.5 / vec2(textureSize(image, 0));
    return texture(image, clamp(uv, halfTexel, 1.0 - halfTexel));
}

void main() {
    vec2 halfSize = RectSize * 0.5;
    vec2 q = abs(localPosition - halfSize) - (halfSize - vec2(Radius));
    float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - Radius;
    float coverage = clamp(0.5 - distance / max(fwidth(distance), 0.0001), 0.0, 1.0);
    float imageHeight = RectSize.y - FooterHeight;
    // Extend edge colors into the antialiasing fringe without inventing a footer when its height is zero.
    vec2 samplePosition = clamp(localPosition, vec2(0.0), RectSize);
    float footerCoverage = 0.0;
    if (FooterHeight > 0.0) {
        if (imageHeight <= 0.0) {
            footerCoverage = 1.0;
        } else {
            // Round the image's bottom-right corner inward so the footer rises along the right edge.
            // The quarter circle joins the horizontal footer edge and the vertical card edge tangentially.
            float cornerRadius = clamp(FooterRadius, 0.0, min(RectSize.x, imageHeight));
            vec2 center = vec2(RectSize.x - cornerRadius, imageHeight - cornerRadius);
            vec2 corner = vec2(max(samplePosition.x - center.x, 0.0), samplePosition.y - center.y);
            float arcDistance = length(corner) - cornerRadius;
            float footerDistance = max(samplePosition.y - imageHeight,
                                       min(samplePosition.y - center.y, arcDistance));
            footerCoverage = clamp(0.5 + footerDistance / max(fwidth(footerDistance), 0.0001), 0.0, 1.0);
        }
    }
    vec4 color = premultiply(FooterColor);
    if (footerCoverage < 1.0) {
        vec2 viewport = vec2(RectSize.x, imageHeight);
        // A gentle diagonal gradient with stationary, fine grain.
        vec2 position = clamp(samplePosition / viewport, 0.0, 1.0);
        float blend = dot(position, vec2(0.2, 0.8));
        color = mix(premultiply(TopColor), premultiply(BottomColor), blend);
        float noise = fract(sin(dot(floor(samplePosition * 2.0), vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
        color.rgb = clamp(color.rgb + noise * Grain * color.a, vec3(0.0), vec3(color.a));
        if (HasBackground > 0.5) {
            color = over(sampleClamped(Sampler1, samplePosition / viewport), color);
        }
        if (HasImage > 0.5) {
            vec2 pixels = vec2(textureSize(Sampler0, 0));
            float scale = min(viewport.x / pixels.x, viewport.y / pixels.y) * ImageScale;
            vec2 size = max(pixels * scale, vec2(0.0001));
            vec2 uv = (samplePosition - (viewport - size) * 0.5) / size;
            if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {
                color = over(sampleClamped(Sampler0, uv), color);
            }
        }
        // Partition the two regions in premultiplied space, including translucent footer colors.
        color = mix(color, premultiply(FooterColor), footerCoverage);
    }
    if (color.a <= 0.0 || coverage <= 0.0) {
        discard;
    }
    // Apply the mask and widget opacity exactly once to the composed layers.
    fragColor = vec4(color.rgb / color.a, color.a * coverage * Opacity) * ColorModulator;
}
