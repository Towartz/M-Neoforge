package meteordevelopment.meteorclient.events.render;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.events.Cancellable;
import net.minecraft.client.renderer.block.model.ItemTransform;

public class ApplyTransformationEvent extends Cancellable {
   private static final ApplyTransformationEvent INSTANCE = new ApplyTransformationEvent();
   public ItemTransform transformation;
   public boolean leftHanded;
   public PoseStack matrices;

   public static ApplyTransformationEvent get(ItemTransform transformation, boolean leftHanded, PoseStack matrices) {
      INSTANCE.setCancelled(false);
      INSTANCE.transformation = transformation;
      INSTANCE.leftHanded = leftHanded;
      INSTANCE.matrices = matrices;
      return INSTANCE;
   }
}
