package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.core.colony.requestsystem.requests.AbstractRequest;
import me.sshcrack.mc_talking.support.PlayerFulfillmentHandler;
import me.sshcrack.mc_talking.pregen.DeliveryInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// NOTICE: This assumes AbstractRequest is the only concrete IRequest.setState() implementation.
//         If other implementations are added, extend this mixin to cover them.
@Mixin(value = AbstractRequest.class, remap = false)
public abstract class AbstractRequestMixin {

    @Inject(method = "setState", at = @At("HEAD"))
    private void mc_talking$onSetState(IRequestManager manager, RequestState state, CallbackInfo ci) {
        IRequest<?> request = (IRequest<?>) this;

        if (request.getRequest() instanceof Delivery) {
            IColony colony = manager.getColony();
            if (state == RequestState.IN_PROGRESS) {
                DeliveryInteractionManager.trackDelivery(colony, request, request.getId());
            } else if (state == RequestState.RESOLVED) {
                DeliveryInteractionManager.onDeliveryResolved(colony, request, request.getId());
            } else if (state == RequestState.FAILED || state == RequestState.CANCELLED) {
                DeliveryInteractionManager.onDeliveryCancelled(request.getId());
            }
        }

        if (state == RequestState.OVERRULED) {
            PlayerFulfillmentHandler.onRequestOverruled(manager, request);
        }
    }
}
