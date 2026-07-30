package sonar.fluxnetworks.common.integration.gtceu;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.LongInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.api.network.SecurityLevel;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.FluxNetworkData;
import sonar.fluxnetworks.common.connection.ITransferNode;
import sonar.fluxnetworks.common.connection.ServerFluxNetwork;
import sonar.fluxnetworks.common.connection.TransferHandler;
import sonar.fluxnetworks.common.device.TileFluxDevice;
import sonar.fluxnetworks.common.util.EnergyUtils;
import sonar.fluxnetworks.common.util.FluxUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * This is the base class for flux energy hatches. A flux hatch is a GT
 * multiblock part machine that is also a full flux network member: it
 * implements {@link ITransferNode} on the {@code MetaMachine}, not on a block
 * entity. The hatch follows the buffer-device model: the network fills or
 * drains an internal buffer in the network's native energy type, and the GT
 * recipe path sees that buffer as an EU energy container. See
 * {@link sonar.fluxnetworks.api.energy.HatchBufferMath} for the unit math.
 * <p>
 * The hatch has no Flux GUI. It uses GT's fancy UI instead. The UI shows the
 * bound network and the buffer state, lists joinable networks, and edits the
 * transfer settings (priority, limit, surge, bypass).
 * <p>
 * Class-loading contract: this class imports {@code com.gregtechceu} and
 * {@code com.lowdragmc} types. The game must load it only when the gtceu mod
 * is present. {@link FluxGTRegistration} creates instances, and only
 * {@code FluxNetworks}'s gtceu-gated constructor block reaches that class.
 *
 * @see FluxEnergyInputHatchMachine
 * @see FluxDynamoHatchMachine
 */
