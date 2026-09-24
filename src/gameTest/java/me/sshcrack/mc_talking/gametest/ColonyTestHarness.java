package me.sshcrack.mc_talking.gametest;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.authlib.GameProfile;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
/*? if neoforge {*/
import net.neoforged.neoforge.common.util.FakePlayerFactory;
/*?}*/
/*? if forge {*/
/*import net.minecraftforge.common.util.FakePlayerFactory;
*//*?}*/

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Minimal MineColonies fixture for server GameTests.
 *
 * <p>Creates a real colony (owned by a loader fake player) anchored inside the test structure,
 * spawns real {@link EntityCitizen}s registered with that colony, and places real hut blocks so
 * MineColonies registers genuine buildings. All positions are structure-relative. The fixture never
 * talks to Gemini: it clears the configured API key for its lifetime so autonomous ambient speech
 * short-circuits, and restores the key on {@link #close()}.</p>
 *
 * <p>Use one fixture per test and always close it (try-with-resources) so the colony is deleted
 * before the next GameTest batch runs in the same world.</p>
 */
public final class ColonyTestHarness implements AutoCloseable {
    /**
     * Colony owner. A loader fake player is a real {@link ServerPlayer} (MineColonies requires one)
     * whose network handler swallows packets; GameTestHelper's mock player instead logs in through
     * the player list and MineColonies' login sync rejects its unnegotiated connection.
     */
    private static final GameProfile OWNER_PROFILE =
            new GameProfile(UUID.fromString("6d635f74-616c-6b69-6e67-67616d657465"), "mc_talking_gametest");

    /** MineColonies' default bundled style; hut registration dereferences the colony's pack. */
    private static final String STRUCTURE_PACK = "Colonial";

    private final GameTestHelper helper;
    private final ServerLevel level;
    private final ServerPlayer owner;
    private final IColony colony;
    private final List<EntityCitizen> citizens = new ArrayList<>();
    private final String savedApiKey;
    private final boolean savedMemory;

    private ColonyTestHarness(GameTestHelper helper) {
        this.helper = helper;
        this.level = helper.getLevel();
        var config = McTalkingConfig.INSTANCE.instance();
        this.savedApiKey = config.geminiApiKey;
        this.savedMemory = config.enableConversationSummaryAndMemorize;
        // Never reach a real provider from a GameTest. These overrides are never saved.
        config.geminiApiKey = "";
        config.enableConversationSummaryAndMemorize = false;

        this.owner = FakePlayerFactory.get(level, OWNER_PROFILE);
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        this.colony = IColonyManager.getInstance().createColony(level, center, owner, "GameTest colony", STRUCTURE_PACK);
        if (colony == null) throw new GameTestAssertException("MineColonies refused to create the fixture colony");
    }

    public static ColonyTestHarness create(GameTestHelper helper) {
        return new ColonyTestHarness(helper);
    }

    public IColony colony() {
        return colony;
    }

    public ServerPlayer owner() {
        return owner;
    }

    /** Spawns a real, AI-disabled colony citizen standing at the given structure-relative position. */
    public EntityCitizen spawnCitizen(BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        ICitizenData data = colony.getCitizenManager().createAndRegisterCivilianData();
        EntityCitizen entity = (EntityCitizen) ModEntities.CITIZEN.create(level);
        if (entity == null) throw new GameTestAssertException("Cannot create citizen entity");
        entity.setUUID(data.getUUID());
        entity.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        entity.setCitizenId(data.getId());
        entity.getCitizenColonyHandler().setColonyId(colony.getID());
        // Deterministic fixtures: MineColonies AI would otherwise walk, sleep or wake on its own.
        entity.setNoAi(true);
        level.addFreshEntity(entity);
        entity.getCitizenColonyHandler().registerWithColony(colony.getID(), data.getId());
        citizens.add(entity);
        return entity;
    }

    public static ICitizenData data(EntityCitizen citizen) {
        ICitizenData data = citizen.getCitizenData();
        if (data == null) throw new GameTestAssertException("Citizen has no colony data");
        return data;
    }

    /**
     * Places a MineColonies hut block (typed as {@link Block} so GameTests need not compile against
     * Structurize, which is runtime-only here) and registers the resulting building with this colony the
     * same way a player placement does ({@code setPlacedBy}). The building is then raised to
     * {@code buildingLevel} so level-gated modules (beds, worker slots) accept assignments.
     */
    public IBuilding placeHut(Block hut, BlockPos relative, int buildingLevel) {
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = hut.defaultBlockState();
        level.setBlock(pos, state, 3);
        hut.setPlacedBy(level, pos, state, owner, ItemStack.EMPTY);
        IBuilding building = IColonyManager.getInstance().getBuilding(level, pos);
        if (building == null && level.getBlockEntity(pos)
                instanceof com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding tile) {
            // Claim lookup by position failed; register directly with the fixture colony.
            building = colony.getServerBuildingManager().addNewBuilding(tile, level);
        }
        if (building == null) throw new GameTestAssertException("Hut at " + relative + " did not register a building");
        building.setBuildingLevel(buildingLevel);
        return building;
    }

    /** Assigns the citizen to a residence through MineColonies' living module. */
    public void assignHome(IBuilding residence, EntityCitizen citizen) {
        var living = residence.getModule(BuildingModules.LIVING);
        if (living == null) throw new GameTestAssertException("Residence has no living module");
        if (!living.assignCitizen(data(citizen))) throw new GameTestAssertException("Residence refused citizen");
    }

    /** Hires the citizen in the builder's hut through MineColonies' worker module (sets the job). */
    public void hireAsBuilder(IBuilding builderHut, EntityCitizen citizen) {
        var work = builderHut.getModule(BuildingModules.BUILDER_WORK);
        if (work == null) throw new GameTestAssertException("Builder hut has no worker module");
        if (!work.assignCitizen(data(citizen))) throw new GameTestAssertException("Builder hut refused citizen");
    }

    /**
     * Places a bed at the structure-relative foot position (head to the north) and puts the citizen
     * to sleep in it through MineColonies' own sleep handler.
     */
    public void sleepInBed(EntityCitizen citizen, BlockPos relativeFoot) {
        BlockPos foot = helper.absolutePos(relativeFoot);
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
        level.setBlock(foot, footState, 3);
        level.setBlock(foot.north(), footState.setValue(BedBlock.PART, BedPart.HEAD), 3);
        if (!citizen.getCitizenSleepHandler().trySleep(foot)) {
            throw new GameTestAssertException("MineColonies refused to put the citizen to sleep");
        }
    }

    @Override
    public void close() {
        try {
            for (EntityCitizen citizen : citizens) {
                if (citizen.isAlive()) citizen.discard();
            }
            IColonyManager.getInstance().deleteColonyByWorld(colony.getID(), false, level);
        } finally {
            var config = McTalkingConfig.INSTANCE.instance();
            config.geminiApiKey = savedApiKey;
            config.enableConversationSummaryAndMemorize = savedMemory;
        }
    }
}
