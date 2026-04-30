package me.sshcrack.mc_talking.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SynchedEntityData;

public class AiStatusHelper {
    public static final EntityDataAccessor<AiStatus> DATA_AI_STATUS = SynchedEntityData.defineId(AbstractEntityCitizen.class, getAiStatusSerializer());

    private AiStatusHelper() {

    }


    public static EntityDataSerializer<AiStatus> getAiStatusSerializer() {
        /*? if forge {*/
        /*return EntityDataSerializer.simpleEnum(AiStatus.class);
         *//*? }*/

        /*? if neoforge {*/
        return EntityDataSerializer.forValueType(AiStatus.STREAM_CODEC);
        /*?} */
    }

    public static void setAiStatusSynced(AbstractEntityCitizen citizen, AiStatus status) {
        citizen.level().getServer().execute(() -> AiStatusHelper.setAiStatusOnServerThread(citizen, status));
    }

    public static void getAiStatus(AbstractEntityCitizen citizen) {
        citizen.getEntityData().get(DATA_AI_STATUS);
    }

    public static void register() {
        // Just called so this class registers the data accessor and serializer
    }

    public static void setAiStatusOnServerThread(AbstractEntityCitizen citizen, AiStatus status) {
        citizen.getEntityData().set(DATA_AI_STATUS, status);
    }
}
