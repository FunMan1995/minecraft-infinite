package dev.funman.infinite.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.funman.infinite.InfiniteApi;
import dev.funman.infinite.game.CombatTag;
import dev.funman.infinite.game.HomeStore;
import dev.funman.infinite.game.TpaService;
import dev.funman.infinite.stack.InfiniteDimension;
import dev.funman.infinite.stack.StackCoord;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfiniteCommands {
    private InfiniteCommands() {}

    public static void register(
            JavaPlugin plugin,
            InfiniteApi api,
            CombatTag combat,
            HomeStore homes,
            TpaService tpa
    ) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register(
                    Commands.literal("infinite")
                            .executes(ctx -> {
                                Player player = player(ctx.getSource().getExecutor());
                                if (player == null) {
                                    ctx.getSource().getSender().sendMessage("Players only.");
                                    return 0;
                                }
                                StackCoord at = api.locate(player.getLocation());
                                player.sendMessage(Component.text("Minecraft Infinite", NamedTextColor.AQUA));
                                player.sendMessage(Component.text(
                                        "seedIndex=" + at.seedIndex()
                                                + " worldSeed=" + api.seedFor(at.seedIndex())
                                                + " dim=" + at.dimension()
                                                + " half=" + api.borderHalf(at.dimension())
                                                + " pos=" + (int) at.x() + " " + (int) at.y() + " " + (int) at.z(),
                                        NamedTextColor.GRAY
                                ));
                                return 1;
                            })
                            .build(),
                    "Show current stack position"
            );

            registrar.register(
                    Commands.literal("sethome")
                            .executes(ctx -> sethome(player(ctx.getSource().getExecutor()), combat, homes, "home"))
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .executes(ctx -> sethome(
                                            player(ctx.getSource().getExecutor()),
                                            combat,
                                            homes,
                                            StringArgumentType.getString(ctx, "name")
                                    )))
                            .build(),
                    "Set a home (blocked in combat)"
            );

            registrar.register(
                    Commands.literal("home")
                            .executes(ctx -> home(player(ctx.getSource().getExecutor()), combat, homes, "home"))
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .executes(ctx -> home(
                                            player(ctx.getSource().getExecutor()),
                                            combat,
                                            homes,
                                            StringArgumentType.getString(ctx, "name")
                                    )))
                            .build(),
                    "Go home (blocked in combat)"
            );

            registrar.register(
                    Commands.literal("tpa")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(ctx -> {
                                        Player player = player(ctx.getSource().getExecutor());
                                        if (player == null) {
                                            return 0;
                                        }
                                        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                                        if (target == null || !target.isOnline()) {
                                            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                                            return 0;
                                        }
                                        tpa.request(player, target);
                                        return 1;
                                    }))
                            .build(),
                    "Request teleport (blocked in combat)"
            );

            registrar.register(
                    Commands.literal("tpaccept")
                            .executes(ctx -> tpaccept(player(ctx.getSource().getExecutor()), tpa))
                            .build(),
                    "Accept a TPA request"
            );
            registrar.register(
                    Commands.literal("tpdeny")
                            .executes(ctx -> {
                                Player player = player(ctx.getSource().getExecutor());
                                if (player == null) {
                                    return 0;
                                }
                                if (tpa.take(player) == null) {
                                    player.sendMessage(Component.text("No TPA request.", NamedTextColor.RED));
                                    return 0;
                                }
                                player.sendMessage(Component.text("TPA denied.", NamedTextColor.YELLOW));
                                return 1;
                            })
                            .build(),
                    "Deny a TPA request"
            );

            registrar.register(
                    Commands.literal("wild")
                            .executes(ctx -> wild(plugin, api, combat, player(ctx.getSource().getExecutor())))
                            .build(),
                    "Random seed from spawn",
                    List.of("rtp")
            );
        });
    }

    private static int sethome(Player player, CombatTag combat, HomeStore homes, String name) {
        if (player == null) {
            return 0;
        }
        if (combat.denyIfTagged(player, "set home")) {
            return 0;
        }
        if (!homes.has(player.getUniqueId(), name) && homes.count(player.getUniqueId()) >= homes.maxHomes()) {
            player.sendMessage(Component.text("Home limit reached (" + homes.maxHomes() + ").", NamedTextColor.RED));
            return 0;
        }
        homes.set(player, name, player.getLocation());
        player.sendMessage(Component.text("Home set: " + name, NamedTextColor.GREEN));
        return 1;
    }

    private static int home(Player player, CombatTag combat, HomeStore homes, String name) {
        if (player == null) {
            return 0;
        }
        if (combat.denyIfTagged(player, "home")) {
            return 0;
        }
        Location dest = homes.get(player.getUniqueId(), name);
        if (dest == null) {
            player.sendMessage(Component.text("No home named " + name + ".", NamedTextColor.RED));
            return 0;
        }
        player.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.sendMessage(Component.text("Welcome home.", NamedTextColor.GREEN));
        return 1;
    }

    private static int tpaccept(Player player, TpaService tpa) {
        if (player == null) {
            return 0;
        }
        if (tpa.combat().denyIfTagged(player, "accept TPA")) {
            return 0;
        }
        TpaService.Request req = tpa.take(player);
        if (req == null) {
            player.sendMessage(Component.text("No TPA request.", NamedTextColor.RED));
            return 0;
        }
        Player from = Bukkit.getPlayer(req.from());
        if (from == null || !from.isOnline()) {
            player.sendMessage(Component.text("That player is offline.", NamedTextColor.RED));
            return 0;
        }
        if (tpa.combat().denyIfTagged(from, "TPA")) {
            player.sendMessage(Component.text(from.getName() + " is combat tagged.", NamedTextColor.RED));
            return 0;
        }
        from.teleport(player.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        from.sendMessage(Component.text("TPA accepted.", NamedTextColor.GREEN));
        player.sendMessage(Component.text(from.getName() + " arrived.", NamedTextColor.GREEN));
        return 1;
    }

    private static int wild(JavaPlugin plugin, InfiniteApi api, CombatTag combat, Player player) {
        if (player == null) {
            return 0;
        }
        if (combat.denyIfTagged(player, "wild")) {
            return 0;
        }
        StackCoord at = api.locate(player.getLocation());
        if (at.seedIndex() != 0 || at.dimension() != InfiniteDimension.OVERWORLD) {
            player.sendMessage(Component.text("Use /wild at main spawn (seed 0).", NamedTextColor.RED));
            return 0;
        }
        int range = api.config().wildSeedRange();
        int seed;
        do {
            seed = ThreadLocalRandom.current().nextInt(-range, range + 1);
        } while (seed == 0);
        player.sendMessage(Component.text("Opening seed " + seed + "...", NamedTextColor.YELLOW));
        int chosen = seed;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            World world = api.worlds().worldFor(chosen, InfiniteDimension.OVERWORLD);
            player.teleport(world.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            player.sendMessage(Component.text("Wild: seed " + chosen, NamedTextColor.GREEN));
        });
        return 1;
    }

    private static Player player(Object executor) {
        return executor instanceof Player p ? p : null;
    }
}
