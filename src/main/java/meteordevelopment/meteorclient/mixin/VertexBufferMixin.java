package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import java.nio.ByteBuffer;
import meteordevelopment.meteorclient.renderer.GL;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VertexBuffer.class})
public abstract class VertexBufferMixin {
   @Shadow
   private int indexBufferId;

   @Inject(
      method = {
         "uploadIndexBuffer(Lcom/mojang/blaze3d/vertex/MeshData$DrawState;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/systems/RenderSystem$AutoStorageIndexBuffer;",
         "uploadIndexBuffer"
      },
      at = {@At("RETURN")},
      require = 0
   )
   private void onConfigureIndexBuffer(DrawState parameters, ByteBuffer indexBuffer, CallbackInfoReturnable<AutoStorageIndexBuffer> info) {
      if (info.getReturnValue() == null) {
         GL.CURRENT_IBO = this.indexBufferId;
      } else {
         GL.CURRENT_IBO = ((ShapeIndexBufferAccessor)(Object)info.getReturnValue()).getId();
      }
   }
}
