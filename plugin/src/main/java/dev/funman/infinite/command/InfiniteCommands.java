package dev.funman.infinite.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.funman.infinite.InfiniteApi;
import dev.funman.infinite.game.CombatTag;
import dev.funman.infinite.game.HomeStore;
import dev.funman.infinite.game.Reincarnation;
import dev.funman.infinite.game.TpaService;
import dev.funman.infinite.substrate.Substrate;
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
            TpaService tpa,
            Substrate substrate,
            Reincarnation reincarnation
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

            registrar.register(
                    Commands.literal("reincarnate")
                            .executes(ctx -> {
                                Player p = player(ctx.getSource().getExecutor());
                                if (p == null) {
                                    return 0;
                                }
                                reincarnation.reincarnate(p);
                                return 1;
                            })
                            .build(),
                    "Use Reincarnate when the wait is over"
            );

            registrar.register(
                    Commands.literal("address")
                            .then(Commands.literal("grab")
                                    .then(Commands.argument("name", StringArgumentType.word())
                                            .executes(ctx -> grab(
                                                    player(ctx.getSource().getExecutor()),
                                                    api,
                                                    substrate,
                                                    StringArgumentType.getString(ctx, "name")
                                            ))))
                            .build(),
                    "Claim a unique subserver address"
            );

            registrar.register(
                    Commands.literal("sub")
                            .then(Commands.literal("import")
                                    .then(Commands.argument("address", StringArgumentType.word())
                                            .then(Commands.argument("kind", StringArgumentType.word())
                                                    .executes(ctx -> subImport(
                                                            player(ctx.getSource().getExecutor()),
                                                            api,
                                                            substrate,
                                                            StringArgumentType.getString(ctx, "address"),
                                                            StringArgumentType.getString(ctx, "kind")
                                                    )))))
                            .then(Commands.literal("upload")
                                    .then(Commands.argument("address", StringArgumentType.word())
                                            .executes(ctx -> subUpload(
                                                    player(ctx.getSource().getExecutor()),
                                                    substrate,
                                                    StringArgumentType.getString(ctx, "address")
                                            ))))
                            .then(Commands.literal("timeout")
                                    .then(Commands.argument("seconds", IntegerArgumentType.integer(60))
                                            .executes(ctx -> subTimeout(
                                                    player(ctx.getSource().getExecutor()),
                                                    substrate,
                                                    IntegerArgumentType.getInteger(ctx, "seconds")
                                            ))))
                            .build(),
                    "Subserver handshake with master"
            );

            registrar.register(
                    Commands.literal("kit")
                            .then(Commands.literal("claim")
                                    .executes(ctx -> kitClaim(player(ctx.getSource().getExecutor()), api, substrate)))
                            .build(),
                    "Claim this seed as a client-only kit on your UUID"
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

    private static int kitClaim(Player player, InfiniteApi api, Substrate substrate) {
        if (player == null) {
            return 0;
        }
        int seed = api.locate(player.getLocation()).seedIndex();
        try {
            Substrate.Kit kit = substrate.claimKit(player.getUniqueId(), seed);
            player.sendMessage(Component.text(
                    "Kit claimed for your client id on seed " + kit.spawnSeed()
                            + ". Drop client-only jars in kits/" + player.getUniqueId() + "/client-mods/",
                    NamedTextColor.GREEN
            ));
        } catch (IllegalStateException ex) {
            player.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return 0;
        }
        return 1;
    }

    private static int grab(Player player, InfiniteApi api, Substrate substrate, String name) {
        if (player == null) {
            return 0;
        }
        int seed = api.locate(player.getLocation()).seedIndex();
        try {
            String key = substrate.request(player.getUniqueId(), name, Substrate.Kind.VANILLA, List.of(), seed);
            substrate.setLoginHome(player.getUniqueId(), name.toLowerCase());
            player.sendMessage(Component.text(
                    "Address " + name.toLowerCase() + " claimed on seed " + seed + ". Import key (save it): " + key,
                    NamedTextColor.GREEN
            ));
        } catch (IllegalStateException ex) {
            player.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return 0;
        }
        return 1;
    }

    private static int subImport(Player player, InfiniteApi api, Substrate substrate, String address, String kindRaw) {
        if (player == null) {
            return 0;
        }
        Substrate.Kind kind;
        try {
            kind = Substrate.Kind.valueOf(kindRaw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Component.text("Kind must be vanilla or modded.", NamedTextColor.RED));
            return 0;
        }
        int seed = api.locate(player.getLocation()).seedIndex();
        List<String> mods = kind == Substrate.Kind.MODDED ? snapshotMods(api) : List.of();
        try {
            String key = substrate.request(player.getUniqueId(), address, kind, mods, seed);
            substrate.setLoginHome(player.getUniqueId(), address.toLowerCase());
            player.sendMessage(Component.text(
                    "Master granted " + address + " (" + kind + ") seed " + seed + ". Key: " + key,
                    NamedTextColor.GREEN
            ));
            player.sendMessage(Component.text(
                    "Worlds generate under substrate/subs/" + address.toLowerCase() + "/worlds/Home",
                    NamedTextColor.GRAY
            ));
        } catch (IllegalStateException ex) {
            player.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return 0;
        }
        return 1;
    }

    private static int subUpload(Player player, Substrate substrate, String address) {
        if (player == null) {
            return 0;
        }
        Substrate.Sub sub = substrate.sub(address);
        if (sub == null || !sub.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You do not operate that address.", NamedTextColor.RED));
            return 0;
        }
        try {
            substrate.upload(address, sub.mods());
            player.sendMessage(Component.text("Uploaded pack info for " + address + " back to master.", NamedTextColor.GREEN));
        } catch (IllegalStateException ex) {
            player.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return 0;
        }
        return 1;
    }

    private static int subTimeout(Player player, Substrate substrate, int seconds) {
        if (player == null) {
            return 0;
        }
        String home = substrate.loginHome(player.getUniqueId());
        if (Substrate.MASTER.equals(home)) {
            player.sendMessage(Component.text("Claim a sub address first.", NamedTextColor.RED));
            return 0;
        }
        try {
            substrate.setSubTimeout(home, player.getUniqueId(), seconds);
            player.sendMessage(Component.text(
                    "Local reincarnate wait is " + seconds + "s for deaths on your seeds. Master deaths stay "
                            + substrate.masterTimeoutSeconds() + "s.",
                    NamedTextColor.GREEN
            ));
        } catch (IllegalStateException ex) {
            player.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return 0;
        }
        return 1;
    }

    private static List<String> snapshotMods(InfiniteApi api) {
        java.io.File mods = new java.io.File(api.worlds().worldFor(0, InfiniteDimension.OVERWORLD)
                .getWorldFolder().getParentFile(), "mods");
        if (!mods.isDirectory()) {
            return List.of();
        }
        String[] names = mods.list((dir, name) -> name.endsWith(".jar"));
        return names == null ? List.of() : List.of(names);
    }

    private static Player player(Object executor) {
        return executor instanceof Player p ? p : null;
    }
}
