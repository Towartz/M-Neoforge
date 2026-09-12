package meteordevelopment.meteorclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({MultiPlayerGameMode.class})
public interface ClientPlayerInteractionManagerAccessor {
   @Accessor("destroyProgress")
   float getBreakingProgress();

   @Accessor("destroyProgress")
   void setCurrentBreakingProgress(float var1);

   @Accessor("destroyBlockPos")
   BlockPos getCurrentBreakingBlockPos();

   @Invoker("startPrediction")
   void invokeStartPrediction(ClientLevel level, PredictiveAction action);
}
