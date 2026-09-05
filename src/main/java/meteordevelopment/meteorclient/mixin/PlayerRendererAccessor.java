package meteordevelopment.meteorclient.mixin;

import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerRenderer.class)
public interface PlayerRendererAccessor {
    @Invoker("getArmPose")
    static ArmPose meteor$getArmPose(AbstractClientPlayer player, InteractionHand hand) {
        throw new AssertionError();
    }
}
