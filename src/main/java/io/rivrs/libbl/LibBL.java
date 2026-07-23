package io.rivrs.libbl;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.rivrs.libbl.listener.EntityInteractionListener;
import io.rivrs.libbl.listener.MapListener;
import io.rivrs.libbl.listener.PlayerListener;
import io.rivrs.libbl.service.BlockService;
import io.rivrs.libbl.service.EntityService;
import io.rivrs.libbl.service.ViewerService;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Façade embarquable de LibBL (variante Poudlard, branche {@code claude/holograms-archi-review-ouhkz3}).
 *
 * <p>Contrairement à l'upstream où {@code LibBL extends JavaPlugin}, cette variante n'est
 * <b>pas</b> un plugin : elle est initialisée par le plugin hôte via {@link #init(JavaPlugin)}
 * (depuis son {@code enable()}) et arrêtée via {@link #shutdown()}. Elle n'initialise
 * <b>jamais</b> PacketEvents — l'instance partagée est fournie par le plugin standalone
 * {@code packetevents}, et l'hôte déclare {@code depend: [packetevents]}. LibBL se contente de
 * {@code PacketEvents.getAPI()}.
 */
@Getter
public final class LibBL {

    @Getter
    private static int ENTITY_SIMULATION_DISTANCE_SQR;
    @Getter
    private static int RENDER_DISTANCE_SQR;
    @Getter
    private static int ENTITY_UPDATE_RATE;

    /** Cadence par défaut de l'update-loop des entités (ms). Ex-{@code config.yml} du plugin standalone. */
    private static final int DEFAULT_ENTITY_UPDATE_RATE_MS = 250;

    private static LibBL instance;

    private final JavaPlugin plugin;
    private final EntityService entityService;
    private final BlockService blockService;
    private final ViewerService viewerService;

    private LibBL(JavaPlugin plugin) {
        this.plugin = plugin;
        this.entityService = new EntityService(plugin);
        this.blockService = new BlockService(plugin);
        this.viewerService = new ViewerService(plugin);
    }

    public static LibBL get() {
        return instance;
    }

    /** Initialise LibBL en mode embarqué avec la cadence d'update par défaut. */
    public static void init(JavaPlugin plugin) {
        init(plugin, DEFAULT_ENTITY_UPDATE_RATE_MS);
    }

    /**
     * Initialise LibBL en mode embarqué. À appeler depuis {@code enable()} du plugin hôte,
     * <b>après</b> que PacketEvents a été initialisé par le plugin standalone (garanti par
     * {@code depend: [packetevents]} + {@code depend: [PoudlardCore]}).
     *
     * @param plugin              le plugin hôte, utilisé comme owner Bukkit (schedulers, listeners, logger)
     * @param entityUpdateRateMs  cadence de l'update-loop des entités, en millisecondes
     */
    public static void init(JavaPlugin plugin, int entityUpdateRateMs) {
        int simulationDistance = plugin.getServer().getSimulationDistance() * 16;
        ENTITY_SIMULATION_DISTANCE_SQR = simulationDistance * simulationDistance;
        int renderDistance = plugin.getServer().getViewDistance() * 16;
        RENDER_DISTANCE_SQR = renderDistance * renderDistance;
        ENTITY_UPDATE_RATE = entityUpdateRateMs;

        LibBL libBL = new LibBL(plugin);
        libBL.entityService.init();
        libBL.blockService.init();
        libBL.viewerService.init();

        new PlayerListener(plugin, libBL.entityService, libBL.viewerService);

        PacketEvents.getAPI().getEventManager().registerListener(new EntityInteractionListener(libBL));
        PacketEvents.getAPI().getEventManager()
                .registerListener(new MapListener(libBL), PacketListenerPriority.HIGHEST);

        instance = libBL;
    }

    public static void shutdown() {
        if (instance == null) {
            return;
        }
        instance.entityService.shutdown();
        instance.blockService.shutdown();
        instance.viewerService.shutdown();
        instance = null;
    }
}
