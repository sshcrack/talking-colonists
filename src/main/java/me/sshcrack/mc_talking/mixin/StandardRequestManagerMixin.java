package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.manager.StandardRequestManager;
import me.sshcrack.mc_talking.pregen.DeliveryInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StandardRequestManager.class, remap = false)
public class StandardRequestManagerMixin {

    @Shadow
    public IColony getColony() {
        throw new AssertionError();
    }

    @Inject(method = "updateRequestState", at = @At("HEAD"))
    private void mc_talking$onUpdateRequestState(IToken<?> token, RequestState state, CallbackInfo ci) {
        IColony colony;
        try {
            colony = getColony();
        } catch (Exception e) {
            return;
        }
        if (colony == null) return;

        var reqManager = colony.getRequestManager();
        if (reqManager == null) return;

        IRequest<?> request = reqManager.getRequestForToken(token);
        if (request == null) return;
        if (!(request.getRequest() instanceof Delivery)) return;

        if (state == RequestState.IN_PROGRESS) {
            DeliveryInteractionManager.trackDelivery(colony, request, token);
        } else if (state == RequestState.RESOLVED) {
            DeliveryInteractionManager.onDeliveryResolved(colony, request, token);
        } else if (state == RequestState.FAILED || state == RequestState.CANCELLED) {
            DeliveryInteractionManager.onDeliveryCancelled(token);
        }
    }
}
