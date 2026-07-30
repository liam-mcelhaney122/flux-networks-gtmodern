package sonar.fluxnetworks.common.connection;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import sonar.fluxnetworks.api.device.IFluxDevice;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * The surface that {@link ServerFluxNetwork} and the packet layer need from a network member.
 * <p>
 * {@link sonar.fluxnetworks.common.device.TileFluxDevice} is the block entity implementation of
 * this interface. A future GregTech machine hatch can implement this interface directly without
 * being a block entity, allowing non-block devices to join flux networks.
 * <p>
 * {@link IFluxDevice} (api package) supplies the descriptive getters used for display and
 * persistence; this interface lives in the common package because it references
 * {@link TransferHandler}, which is an internal implementation type.
 */
public interface ITransferNode extends IFluxDevice {

    /**
     * Returns the transfer handler that manages energy transfer logic for this device.
     *
     * @return the transfer handler
     */
    @Nonnull
    TransferHandler getTransferHandler();

    /**
     * Connect this device to a flux network. Server only.
     * Check access first. Called outside network ticking cycle.
     *
     * @param network the server network to connect, can be invalid
     * @return true if successfully connected to the network
     */
    boolean connect(@Nonnull FluxNetwork network);

    /**
     * Connect this device to an invalid network (i.e. disconnect).
     */
    void disconnect();

    void setOwnerUUID(@Nonnull UUID uuid);

    /**
     * Server-only.
     *
     * @param player the player to access this
     * @return should access
     */
    boolean canPlayerAccess(@Nonnull Player player);

    /**
     * Marks this device's energy state as changed, so it will be synced later.
     */
    void markEnergyChanged();

    /**
     * Sends a block update to nearby clients. Server only.
     */
    void sendBlockUpdate();

    /**
     * Returns the block position of this device.
     *
     * @return the block position
     */
    @Nonnull
    BlockPos getBlockPos();

    /**
     * Write hot data to a byte buffer. Hot data is what's updated almost every tick,
     * such as energy changes.
     *
     * @param buf  the byte buf
     * @param type the type id
     */
    void writePacketBuffer(@Nonnull FriendlyByteBuf buf, byte type);

    /**
     * Read hot data from a byte buffer. Hot data is what's updated almost every tick,
     * such as energy changes.
     *
     * @param buf  the byte buf
     * @param type the type id
     */
    void readPacketBuffer(@Nonnull FriendlyByteBuf buf, byte type);
}
