package me.sshcrack.mc_talking.client;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.blaze3d.platform.InputConstants;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.network.TalkToCitizenPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
/*? if forge {*/
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.sshcrack.mc_talking.McTalking;
*//*?}*/
/*? if neoforge {*/
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
/*?}*/

/**
 * Optional client-only "talk to citizen" keybind (unbound by default). Talks to (or ends a
 * conversation with) whichever citizen is under the crosshair within the configured max
 * conversation distance.
 *
 * <p>This class only ever runs on the physical client: on Forge it is discovered via the
 * {@code Dist.CLIENT}-scoped {@code @Mod.EventBusSubscriber}, and on NeoForge it is only ever
 * referenced from {@code McTalkingClient}, itself a {@code Dist.CLIENT}-only mod entry point.
 * The server never trusts the entity this class picks; it is re-resolved and re-validated in
 * {@link TalkToCitizenPayload#handleOnServer}.</p>
 */
/*? if forge {*/
/*@Mod.EventBusSubscriber(modid = McTalking.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*//*?}*/
public final class TalkKeybinds {
    private TalkKeybinds() {
    }

    private static final String CATEGORY = "key.categories.mc_talking";

    public static final KeyMapping TALK_TO_CITIZEN = new KeyMapping(
            "key.mc_talking.talk_to_citizen",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    /*? if forge {*/
    /*@SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TALK_TO_CITIZEN);
    }
    *//*?}*/
    /*? if neoforge {*/
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TALK_TO_CITIZEN);
    }
    /*?}*/

    /** Called once per client tick; consumes any queued key presses. */
    public static void tick() {
        while (TALK_TO_CITIZEN.consumeClick()) {
            attemptTalk();
        }
    }

    private static void attemptTalk() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        double reach = McTalkingConfig.INSTANCE.instance().maxConversationDistance;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, searchBox,
                e -> e instanceof AbstractEntityCitizen, reach * reach);
        if (hit == null || !(hit.getEntity() instanceof AbstractEntityCitizen citizen)) {
            return;
        }

        TalkToCitizenPayload.send(citizen.getId());
    }
}
