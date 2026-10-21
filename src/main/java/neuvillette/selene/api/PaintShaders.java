package neuvillette.selene.api;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import neuvillette.selene.SeleneUI;

import java.io.IOException;

@EventBusSubscriber(modid = SeleneUI.MODID, value = Dist.CLIENT)
public final class PaintShaders {
    private static ShaderInstance roundedRectangle;
    private static final RenderType ROUNDED_RECTANGLE = RenderType.create(
            "selene_rounded_rectangle", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(PaintShaders::roundedRectangle))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    private PaintShaders() {}

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "rounded_rectangle"),
                DefaultVertexFormat.POSITION_TEX
        ), shader -> roundedRectangle = shader);
    }

    static ShaderInstance roundedRectangle() {
        return roundedRectangle;
    }

    static RenderType roundedRectangleType() {
        return ROUNDED_RECTANGLE;
    }
}
