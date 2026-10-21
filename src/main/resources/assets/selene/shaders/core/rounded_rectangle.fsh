#version 150

in vec2 localPosition;

uniform vec2 RectSize;
uniform float Radius;
uniform float BorderWidth;
uniform vec4 FillColor;
uniform vec4 BorderColor;
uniform vec4 ColorModulator;

out vec4 fragColor;

float roundedDistance(vec2 position, vec2 halfSize, float radius) {
    vec2 q = abs(position) - (halfSize - vec2(radius));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float coverage(float distance) {
    return clamp(0.5 - distance / max(fwidth(distance), 0.0001), 0.0, 1.0);
}

void main() {
    vec2 halfSize = RectSize * 0.5;
    vec2 position = localPosition - halfSize;
    float outerCoverage = coverage(roundedDistance(position, halfSize, Radius));
    float innerCoverage = outerCoverage;
    if (BorderWidth > 0.0) {
        vec2 innerSize = halfSize - vec2(BorderWidth);
        float innerDistance = roundedDistance(position, max(innerSize, 0.0), max(Radius - BorderWidth, 0.0));
        innerCoverage = min(outerCoverage, coverage(innerDistance));
        if (min(innerSize.x, innerSize.y) <= 0.0) {
            innerCoverage = 0.0;
        }
    }

    // Mix covered areas in premultiplied space, including transparent fills and borders.
    float fillAlpha = FillColor.a * innerCoverage;
    float borderAlpha = BorderColor.a * max(outerCoverage - innerCoverage, 0.0);
    float alpha = fillAlpha + borderAlpha;
    if (alpha <= 0.0) {
        discard;
    }
    vec3 color = (FillColor.rgb * fillAlpha + BorderColor.rgb * borderAlpha) / alpha;
    fragColor = vec4(color, alpha) * ColorModulator;
}
