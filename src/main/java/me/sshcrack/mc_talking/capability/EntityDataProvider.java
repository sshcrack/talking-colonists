package me.sshcrack.mc_talking.capability;

import me.sshcrack.mc_talking.McTalking;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Capability for storing session data for entities
 */
public class EntityDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static final ResourceLocation IDENTIFIER = new ResourceLocation(McTalking.MODID, "entity_data");
    
    public static final Capability<EntityDataProvider> ENTITY_DATA_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});
    
    private String sessionToken = "";
    private final LazyOptional<EntityDataProvider> holder = LazyOptional.of(() -> this);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ENTITY_DATA_CAPABILITY) {
            return holder.cast();
        }
        return LazyOptional.empty();
    }

    /**
     * Get the session token for the entity
     * @return The session token, or empty string if not set
     */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Set the session token for the entity
     * @param token The session token to set
     */
    public void setSessionToken(String token) {
        this.sessionToken = token;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("SessionToken", sessionToken);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        sessionToken = nbt.getString("SessionToken");
    }

    /**
     * Helper method to get entity data from an entity
     * @param entity The entity to get data from
     * @return The entity data provider
     */
    public static LazyOptional<EntityDataProvider> getFromEntity(Entity entity) {
        if (entity == null) {
            return LazyOptional.empty();
        }
        return entity.getCapability(ENTITY_DATA_CAPABILITY);
    }
}
