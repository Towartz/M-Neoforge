package meteordevelopment.meteorclient.systems.modules.movement.plus;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.LivingEntityAccessor;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super(Categories.PlusMovement, "no-jump-delay", "Removes the vanilla jump cooldown allowing continuous jumping.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player != null) {
            ((LivingEntityAccessor) this.mc.player).setJumpCooldown(0);
        }
    }
}
