package dev.funman.infinite.command;

import dev.funman.infinite.InfiniteApi;
import dev.funman.infinite.stack.StackCoord;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfiniteCommands {
    private InfiniteCommands() {}

    public static void register(JavaPlugin plugin, InfiniteApi api) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    Commands.literal("infinite")
                            .executes(ctx -> {
                                if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                                    ctx.getSource().getSender().sendMessage("Players only.");
                                    return 0;
                                }
                                StackCoord at = api.locate(player.getLocation());
                                int half = api.borderHalf(at.dimension());
                                player.sendMessage(Component.text("Minecraft Infinite", NamedTextColor.AQUA));
                                player.sendMessage(Component.text(
                                        "seedIndex=" + at.seedIndex()
                                                + " worldSeed=" + api.seedFor(at.seedIndex())
                                                + " dim=" + at.dimension()
                                                + " half=" + half
                                                + " pos="
                                                + fmt(at.x()) + " " + fmt(at.y()) + " " + fmt(at.z()),
                                        NamedTextColor.GRAY
                                ));
                                player.sendMessage(Component.text(
                                        "floatCap=±" + api.config().maxAbs()
                                                + " precision=" + api.config().minPrecisionBlocks()
                                                + " netherScale=1:" + api.config().netherScale(),
                                        NamedTextColor.DARK_GRAY
                                ));
                                return 1;
                            })
                            .build(),
                    "Show current stack position (command pack comes later)"
            );
        });
    }

    private static String fmt(double v) {
        return Integer.toString((int) Math.floor(v));
    }
}
