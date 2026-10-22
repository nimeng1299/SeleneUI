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
import neuvillette.selene.screen.SeleneScreen;
import neuvillette.selene.widget.Block;

@EventBusSubscriber(modid = SeleneUI.MODID, value = Dist.CLIENT)
public class SeleneCommand {
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("selene")
                .executes(context -> {
                    // 打开 Screen 的核心代码
                    Block parent = new Block(50, 50, 150, 150, Component.literal("Parent")).Padding(10);
                    parent.addChild(new Block(10, 20, 60, 30, Component.literal("Child"))
                            .Radius(5)
                            .FillColor(Color.fromRGB(40, 160, 255)));
                    Minecraft.getInstance().setScreen(new SeleneScreen(Component.literal("screen test"), parent));
                    return Command.SINGLE_SUCCESS;
                });

        // 将命令注册到客户端的命令调度器中
        event.getDispatcher().register(command);
    }

}
