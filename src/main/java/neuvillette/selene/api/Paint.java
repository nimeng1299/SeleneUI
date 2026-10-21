package neuvillette.selene.api;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;

import java.util.Objects;
import java.util.function.Consumer;

public class Paint {
    /**
     * 在客户端渲染线程中，使用当前 GUI 投影绘制实心矩形。
     * 坐标使用 GUI 缩放后的单位，不附加局部平移或缩放。
     */
    public static void drawRectangle(Rect rect, Color color) {
        drawRectangle(new Matrix4f(), rect, color);
    }

    /**
     * 使用指定的局部变换绘制实心矩形，支持 PoseStack 中的平移、缩放和 Z 层级。
     * 绘制立即提交，并在结束后恢复修改过的渲染状态。
     */
    public static void drawRectangle(Matrix4f pose, Rect rect, Color color) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(color, "color");
        RenderSystem.assertOnRenderThread();

        float left = Math.min(rect.x(), rect.x2());
        float top = Math.min(rect.y(), rect.y2());
        float right = Math.max(rect.x(), rect.x2());
        float bottom = Math.max(rect.y(), rect.y2());
        int red = color.Red();
        int green = color.Green();
        int blue = color.Blue();
        int alpha = Math.round(color.Alpha() * 255f / 100f);

        if (left == right || top == bottom || alpha == 0) {
            return;
        }

        draw(VertexFormat.Mode.QUADS, buffer -> {
            buffer.addVertex(pose, left, top, 0f).setColor(red, green, blue, alpha);
            buffer.addVertex(pose, left, bottom, 0f).setColor(red, green, blue, alpha);
            buffer.addVertex(pose, right, bottom, 0f).setColor(red, green, blue, alpha);
            buffer.addVertex(pose, right, top, 0f).setColor(red, green, blue, alpha);
        });
    }

    /**
     * 使用当前 GUI 投影绘制实心圆，坐标为圆心，radius 为半径。
     */
    public static void drawCircle(float centerX, float centerY, float radius, Color color) {
        drawCircle(new Matrix4f(), centerX, centerY, radius, color);
    }

    /** 使用指定的局部变换绘制实心圆。 */
    public static void drawCircle(Matrix4f pose, float centerX, float centerY, float radius, Color color) {
        drawSector(pose, centerX, centerY, radius, 0, 360, color);
    }

    /**
     * 顺时针绘制实心扇形：正上方为 0 度，右方为 90 度，下方为 180 度。
     * 支持所有整数角度，包括 45 度的倍数；315 到 45 表示跨过 0 度的 90 度扇形。
     * 相同起止角度不绘制；不同起止角度相差整圈时绘制整圆，例如 0 到 360。
     * 半径小于等于 0 时不绘制。
     */
    public static void drawSector(
            float centerX, float centerY, float radius, int startAngle, int endAngle, Color color
    ) {
        drawSector(new Matrix4f(), centerX, centerY, radius, startAngle, endAngle, color);
    }

    /** 使用指定的局部变换绘制实心扇形，角度规则与无矩阵重载相同。 */
    public static void drawSector(
            Matrix4f pose, float centerX, float centerY, float radius,
            int startAngle, int endAngle, Color color
    ) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(color, "color");
        RenderSystem.assertOnRenderThread();

        if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(radius)) {
            throw new IllegalArgumentException("Circle center and radius must be finite");
        }

        long angleDifference = (long) endAngle - startAngle;
        int sweep = (int) Math.floorMod(angleDifference, 360L);
        int sweepAngle = sweep == 0 && angleDifference != 0 ? 360 : sweep;
        int red = color.Red();
        int green = color.Green();
        int blue = color.Blue();
        int alpha = Math.round(color.Alpha() * 255f / 100f);

        if (radius <= 0 || sweepAngle == 0 || alpha == 0) {
            return;
        }

        int start = Math.floorMod(startAngle, 360);
        // 弧线约每 2 个 GUI 单位细分一次，小圆也保留足够的分段。
        int segments = Math.clamp(
                (int) Math.ceil(Math.toRadians(sweepAngle) * Math.max(radius, 16f) / 2.0),
                1, 4096
        );

        draw(VertexFormat.Mode.TRIANGLE_FAN, buffer -> {
            buffer.addVertex(pose, centerX, centerY, 0f).setColor(red, green, blue, alpha);
            // GUI 的 Y 轴向下，反向提交圆弧顶点，使三角形正面朝向屏幕。
            for (int i = 0; i <= segments; i++) {
                double degrees = (start + sweepAngle * (1.0 - (double) i / segments)) % 360.0;
                double radians = Math.toRadians(degrees);
                float x = centerX + (float) (Math.sin(radians) * radius);
                float y = centerY - (float) (Math.cos(radians) * radius);
                buffer.addVertex(pose, x, y, 0f).setColor(red, green, blue, alpha);
            }
        });
    }

    private static void draw(VertexFormat.Mode mode, Consumer<BufferBuilder> vertices) {
        GlStateBackup previousState = new GlStateBackup();
        RenderSystem.backupGlState(previousState);
        ShaderInstance previousShader = RenderSystem.getShader();
        float[] previousShaderColor = RenderSystem.getShaderColor().clone();

        try {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            BufferBuilder buffer = Tesselator.getInstance().begin(mode, DefaultVertexFormat.POSITION_COLOR);
            vertices.accept(buffer);
            // 直接提交网格，沿用原版 GUI 的着色器、透明混合和深度设置。
            RenderType.gui().draw(buffer.buildOrThrow());
        } finally {
            RenderSystem.setShaderColor(
                    previousShaderColor[0], previousShaderColor[1],
                    previousShaderColor[2], previousShaderColor[3]
            );
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.restoreGlState(previousState);
        }
    }
}
