package neuvillette.selene.gallery;

import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.screen.SeleneScreen;
import neuvillette.selene.widget.Block;
import neuvillette.selene.widget.Layout;

public class GalleryScreen extends SeleneScreen {
    public GalleryScreen() {
        this(new Layout().Padding(16).Spacing(8).Horizontal());
    }

    private GalleryScreen(Layout root) {
        super(Component.literal("gallery"), root, true);
        DrawNavigation(root);
        root.addChild(panel("right", 40, 100, 180), Constraint.fill(1));
    }

    private void DrawNavigation(Layout root){
        root.addChild(panel("left", 40, 100, 180), Constraint.length(90));
    }


    private static Block panel(String title, int red, int green, int blue) {
        return new Block(0, 0, 0, 0, Component.literal(title))
                .Radius(5)
                .BorderWidth(1)
                .BorderColor(Color.fromRGB(180, 200, 220))
                .FillColor(Color.fromRGB(red, green, blue));
    }
}
