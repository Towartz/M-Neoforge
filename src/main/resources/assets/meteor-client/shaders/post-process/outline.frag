#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform int u_Width;
uniform float u_FillOpacity;
uniform int u_ShapeMode;
uniform float u_GlowMultiplier;
uniform int u_Rainbow;
uniform float u_RainbowSpeed;
uniform int u_Pulse;
uniform float u_PulseSpeed;
uniform float u_Time;

out vec4 color;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 center = texture(u_Texture, v_TexCoord);

    if (center.a != 0.0) {
        if (u_ShapeMode == 0) discard;
        center = vec4(center.rgb, center.a * u_FillOpacity);
    }
    else {
        if (u_ShapeMode == 1) discard;

        float dist = float(u_Width * u_Width * 4);
        int maxDistSq = u_Width * u_Width;

        for (int x = -u_Width; x <= u_Width; x++) {
            for (int y = -u_Width; y <= u_Width; y++) {
                int r2 = x * x + y * y;
                if (r2 > maxDistSq) continue;

                vec4 offset = texture(u_Texture, v_TexCoord + v_OneTexel * vec2(x, y));

                if (offset.a > 0.0) {
                    float ndist = float(r2);
                    if (ndist < dist) {
                        dist = ndist;
                        center = offset;
                    }
                }
            }
        }

        float minDist = float(maxDistSq);

        if (dist > minDist) {
            center.a = 0.0;
        } else {
            center.a = min((1.0 - (dist / minDist)) * u_GlowMultiplier, 1.0);
        }
    }

    if (u_Rainbow == 1 && center.a > 0.0) {
        float hue = fract(u_Time * u_RainbowSpeed + (v_TexCoord.x + v_TexCoord.y) * 0.5);
        center.rgb = hsv2rgb(vec3(hue, 1.0, 1.0));
    }

    if (u_Pulse == 1 && center.a > 0.0) {
        float pulse = 0.65 + 0.35 * sin(u_Time * u_PulseSpeed);
        center.a *= pulse;
    }

    color = center;
}