public abstract class FluxHatchPartMachine extends TieredIOPartMachine implements ITransferNode, IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(FluxHatchPartMachine.class, TieredIOPartMachine.MANAGED_FIELD_HOLDER);

    /**
     * The network ID that this hatch connects to. Persisted value.
     * Not synced to clients; the GT UI ships display text instead.
     */
    protected int mNetworkID = FluxConstants.INVALID_NETWORK_ID;

    /**
     * Leave empty to show a localized name. Persisted value.
     */
    @Nonnull
    protected String mCustomName = "";

    /**
     * Player UUID of the hatch's owner. Persisted value.
     */
    @Nonnull
    protected UUID mOwnerUUID = Util.NIL_UUID;

    /**
     * The password typed into the GT UI text field. Transient value, never
     * persisted and never echoed back to clients.
     */
    @Nonnull
    protected String mPendingPassword = "";

    /**
     * Server only. Marks that this hatch announced itself to its network.
     * This mirrors {@code TileFluxDevice}'s FLAG_FIRST_TICKED.
     */
    private boolean mLoaded;

    /**
     * One-shot subscription that delays the first connect to the first server
     * tick, when {@link FluxNetworkData} is safe to query.
     */
    @Nullable
    private TickableSubscription mFirstTickSubs;

    /**
     * Lazy-loading, the level is not set at construction time. Non-persisted value.
     */
    @Nullable
    private GlobalPos mGlobalPos;

    // server only, the connected network
    @Nonnull
    private FluxNetwork mNetwork = FluxNetwork.INVALID;

    protected FluxHatchPartMachine(IMachineBlockEntity holder, int tier, IO io) {
        super(holder, tier, io);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //// LIFECYCLE \\\\

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            // SavedData may not be ready during load. Delay the connect to the
            // first server tick, like TileFluxDevice#onFirstTick.
            mFirstTickSubs = subscribeServerTick(this::onFirstTick);
        }
    }

    private void onFirstTick() {
        unsubscribe(mFirstTickSubs);
        mFirstTickSubs = null;
        if (mLoaded) {
            return;
        }
        mLoaded = true;
        // When the GT integration is disabled by config, the bridge is null and
        // the hatch stays disconnected. The saved network ID is kept, so the
        // hatch reconnects after the config is enabled again.
        if (EnergyUtils.getGTEnergyBridge() != null) {
            connect(FluxNetworkData.getNetwork(mNetworkID));
        }
    }

    /**
     * Called when a player breaks the hatch. The membership is removed
     * permanently, like {@code TileFluxDevice#setRemoved}.
     */
    @Override
    public void onMachineRemoved() {
        if (!isRemote() && mLoaded) {
            mNetwork.enqueueConnectionRemoval(this, false);
            getTransferHandler().onNetworkChanged();
            mLoaded = false;
        }
    }

    /**
     * Called when the block entity invalidates, which includes chunk unload.
     * The network keeps a phantom record, like {@code TileFluxDevice#onChunkUnloaded}.
     * After {@link #onMachineRemoved()} already ran, this method does nothing.
     */
    @Override
    public void onUnload() {
        super.onUnload();
        if (!isRemote() && mLoaded) {
            mNetwork.enqueueConnectionRemoval(this, true);
            getTransferHandler().onNetworkChanged();
            mLoaded = false;
        }
    }

    //// PERSISTENCE \\\\

    @Override
    public void saveCustomPersistedData(@Nonnull CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            // Rough draft: the hatch drops as a fresh item, settings are lost.
            return;
        }
        CompoundTag subTag = new CompoundTag();
        writeCustomTag(subTag, FluxConstants.NBT_SAVE_ALL);
        tag.put(FluxConstants.TAG_FLUX_DATA, subTag);
    }

    @Override
    public void loadCustomPersistedData(@Nonnull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains(FluxConstants.TAG_FLUX_DATA, Tag.TAG_COMPOUND)) {
            readCustomTag(tag.getCompound(FluxConstants.TAG_FLUX_DATA), FluxConstants.NBT_SAVE_ALL);
        }
    }

    @Override
    public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
        // the two most basic data, regardless of type
        tag.putInt(FluxConstants.NETWORK_ID, mNetworkID);
        tag.putString(FluxConstants.CUSTOM_NAME, mCustomName);
        getTransferHandler().writeCustomTag(tag, type);
        switch (type) {
            case FluxConstants.NBT_SAVE_ALL -> tag.putUUID(FluxConstants.PLAYER_UUID, mOwnerUUID);
            case FluxConstants.NBT_TILE_UPDATE ->
                // No client color and no flags: the hatch renders as a GT machine,
                // and it never opens a FluxMenu.
                    tag.putUUID(FluxConstants.PLAYER_UUID, mOwnerUUID);
            case FluxConstants.NBT_PHANTOM_UPDATE -> {
                FluxUtils.writeGlobalPos(tag, getGlobalPos());
                tag.putByte(FluxConstants.DEVICE_TYPE, getDeviceType().getId());
                tag.putUUID(FluxConstants.PLAYER_UUID, mOwnerUUID);
                tag.putBoolean(FluxConstants.FORCED_LOADING, isForcedLoading());
                tag.putBoolean(FluxConstants.CHUNK_LOADED, isChunkLoaded());
                getDisplayStack().save(tag);
            }
        }
    }

    @Override
    public void readCustomTag(@Nonnull CompoundTag tag, byte type) {
        if (type == FluxConstants.NBT_TILE_SETTINGS) {
            assert !isRemote();
            if (tag.isEmpty()) {
                return;
            }
            if (tag.contains(FluxConstants.CUSTOM_NAME)) {
                String name = tag.getString(FluxConstants.CUSTOM_NAME);
                if (name.length() <= TileFluxDevice.MAX_CUSTOM_NAME_LENGTH) {
                    mCustomName = name;
                }
            }
            // No forced-loading and no chunk-loading branch: the hatch does not
            // support chunk loading.
            onSettingsChanged(getTransferHandler().changeSettings(tag));
            return;
        }
        mNetworkID = tag.getInt(FluxConstants.NETWORK_ID);
        mCustomName = tag.getString(FluxConstants.CUSTOM_NAME);
        getTransferHandler().readCustomTag(tag, type);
        switch (type) {
            case FluxConstants.NBT_SAVE_ALL, FluxConstants.NBT_TILE_UPDATE ->
                    mOwnerUUID = tag.getUUID(FluxConstants.PLAYER_UUID);
        }
    }

    //// ITransferNode \\\\

    /**
     * Connect this device to a flux network. Server only.
     * Check access first. Called outside network ticking cycle.
     * This ports {@code TileFluxDevice#connect}.
     *
     * @param network the server network to connect, can be invalid
     * @return true if successfully connected to the network
     */
    @Override
    public boolean connect(@Nonnull FluxNetwork network) {
        assert !isRemote();
        if (mNetwork == network) {
            return true;
        }
        if (network.enqueueConnectionAddition(this)) {
            mNetwork.enqueueConnectionRemoval(this, false);
            mNetwork = network;
            mNetworkID = mNetwork.getNetworkID();
            getTransferHandler().onNetworkChanged();
            if (network.isValid()) {
                // Re-denominate the buffer and the limit if the network's energy
                // type changed while this device was unloaded or disconnected.
                getTransferHandler().reconcileEnergyUnit(network.getEnergyType());
            }
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * Connect this device to an invalid network (i.e. disconnect).
     */
    @Override
    public final void disconnect() {
        connect(FluxNetwork.INVALID);
    }

    @Override
    public final int getNetworkID() {
        return mNetworkID;
    }

    /**
     * @return the connected network, always invalid on the client
     */
    @Nonnull
    protected final FluxNetwork getNetwork() {
        return mNetwork;
    }

    /**
     * Server-only. This ports {@code TileFluxDevice#canPlayerAccess}.
     *
     * @param player the player to access this
     * @return should access
     */
    @Override
    public boolean canPlayerAccess(@Nonnull Player player) {
        assert !isRemote();
        // devices without a network connection are not protected (e.g. abandoned).
        if (mNetwork.isValid()) {
            if (player.getUUID().equals(mOwnerUUID)) {
                return true;
            }
            return mNetwork.canPlayerAccess(player);
        }
        return true;
    }

    @Override
    public final void setOwnerUUID(@Nonnull UUID uuid) {
        if (!mOwnerUUID.equals(uuid)) {
            mOwnerUUID = uuid;
            markDirty();
        }
    }

    /**
     * Marks this device's energy state as changed. The holder block entity
     * marks its chunk unsaved, so the buffer persists.
     */
    @Override
    public void markEnergyChanged() {
        markDirty();
    }

    /**
     * No-op. The hatch renders as a GT machine, with no network color, so a
     * client block update carries no information.
     */
    @Override
    public void sendBlockUpdate() {
    }

    @Nonnull
    @Override
    public final BlockPos getBlockPos() {
        return getPos();
    }

    @Nonnull
    @Override
    public final GlobalPos getGlobalPos() {
        if (mGlobalPos == null) {
            mGlobalPos = GlobalPos.of(getLevel().dimension(), getPos());
        }
        return mGlobalPos;
    }

    /**
     * Write hot data to a byte buffer. Hot data is what's updated almost every tick,
     * such as energy changes. Same delegation as {@code TileFluxDevice}.
     *
     * @param buf  the byte buf
     * @param type the type id
     */
    @Override
    public void writePacketBuffer(@Nonnull FriendlyByteBuf buf, byte type) {
        getTransferHandler().writePacketBuffer(buf, type);
    }

    /**
     * Read hot data from a byte buffer. Hot data is what's updated almost every tick,
     * such as energy changes. Same delegation as {@code TileFluxDevice}.
     *
     * @param buf  the byte buf
     * @param type the type id
     */
    @Override
    public void readPacketBuffer(@Nonnull FriendlyByteBuf buf, byte type) {
        getTransferHandler().readPacketBuffer(buf, type);
    }

    @Nonnull
    @Override
    public final UUID getOwnerUUID() {
        return mOwnerUUID;
    }

    @Nonnull
    @Override
    public final String getCustomName() {
        return mCustomName;
    }

    @Override
    public final int getRawPriority() {
        return getTransferHandler().getRawPriority();
    }

    @Override
    public final long getRawLimit() {
        return getTransferHandler().getRawLimit();
    }

    @Override
    public final boolean getSurgeMode() {
        return getTransferHandler().getSurgeMode();
    }

    @Override
    public final boolean getDisableLimit() {
        return getTransferHandler().getDisableLimit();
    }

    @Override
    public long getMaxTransferLimit() {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isChunkLoaded() {
        return !isInValid();
    }

    @Override
    public boolean isForcedLoading() {
        return false;
    }

    @Override
    public final long getTransferBuffer() {
        return getTransferHandler().getBuffer();
    }

    @Override
    public final long getTransferChange() {
        return getTransferHandler().getChange();
    }

    @Nonnull
    @Override
    public ItemStack getDisplayStack() {
        return getDefinition().asStack();
    }

    @Override
    public void onPlayerOpened(@Nonnull Player player) {
    }

    @Override
    public void onPlayerClosed(@Nonnull Player player) {
    }

    /**
     * The hatch uses the GT fancy UI, never FluxMenu.
     *
     * @return always null
     */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory inventory, @Nonnull Player player) {
        return null;
    }

    //// GT UI \\\\

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        if (isRemote()) {
            // the server does the authoritative check
            return true;
        }
        if (EnergyUtils.getGTEnergyBridge() == null) {
            // GT integration disabled by config
            return false;
        }
        return canPlayerAccess(player);
    }

    @Override
    public boolean hasPlayerInventory() {
        return false;
    }

    /**
     * Builds the native GT page. The layout is a vertical stack: a scrollable
     * display panel with the network state and the join list, then a password
     * field with two toggle buttons, then the priority and limit inputs.
     */
    @Override
    public Widget createUIWidget() {
        // Height 126, not 120: NumberInputWidget hardcodes its children to 20 px
        // tall, so the two number rows at y = 102 end at y = 122.
        WidgetGroup root = new WidgetGroup(0, 0, 200, 126);
        // The panel reference is captured by both lambdas. The text supplier
        // runs once inside the constructor, before the assignment, so it must
        // accept a null panel.
        final ComponentPanelWidget[] panelRef = new ComponentPanelWidget[1];
        panelRef[0] = new ComponentPanelWidget(4, 4, text -> buildDisplayText(panelRef[0], text));
        panelRef[0].setMaxWidthLimit(180);
        panelRef[0].clickHandler((componentData, clickData) -> handleDisplayClick(panelRef[0], componentData, clickData));
        DraggableScrollableWidgetGroup display = new DraggableScrollableWidgetGroup(4, 4, 192, 76)
                .setBackground(GuiTextures.DISPLAY);
        display.addWidget(panelRef[0]);
        root.addWidget(display);

        // Write-only password field: the null supplier means the server never
        // echoes the text back. Note: LDLib has no password masking, the typed
        // text shows as plain characters on the typing client only.
        TextFieldWidget password = new TextFieldWidget(4, 84, 90, 14,
                null, s -> mPendingPassword = s);
        password.setMaxStringLength(FluxNetwork.MAX_PASSWORD_LENGTH);
        password.setHoverTooltips("gui.fluxnetworks.hatch.password");
        root.addWidget(password);

        root.addWidget(new ToggleButtonWidget(98, 84, 14, 14, GuiTextures.BUTTON_POWER,
                this::getSurgeMode, this::changeSurgeMode)
                .setShouldUseBaseBackground()
                .setTooltipText("gui.fluxnetworks.hatch.surge"));
        root.addWidget(new ToggleButtonWidget(114, 84, 14, 14, GuiTextures.BUTTON_LOCK,
                this::getDisableLimit, this::changeDisableLimit)
                .setShouldUseBaseBackground()
                .setTooltipText("gui.fluxnetworks.hatch.disable_limit"));

        IntInputWidget priority = new IntInputWidget(4, 102, 94, 14,
                this::getRawPriority, this::changePriority);
        priority.setMin(TransferHandler.PRI_USER_MIN);
        priority.setMax(TransferHandler.PRI_USER_MAX);
        root.addWidget(priority);

        LongInputWidget limit = new LongInputWidget(102, 102, 94, 14,
                this::getRawLimit, this::changeLimit);
        limit.setMin(0L);
        root.addWidget(limit);
        return root;
    }

    /**
     * Server-side text supplier for the display panel. It runs every tick in
     * {@code detectAndSendChanges}, and the rendered list ships to the client.
     *
     * @param panel the display panel, null during widget construction
     * @param text  the output line list
     */
    private void buildDisplayText(@Nullable ComponentPanelWidget panel, @Nonnull List<Component> text) {
        if (isRemote()) {
            // the client renders the synced copy
            return;
        }
        final TransferHandler handler = getTransferHandler();
        final FluxNetwork network = mNetwork;
        if (network.isValid()) {
            MutableComponent title = Component.literal(network.getNetworkName())
                    .withStyle(style -> style.withColor(network.getNetworkColor()));
            title.append(" ").append(ComponentPanelWidget.withButton(
                    Component.translatable("gui.fluxnetworks.hatch.leave"), "disconnect"));
            text.add(title);
        } else {
            text.add(Component.translatable("gui.fluxnetworks.hatch.no_network"));
        }
        final EnergyType unit = handler.getEnergyUnit();
        text.add(Component.translatable("gui.fluxnetworks.hatch.buffer", unit.getStorage(handler.getBuffer())));
        text.add(Component.translatable("gui.fluxnetworks.hatch.change", unit.getUsage(handler.getChange())));
        // The supplier has no player argument. The opening player comes from
        // the ModularUI that owns the panel; on the server this is the real
        // ServerPlayer.
        final Player player = panel == null || panel.getGui() == null ? null : panel.getGui().entityPlayer;
        if (player == null) {
            return;
        }
        text.add(Component.empty());
        text.add(Component.translatable("gui.fluxnetworks.hatch.networks"));
        for (FluxNetwork n : FluxNetworkData.getAllNetworks()) {
            if (n == network) {
                continue;
            }
            // List the networks this player can join now. An encrypted network
            // appears once the typed password unlocks it.
            if (!n.canPlayerAccess(player, mPendingPassword)) {
                continue;
            }
            MutableComponent row = Component.literal(n.getNetworkName())
                    .withStyle(style -> style.withColor(n.getNetworkColor()));
            if (n.getSecurityLevel() != SecurityLevel.PUBLIC) {
                // text-only security marker, [E] = encrypted
                row.append(" [E]");
            }
            row.append(" ").append(ComponentPanelWidget.withButton(
                    Component.translatable("gui.fluxnetworks.hatch.join"), "join:" + n.getNetworkID()));
            text.add(row);
        }
    }

    /**
     * Handles a click on a display panel button. The client and the server
     * both invoke this handler; only the server branch acts.
     */
    private void handleDisplayClick(@Nonnull ComponentPanelWidget panel,
                                    @Nonnull String componentData, @Nonnull ClickData clickData) {
        if (clickData.isRemote || isRemote()) {
            return;
        }
        if (EnergyUtils.getGTEnergyBridge() == null) {
            return;
        }
        final Player player = panel.getGui() == null ? null : panel.getGui().entityPlayer;
        if (player == null) {
            return;
        }
        if ("disconnect".equals(componentData)) {
            if (canPlayerAccess(player)) {
                disconnect();
            }
        } else if (componentData.startsWith("join:")) {
            final int id;
            try {
                id = Integer.parseInt(componentData.substring(5));
            } catch (NumberFormatException e) {
                return;
            }
            final FluxNetwork network = FluxNetworkData.getNetwork(id);
            // re-check access on the server, with the transient password
            if (network.isValid() && network.canPlayerAccess(player, mPendingPassword)) {
                setOwnerUUID(player.getUUID());
                connect(network);
            }
            // on failure this is a silent no-op (rough draft: no toast)
        }
    }

    //// SERVER-SIDE SETTING RESPONDERS \\\\

    private void changePriority(int priority) {
        if (!isRemote()) {
            onSettingsChanged(getTransferHandler().setPriority(priority));
        }
    }

    private void changeLimit(long limit) {
        if (!isRemote()) {
            getTransferHandler().setLimit(limit);
            onSettingsChanged(false);
        }
    }

    private void changeSurgeMode(boolean surgeMode) {
        if (!isRemote()) {
            onSettingsChanged(getTransferHandler().setSurgeMode(surgeMode));
        }
    }

    private void changeDisableLimit(boolean disableLimit) {
        if (!isRemote()) {
            getTransferHandler().setDisableLimit(disableLimit);
            onSettingsChanged(false);
        }
    }

    /**
     * Marks the machine dirty after a setting change, and re-sorts the network
     * when the logical priority changed. This mirrors the sort-marking in
     * {@code TileFluxDevice#readCustomTag(NBT_TILE_SETTINGS)}.
     *
     * @param sort true if the network must re-sort its connections
     */
    private void onSettingsChanged(boolean sort) {
        if (sort && mNetwork instanceof ServerFluxNetwork network) {
            network.markSortConnections();
        }
        markDirty();
    }
}
