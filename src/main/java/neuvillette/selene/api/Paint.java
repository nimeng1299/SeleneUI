package neuvillette.selene.api;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;

import java.util.Objects;
import java.util.TreeSet;
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

    /**
     * 绘制带边框的实心矩形。边框向内绘制，宽度不超过短边的一半。
     * 填充和边框互不重叠，支持透明填充、半透明边框和小数宽度。
     */
    public static void drawBorderedRectangle(Rect rect, Color fillColor, Color borderColor, float borderWidth) {
        drawBorderedRectangle(new Matrix4f(), rect, fillColor, borderColor, borderWidth);
    }

    /** 使用指定的局部变换绘制带边框的矩形。 */
    public static void drawBorderedRectangle(
            Matrix4f pose, Rect rect, Color fillColor, Color borderColor, float borderWidth
    ) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(fillColor, "fillColor");
        Objects.requireNonNull(borderColor, "borderColor");
        RenderSystem.assertOnRenderThread();
        if (!Float.isFinite(borderWidth)) {
            throw new IllegalArgumentException("Border width must be finite");
        }

        float left = Math.min(rect.x(), rect.x2());
        float top = Math.min(rect.y(), rect.y2());
        float right = Math.max(rect.x(), rect.x2());
        float bottom = Math.max(rect.y(), rect.y2());
        if (left == right || top == bottom) {
            return;
        }

        float width = Math.clamp(borderWidth, 0f, Math.min(right - left, bottom - top) / 2f);
        int fill = argb(fillColor);
        int border = argb(borderColor);
        draw(VertexFormat.Mode.QUADS, buffer -> {
            addRectangle(buffer, pose, left + width, top + width, right - width, bottom - width, fill);
            addRectangle(buffer, pose, left, top, right, top + width, border);
            addRectangle(buffer, pose, left, bottom - width, right, bottom, border);
            addRectangle(buffer, pose, left, top + width, left + width, bottom - width, border);
            addRectangle(buffer, pose, right - width, top + width, right, bottom - width, border);
        });
    }

    /** 绘制带边框的实心圆，radius 为包含边框的外半径，边框向内绘制。 */
    public static void drawBorderedCircle(
            float centerX, float centerY, float radius, Color fillColor, Color borderColor, float borderWidth
    ) {
        drawBorderedCircle(new Matrix4f(), centerX, centerY, radius, fillColor, borderColor, borderWidth);
    }

    /** 使用指定的局部变换绘制带边框的圆。 */
    public static void drawBorderedCircle(
            Matrix4f pose, float centerX, float centerY, float radius,
            Color fillColor, Color borderColor, float borderWidth
    ) {
        drawBorderedSector(pose, centerX, centerY, radius, 0, 360, fillColor, borderColor, borderWidth, false);
    }

    /**
     * 绘制带边框的扇形，角度规则与 drawSector 相同，radius 为外半径。
     * drawRadii 为 true 时绘制圆弧边和两条半径边，为 false 时只绘制圆弧边。
     * 边框向内绘制；整圆没有半径边；非正边框宽度表示仅绘制填充。
     * 填充和各段边框互不重叠，透明填充也不会露出底层边框。
     */
    public static void drawBorderedSector(
            float centerX, float centerY, float radius, int startAngle, int endAngle,
            Color fillColor, Color borderColor, float borderWidth, boolean drawRadii
    ) {
        drawBorderedSector(
                new Matrix4f(), centerX, centerY, radius, startAngle, endAngle,
                fillColor, borderColor, borderWidth, drawRadii
        );
    }

    /** 使用指定的局部变换绘制带边框的扇形。 */
    public static void drawBorderedSector(
            Matrix4f pose, float centerX, float centerY, float radius, int startAngle, int endAngle,
            Color fillColor, Color borderColor, float borderWidth, boolean drawRadii
    ) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(fillColor, "fillColor");
        Objects.requireNonNull(borderColor, "borderColor");
        RenderSystem.assertOnRenderThread();
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(radius) || !Float.isFinite(borderWidth)) {
            throw new IllegalArgumentException("Circle center, radius and border width must be finite");
        }

        long difference = (long) endAngle - startAngle;
        int sweep = (int) Math.floorMod(difference, 360L);
        int sweepAngle = sweep == 0 && difference != 0 ? 360 : sweep;
        if (radius <= 0 || sweepAngle == 0) {
            return;
        }

        float width = Math.clamp(borderWidth, 0f, radius);
        int start = Math.floorMod(startAngle, 360);
        int fill = argb(fillColor);
        int border = argb(borderColor);
        draw(VertexFormat.Mode.QUADS, buffer -> addBorderedSector(
                buffer, pose, centerX, centerY, radius, start, sweepAngle,
                fill, border, width, drawRadii && sweepAngle < 360 && width > 0
        ));
    }

    /** 绘制带屏幕像素抗锯齿的实心圆角矩形，圆角半径限制在 0 到短边的一半之间。 */
    public static void drawRoundedRectangle(Rect rect, float radius, Color color) {
        drawRoundedRectangle(new Matrix4f(), rect, radius, color);
    }

    /** 使用指定的局部变换绘制实心圆角矩形。 */
    public static void drawRoundedRectangle(Matrix4f pose, Rect rect, float radius, Color color) {
        drawBorderedRoundedRectangle(pose, rect, radius, color, color, 0f);
    }

    /**
     * 绘制带边框的圆角矩形，radius 为外侧圆角半径，边框向内绘制。
     * 内侧圆角半径为 max(radius - borderWidth, 0)，边框宽度不超过短边的一半。
     * 填充和边框互不重叠，支持透明填充、半透明边框和小数宽度。
     * 外轮廓和边框内缘均按屏幕像素进行抗锯齿，适配 GUI 缩放和局部变换。
     */
    public static void drawBorderedRoundedRectangle(
            Rect rect, float radius, Color fillColor, Color borderColor, float borderWidth
    ) {
        drawBorderedRoundedRectangle(new Matrix4f(), rect, radius, fillColor, borderColor, borderWidth);
    }

    /** 使用指定的局部变换绘制带边框的圆角矩形。 */
    public static void drawBorderedRoundedRectangle(
            Matrix4f pose, Rect rect, float radius, Color fillColor, Color borderColor, float borderWidth
    ) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(fillColor, "fillColor");
        Objects.requireNonNull(borderColor, "borderColor");
        RenderSystem.assertOnRenderThread();
        if (!Float.isFinite(radius) || !Float.isFinite(borderWidth)) {
            throw new IllegalArgumentException("Corner radius and border width must be finite");
        }

        float left = Math.min(rect.x(), rect.x2());
        float top = Math.min(rect.y(), rect.y2());
        float right = Math.max(rect.x(), rect.x2());
        float bottom = Math.max(rect.y(), rect.y2());
        if (left == right || top == bottom) {
            return;
        }

        float limit = Math.min(right - left, bottom - top) / 2f;
        float cornerRadius = Math.clamp(radius, 0f, limit);
        float width = Math.clamp(borderWidth, 0f, limit);
        if (cornerRadius == 0f) {
            drawBorderedRectangle(pose, rect, fillColor, borderColor, width);
            return;
        }

        int fill = argb(fillColor);
        int border = argb(borderColor);
        ShaderInstance shader = PaintShaders.roundedRectangle();
        if (shader != null) {
            drawSmoothRoundedRectangle(pose, left, top, right, bottom, cornerRadius, width, fill, border, shader);
            return;
        }
        // 资源尚未加载时沿用网格绘制，正常加载后的界面使用距离场着色器。
        draw(VertexFormat.Mode.QUADS, buffer -> addRoundedRectangle(
                buffer, pose, left, top, right, bottom, cornerRadius, width, fill, border
        ));
    }

    private static void drawSmoothRoundedRectangle(
            Matrix4f pose, float left, float top, float right, float bottom,
            float radius, float borderWidth, int fill, int border, ShaderInstance shader
    ) {
        if ((fill >>> 24) == 0 && (border >>> 24) == 0) {
            return;
        }
        var window = Minecraft.getInstance().getWindow();
        Matrix4f localToClip = new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix()).mul(pose);
        float[] padding = roundedRectanglePadding(
                localToClip, left + (right - left) / 2f, top + (bottom - top) / 2f,
                window.getWidth(), window.getHeight()
        );
        if (padding == null) {
            return;
        }

        float width = right - left;
        float height = bottom - top;
        shader.safeGetUniform("RectSize").set(width, height);
        shader.safeGetUniform("Radius").set(radius);
        shader.safeGetUniform("BorderWidth").set(borderWidth);
        setColorUniform(shader, "FillColor", fill);
        setColorUniform(shader, "BorderColor", border);
        float padX = padding[0];
        float padY = padding[1];
        draw(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX, PaintShaders.roundedRectangleType(), buffer -> {
            // 给轮廓外侧留出一个屏幕像素，避免抗锯齿过渡被包围矩形裁掉。
            buffer.addVertex(pose, left - padX, top - padY, 0f).setUv(-padX, -padY);
            buffer.addVertex(pose, left - padX, bottom + padY, 0f).setUv(-padX, height + padY);
            buffer.addVertex(pose, right + padX, bottom + padY, 0f).setUv(width + padX, height + padY);
            buffer.addVertex(pose, right + padX, top - padY, 0f).setUv(width + padX, -padY);
        });
    }

    private static void setColorUniform(ShaderInstance shader, String name, int color) {
        shader.safeGetUniform(name).set(
                ((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f,
                (color & 255) / 255f, (color >>> 24) / 255f
        );
    }

    private static float[] roundedRectanglePadding(
            Matrix4f localToClip, float centerX, float centerY, int framebufferWidth, int framebufferHeight
    ) {
        double x = localToClip.m00() * (double) centerX + localToClip.m10() * (double) centerY + localToClip.m30();
        double y = localToClip.m01() * (double) centerX + localToClip.m11() * (double) centerY + localToClip.m31();
        double w = localToClip.m03() * (double) centerX + localToClip.m13() * (double) centerY + localToClip.m33();
        double a = (localToClip.m00() * w - x * localToClip.m03()) / (w * w) * framebufferWidth / 2;
        double b = (localToClip.m10() * w - x * localToClip.m13()) / (w * w) * framebufferWidth / 2;
        double c = (localToClip.m01() * w - y * localToClip.m03()) / (w * w) * framebufferHeight / 2;
        double d = (localToClip.m11() * w - y * localToClip.m13()) / (w * w) * framebufferHeight / 2;
        double determinant = Math.abs(a * d - b * c);
        if (!Double.isFinite(determinant) || determinant < 1e-12) {
            return null;
        }
        // 屏幕像素在局部坐标中的足迹，包含非等比缩放和旋转。
        return new float[] {(float) ((Math.abs(d) + Math.abs(b)) / determinant),
                (float) ((Math.abs(c) + Math.abs(a)) / determinant)};
    }

    private static int argb(Color color) {
        int alpha = Math.round(Math.clamp(color.Alpha(), 0, 100) * 255f / 100f);
        return (alpha << 24) | (Math.clamp(color.Red(), 0, 255) << 16)
                | (Math.clamp(color.Green(), 0, 255) << 8) | Math.clamp(color.Blue(), 0, 255);
    }

    private static void addRectangle(
            VertexConsumer buffer, Matrix4f pose, float left, float top, float right, float bottom, int color
    ) {
        if ((color >>> 24) == 0 || left >= right || top >= bottom) {
            return;
        }
        buffer.addVertex(pose, left, top, 0f).setColor(color);
        buffer.addVertex(pose, left, bottom, 0f).setColor(color);
        buffer.addVertex(pose, right, bottom, 0f).setColor(color);
        buffer.addVertex(pose, right, top, 0f).setColor(color);
    }

    private static void addBorderedSector(
            VertexConsumer buffer, Matrix4f pose, float centerX, float centerY, float radius,
            int start, int sweep, int fill, int border, float width, boolean drawRadii
    ) {
        float innerRadius = radius - width;
        int segments = Math.clamp(
                (int) Math.ceil(Math.toRadians(sweep) * Math.max(radius, 16f) / 2.0), 1, 4096
        );
        TreeSet<Double> offsets = new TreeSet<>();
        for (int i = 0; i <= segments; i++) {
            offsets.add((double) sweep * i / segments);
        }
        if (drawRadii) {
            // 加入直边和内圆的交点，避免细边框在圆弧接头处变窄。
            if (innerRadius > width) {
                double margin = Math.toDegrees(Math.asin(width / innerRadius));
                if (margin < sweep) {
                    offsets.add(margin);
                    offsets.add(sweep - margin);
                }
            }
            offsets.add(sweep / 2.0);
            if (sweep > 180) {
                offsets.add(90.0);
                offsets.add(sweep - 90.0);
            }
        }

        double previous = 0;
        float previousFillStart = drawRadii ? radialBorderRadius(0, sweep, width, innerRadius) : 0;
        for (double offset : offsets.tailSet(0.0, false)) {
            float fillStart = drawRadii ? radialBorderRadius(offset, sweep, width, innerRadius) : 0;
            double angle1 = Math.toRadians((start + previous) % 360.0);
            double angle2 = Math.toRadians((start + offset) % 360.0);
            // 将每一片拆成圆弧边框、半径边框、填充三个互不重叠的区域。
            addArcBand(buffer, pose, centerX, centerY, angle1, angle2,
                    innerRadius, innerRadius, radius, radius, border);
            addArcBand(buffer, pose, centerX, centerY, angle1, angle2,
                    0, 0, previousFillStart, fillStart, border);
            addArcBand(buffer, pose, centerX, centerY, angle1, angle2,
                    previousFillStart, fillStart, innerRadius, innerRadius, fill);
            previous = offset;
            previousFillStart = fillStart;
        }
    }

    private static float radialBorderRadius(double offset, int sweep, float width, float innerRadius) {
        double factor = Math.min(rayDistanceFactor(Math.toRadians(offset)),
                rayDistanceFactor(Math.toRadians(sweep - offset)));
        return factor <= 0 ? innerRadius : (float) Math.min(innerRadius, width / factor);
    }

    private static double rayDistanceFactor(double angle) {
        // 投影落在半径线的反方向时，最近的边界点是圆心。
        return Math.cos(angle) > 0 ? Math.abs(Math.sin(angle)) : 1.0;
    }

    private static void addArcBand(
            VertexConsumer buffer, Matrix4f pose, float centerX, float centerY, double angle1, double angle2,
            float inner1, float inner2, float outer1, float outer2, int color
    ) {
        if ((color >>> 24) == 0 || (inner1 == outer1 && inner2 == outer2)) {
            return;
        }
        float dx1 = (float) Math.sin(angle1);
        float dy1 = (float) -Math.cos(angle1);
        float dx2 = (float) Math.sin(angle2);
        float dy2 = (float) -Math.cos(angle2);
        buffer.addVertex(pose, centerX + dx1 * outer1, centerY + dy1 * outer1, 0f).setColor(color);
        buffer.addVertex(pose, centerX + dx1 * inner1, centerY + dy1 * inner1, 0f).setColor(color);
        buffer.addVertex(pose, centerX + dx2 * inner2, centerY + dy2 * inner2, 0f).setColor(color);
        buffer.addVertex(pose, centerX + dx2 * outer2, centerY + dy2 * outer2, 0f).setColor(color);
    }

    private record OutlinePoint(float outerX, float outerY, float innerX, float innerY) {}

    private static void addRoundedRectangle(
            VertexConsumer buffer, Matrix4f pose, float left, float top, float right, float bottom,
            float radius, float width, int fill, int border
    ) {
        float innerRadius = Math.max(radius - width, 0f);
        float centerX = left + (right - left) / 2f;
        float centerY = top + (bottom - top) / 2f;
        int fillColor = left + width < right - width && top + width < bottom - width ? fill : 0;
        int borderColor = width > 0 ? border : 0;
        int segments = Math.clamp(
                (int) Math.ceil(Math.PI / 2 * Math.max(radius, 16f) / 2.0), 1, 1024
        );
        OutlinePoint first = null;
        OutlinePoint previous = null;
        for (int corner = 0; corner < 4; corner++) {
            boolean rightCorner = corner < 2;
            boolean bottomCorner = corner == 1 || corner == 2;
            float outerCenterX = rightCorner ? right - radius : left + radius;
            float outerCenterY = bottomCorner ? bottom - radius : top + radius;
            float innerCenterX = rightCorner ? right - width - innerRadius : left + width + innerRadius;
            float innerCenterY = bottomCorner ? bottom - width - innerRadius : top + width + innerRadius;
            for (int i = 0; i <= segments; i++) {
                double angle = Math.toRadians((corner * 90 + 90.0 * i / segments) % 360.0);
                float dx = (float) Math.sin(angle);
                float dy = (float) -Math.cos(angle);
                OutlinePoint point = new OutlinePoint(
                        outerCenterX + dx * radius, outerCenterY + dy * radius,
                        innerCenterX + dx * innerRadius, innerCenterY + dy * innerRadius
                );
                if (previous == null) {
                    first = point;
                } else {
                    addRoundedEdge(buffer, pose, previous, point, centerX, centerY, fillColor, borderColor);
                }
                previous = point;
            }
        }
        addRoundedEdge(buffer, pose, previous, first, centerX, centerY, fillColor, borderColor);
    }

    private static void addRoundedEdge(
            VertexConsumer buffer, Matrix4f pose, OutlinePoint previous, OutlinePoint next,
            float centerX, float centerY, int fill, int border
    ) {
        if ((fill >>> 24) != 0) {
            // 一个退化为三角形的四边形，填满内轮廓的一片。
            buffer.addVertex(pose, centerX, centerY, 0f).setColor(fill);
            buffer.addVertex(pose, next.innerX, next.innerY, 0f).setColor(fill);
            buffer.addVertex(pose, previous.innerX, previous.innerY, 0f).setColor(fill);
            buffer.addVertex(pose, centerX, centerY, 0f).setColor(fill);
        }
        if ((border >>> 24) != 0) {
            // 内外轮廓之间的网格与填充共用边界，避免半透明颜色叠加。
            buffer.addVertex(pose, previous.outerX, previous.outerY, 0f).setColor(border);
            buffer.addVertex(pose, previous.innerX, previous.innerY, 0f).setColor(border);
            buffer.addVertex(pose, next.innerX, next.innerY, 0f).setColor(border);
            buffer.addVertex(pose, next.outerX, next.outerY, 0f).setColor(border);
        }
    }

    private static void draw(VertexFormat.Mode mode, Consumer<BufferBuilder> vertices) {
        draw(mode, DefaultVertexFormat.POSITION_COLOR, RenderType.gui(), vertices);
    }

    private static void draw(
            VertexFormat.Mode mode, VertexFormat format, RenderType renderType, Consumer<BufferBuilder> vertices
    ) {
        GlStateBackup previousState = new GlStateBackup();
        RenderSystem.backupGlState(previousState);
        ShaderInstance previousShader = RenderSystem.getShader();
        float[] previousShaderColor = RenderSystem.getShaderColor().clone();

        try {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            BufferBuilder buffer = Tesselator.getInstance().begin(mode, format);
            vertices.accept(buffer);
            // 直接提交网格，沿用原版 GUI 的着色器、透明混合和深度设置。
            MeshData mesh = buffer.build();
            if (mesh != null) {
                renderType.draw(mesh);
            }
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
