package neuvillette.selene;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import neuvillette.selene.api.Color;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.layout.Direction;
import neuvillette.selene.screen.SeleneScreen;
import neuvillette.selene.widget.Block;
import neuvillette.selene.widget.Layout;

@EventBusSubscriber(modid = SeleneUI.MODID, value = Dist.CLIENT)
public class SeleneCommand {
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("selene")
                .executes(context -> {
                    Layout root = new Layout().Padding(16).Spacing(8).Horizontal();
                    root.addChild(panel("left", 40, 100, 180), Constraint.length(90));
                    root.addChild(panel("right", 40, 100, 180), Constraint.fill(1));

                    Minecraft.getInstance().setScreen(new SeleneScreen(Component.literal("Layout demo"), root, true));
                    return Command.SINGLE_SUCCESS;
                });

        // 将命令注册到客户端的命令调度器中
        event.getDispatcher().register(command);
    }

    private static Block panel(String title, int red, int green, int blue) {
        return new Block(0, 0, 0, 0, Component.literal(title))
                .Radius(5)
                .BorderWidth(1)
                .BorderColor(Color.fromRGB(180, 200, 220))
                .FillColor(Color.fromRGB(red, green, blue));
    }

}
