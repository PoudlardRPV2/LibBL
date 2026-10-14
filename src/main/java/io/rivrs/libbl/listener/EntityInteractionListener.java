package io.rivrs.libbl.listener;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import io.rivrs.libbl.LibBL;
import io.rivrs.libbl.event.entity.PacketEntityAttackEvent;
import io.rivrs.libbl.event.entity.PacketEntityInteractEvent;
import io.rivrs.libbl.model.entities.PacketEntity;
import io.rivrs.libbl.model.entities.entity.PacketInteraction;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;

@RequiredArgsConstructor
public class EntityInteractionListener extends SimplePacketListenerAbstract {

    /** Vanilla's tolerance on top of the interaction range (ServerGamePacketListenerImpl#handleInteract). */
    private static final double REACH_BUFFER = 3.0;
    /** Vanilla's default {@code entity_interaction_range}. */
    private static final double DEFAULT_INTERACTION_RANGE = 3.0;

    private final LibBL plugin;

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (!event.getPacketType().equals(PacketType.Play.Client.INTERACT_ENTITY))
            return;

        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        this.plugin.entityService()
                .findById(packet.getEntityId())
                .ifPresent(packetEntity -> {
                    event.setCancelled(true);

                    Player player = event.getPlayer();

                    if (!withinReach(player, packetEntity)) {
                        // Too far away, the packet is likely spoofed and we ignore it
                        return;
                    }

                    MainHand hand = packet.getHand().equals(InteractionHand.MAIN_HAND) ? MainHand.RIGHT : MainHand.LEFT;

                    if (packet.getAction().equals(WrapperPlayClientInteractEntity.InteractAction.ATTACK)) {
                        new PacketEntityAttackEvent(packetEntity, player, packet.isSneaking().orElse(false), hand).callEvent();
                        return;
                    }

                    new PacketEntityInteractEvent(packetEntity, player, packet.isSneaking().orElse(false), hand).callEvent();
                });
    }

    /**
     * The anti-spoof guard, as vanilla does it for a real entity: the player's eyes must be within
     * their interaction range plus {@link #REACH_BUFFER} of the closest point of the entity's box.
     *
     * <p>Measuring from the feet to the entity's location (the <em>bottom</em> of an interaction's
     * box) refused a button hanging a few blocks above the ground to a player standing right in
     * front of it.</p>
     */
    static boolean withinReach(Player player, PacketEntity entity) {
        Location eye = player.getEyeLocation();
        Location base = entity.location();
        if (base.getWorld() == null || !base.getWorld().equals(eye.getWorld())) {
            return false;
        }
        double halfWidth = 0;
        double height = 0;
        if (entity instanceof PacketInteraction interaction) {
            halfWidth = interaction.width() / 2.0;
            height = interaction.height();
        }
        double dx = outside(eye.getX(), base.getX() - halfWidth, base.getX() + halfWidth);
        double dy = outside(eye.getY(), base.getY(), base.getY() + height);
        double dz = outside(eye.getZ(), base.getZ() - halfWidth, base.getZ() + halfWidth);
        double reach = interactionRange(player) + REACH_BUFFER;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /** Distance from {@code value} to the segment {@code [min, max]}, zero inside it. */
    private static double outside(double value, double min, double max) {
        return value < min ? min - value : value > max ? value - max : 0;
    }

    private static double interactionRange(Player player) {
        AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        return range != null ? range.getValue() : DEFAULT_INTERACTION_RANGE;
    }

    @Override
    public PacketListenerPriority getPriority() {
        return PacketListenerPriority.MONITOR;
    }
}
