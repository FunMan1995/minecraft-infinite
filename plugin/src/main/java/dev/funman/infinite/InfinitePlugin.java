package dev.funman.infinite;

import dev.funman.infinite.command.InfiniteCommands;
import dev.funman.infinite.config.InfiniteConfig;
import dev.funman.infinite.game.CombatTag;
import dev.funman.infinite.game.DragonGate;
import dev.funman.infinite.game.GraveService;
import dev.funman.infinite.game.HomeStore;
import dev.funman.infinite.game.HubGuard;
import dev.funman.infinite.game.Reincarnation;
import dev.funman.infinite.game.SpawnListener;
import dev.funman.infinite.game.TpaService;
import dev.funman.infinite.substrate.KitLoadout;
import dev.funman.infinite.substrate.PackHttp;
import dev.funman.infinite.substrate.PackOffers;
import dev.funman.infinite.substrate.Substrate;
import dev.funman.infinite.listener.BlackoutListener;
import dev.funman.infinite.listener.BorderCrossListener;
import dev.funman.infinite.listener.VanillaPortalListener;
import dev.funman.infinite.see.ChunkProjector;
import dev.funman.infinite.see.NmsChunkSender;
import dev.funman.infinite.stack.StackWorlds;
import dev.funman.infinite.stack.Topology;
import dev.funman.infinite.worldgen.BlankChunkGenerator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfinitePlugin extends JavaPlugin {
    private InfiniteApi api;
    private PackHttp packHttp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        InfiniteConfig config = InfiniteConfig.load(getConfig());
        Topology topology = new Topology(config);
        StackWorlds worlds = new StackWorlds(this, config);
        Substrate substrate = new Substrate(this, config.reincarnateTimeoutSeconds());
        api = new InfiniteApi(config, topology, worlds, substrate);
        getServer().getServicesManager().register(InfiniteApi.class, api, this, ServicePriority.Normal);

        DragonGate dragonGate = new DragonGate(this, worlds);
        CombatTag combat = new CombatTag(worlds, config.combatTagSeconds());
        HubGuard hub = new HubGuard(worlds, substrate);
        HomeStore homes = new HomeStore(this, worlds, config.maxHomes());
        TpaService tpa = new TpaService(combat, config.tpaTimeoutSeconds());
        Reincarnation reincarnation = new Reincarnation(this, substrate, worlds, homes);

        getServer().getPluginManager().registerEvents(new BorderCrossListener(this, topology, worlds, dragonGate, substrate), this);
        getServer().getPluginManager().registerEvents(new VanillaPortalListener(topology, worlds), this);
        getServer().getPluginManager().registerEvents(new BlackoutListener(worlds), this);
        getServer().getPluginManager().registerEvents(dragonGate, this);
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getPluginManager().registerEvents(new GraveService(this), this);
        getServer().getPluginManager().registerEvents(new SpawnListener(worlds), this);
        getServer().getPluginManager().registerEvents(reincarnation, this);
        getServer().getPluginManager().registerEvents(hub, this);
        KitLoadout kits = new KitLoadout(this, substrate, getConfig().getStringList("integral-client-mods"));
        packHttp = new PackHttp(this, substrate, kits, getConfig().getInt("kit-http-port", 25580));
        packHttp.start();
        getServer().getPluginManager().registerEvents(new PackOffers(this, substrate, packHttp), this);

        NmsChunkSender sender = new NmsChunkSender(getLogger());
        InfiniteCommands.register(this, api, combat, homes, tpa, substrate, reincarnation, kits);

        getServer().getScheduler().runTask(this, () -> {
            worlds.attachDefaultWorlds();
            hub.start(this);
            new ChunkProjector(this, topology, worlds, sender).start();
            getLogger().info("Minecraft Infinite: seed 0 is one vanilla save (overworld+nether+end). Hub is blank.");
        });
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if ("blank".equalsIgnoreCase(id)) {
            return new BlankChunkGenerator();
        }
        var parsed = StackWorlds.parseWorldName(worldName);
        if (parsed != null && parsed.seedIndex() == 0) {
            return new BlankChunkGenerator();
        }
        return null;
    }

    @Override
    public void onDisable() {
        if (packHttp != null) {
            packHttp.stop();
        }
    }

    public InfiniteApi api() {
        return api;
    }
}
