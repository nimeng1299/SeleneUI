package neuvillette.selene.api;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
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
import java.util.function.Function;

@EventBusSubscriber(modid = SeleneUI.MODID, value = Dist.CLIENT)
public final class PaintShaders {
    private static ShaderInstance roundedRectangle;
    private static ShaderInstance circularTexture;
    private static ShaderInstance infoCard;
    private static final RenderType ROUNDED_RECTANGLE = RenderType.create(
            "selene_rounded_rectangle", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(PaintShaders::roundedRectangle))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );
    private static final Function<ResourceLocation, RenderType> CIRCULAR_TEXTURE = Util.memoize(texture ->
            RenderType.create("selene_circular_texture", DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS, 256,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(PaintShaders::circularTexture))
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setCullState(RenderStateShard.NO_CULL)
                            .createCompositeState(false)));

    private record CardTextures(ResourceLocation image, ResourceLocation background) {}

    private static final Function<CardTextures, RenderType> INFO_CARD = Util.memoize(textures ->
            RenderType.create("selene_info_card", DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS, 256,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(PaintShaders::infoCard))
                            .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                                    .add(textures.image(), false, false)
                                    .add(textures.background(), false, false).build())
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setCullState(RenderStateShard.NO_CULL)
                            .createCompositeState(false)));

    private PaintShaders() {}

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "rounded_rectangle"),
                DefaultVertexFormat.POSITION_TEX
        ), shader -> roundedRectangle = shader);
        event.registerShader(new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "circular_texture"),
                DefaultVertexFormat.POSITION_TEX
        ), shader -> circularTexture = shader);
        event.registerShader(new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "info_card"),
                DefaultVertexFormat.POSITION_TEX
        ), shader -> infoCard = shader);
    }

    static ShaderInstance roundedRectangle() {
        return roundedRectangle;
    }

    static RenderType roundedRectangleType() {
        return ROUNDED_RECTANGLE;
    }

    static ShaderInstance circularTexture() {
        return circularTexture;
    }

    static RenderType circularTextureType(ResourceLocation texture) {
        return CIRCULAR_TEXTURE.apply(texture);
    }

    static ShaderInstance infoCard() {
        return infoCard;
    }

    static RenderType infoCardType(ResourceLocation image, ResourceLocation background) {
        return INFO_CARD.apply(new CardTextures(image, background));
    }
}
