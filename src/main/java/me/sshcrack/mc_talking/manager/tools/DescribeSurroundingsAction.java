package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * AI tool that provides the citizen with a real-time description of their
 * immediate surroundings: time, weather, nearby players, other citizens,
 * animals, hostile mobs, and visible colony buildings.
 */
public class DescribeSurroundingsAction extends FunctionAction {

    private static final double SCAN_RADIUS = 20.0;

    public DescribeSurroundingsAction() {
        super(
                "describe_surroundings",
                "Describes what is currently around you in the game world: the time of day, " +
                "weather, nearby players, fellow citizens, animals, monsters, and visible " +
                "colony buildings within roughly 20 blocks. Use this to ground your " +
                "conversation in what is actually happening around you right now."
        );
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        JsonObject result = new JsonObject();
        Level level = citizen.level();
        BlockPos pos = citizen.blockPosition();

        // ── Time & weather ──────────────────────────────────────────────────
        long dayTime = level.getDayTime() % 24000L;
        result.addProperty("time_of_day", MiscUtil.describeTime(dayTime));
        result.addProperty("is_raining", level.isRaining());
        result.addProperty("is_thundering", level.isThundering());
        result.addProperty("sky_light", level.getBrightness(LightLayer.SKY, pos));

        // ── Nearby entities ─────────────────────────────────────────────────
        AABB scanBox = citizen.getBoundingBox().inflate(SCAN_RADIUS);

        // Players
        JsonArray players = new JsonArray();
        level.getEntitiesOfClass(Player.class, scanBox).forEach(p -> {
            if (p.getUUID().equals(citizen.getUUID())) return;
            JsonObject pObj = new JsonObject();
            pObj.addProperty("name", p.getName().getString());
            pObj.addProperty("distance_blocks", (int) citizen.distanceTo(p));
            players.add(pObj);
        });
        result.add("nearby_players", players);

        // Fellow citizens
        JsonArray citizens = new JsonArray();
        level.getEntitiesOfClass(AbstractEntityCitizen.class, scanBox).forEach(c -> {
            if (c.getUUID().equals(citizen.getUUID())) return;
            JsonObject cObj = new JsonObject();
            String name = c.getCitizenData() != null ? c.getCitizenData().getName() : c.getName().getString();
            cObj.addProperty("name", name);
            cObj.addProperty("distance_blocks", (int) citizen.distanceTo(c));
            if (c.getCitizenData() != null && c.getCitizenData().getJob() != null) {
                String job = net.minecraft.network.chat.Component.translatable(
                        c.getCitizenData().getJob().getJobRegistryEntry().getTranslationKey()
                ).getString();
                cObj.addProperty("job", job);
            }
            citizens.add(cObj);
        });
        result.add("nearby_citizens", citizens);

        // Animals (summarized by type to keep the response compact)
        JsonObject animalCounts = new JsonObject();
        level.getEntitiesOfClass(Animal.class, scanBox).forEach(a -> {
            String type = a.getType().getDescriptionId();
            // strip "entity.minecraft." prefix for readability
            int dot = type.lastIndexOf('.');
            String shortType = dot >= 0 ? type.substring(dot + 1) : type;
            animalCounts.addProperty(shortType,
                    (animalCounts.has(shortType) ? animalCounts.get(shortType).getAsInt() : 0) + 1);
        });
        result.add("nearby_animals", animalCounts);

        // Hostile mobs
        int hostileCount = level.getEntitiesOfClass(Monster.class, scanBox).size();
        result.addProperty("nearby_hostile_mobs", hostileCount);
        if (hostileCount > 0) {
            result.addProperty("hostile_mobs_warning", "There are hostile creatures nearby!");
        }

        // ── Colony buildings in range ────────────────────────────────────────
        JsonArray buildings = new JsonArray();
        if (colony != null) {
            colony.getServerBuildingManager().getBuildings().values().forEach(building -> {
                BlockPos bPos = building.getPosition();
                double dist = Math.sqrt(pos.distSqr(bPos));
                if (dist <= SCAN_RADIUS * 1.5) { // slightly wider for buildings since they are large
                    JsonObject bObj = new JsonObject();
                    bObj.addProperty("type", building.getBuildingType().getRegistryName().getPath());
                    bObj.addProperty("level", building.getBuildingLevel());
                    bObj.addProperty("distance_blocks", (int) dist);
                    buildings.add(bObj);
                }
            });
        }
        result.add("visible_colony_buildings", buildings);

        // ── Citizen's own position hint ──────────────────────────────────────
        result.addProperty("your_position", pos.toShortString());
        result.addProperty("dimension", level.dimension().location().toString());

        return result;
    }
}
