package baritone.launch.mixins;

import baritone.utils.accessor.IChunkArray;
import baritone.utils.accessor.IClientChunkProvider;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({ClientChunkCache.class})
public class MixinClientChunkProvider implements IClientChunkProvider {
   @Final
   @Shadow
   ClientLevel level;

   @Override
   public ClientChunkCache createThreadSafeCopy() {
      IChunkArray arr = this.extractReferenceArray();
      ClientChunkCache result = new ClientChunkCache(this.level, arr.viewDistance() - 3);
      IChunkArray copyArr = ((IClientChunkProvider)result).extractReferenceArray();
      copyArr.copyFrom(arr);
      if (copyArr.viewDistance() != arr.viewDistance()) {
         throw new IllegalStateException(copyArr.viewDistance() + " " + arr.viewDistance());
      } else {
         return result;
      }
   }

   @Override
   public IChunkArray extractReferenceArray() {
      for (Field f : ClientChunkCache.class.getDeclaredFields()) {
         if (IChunkArray.class.isAssignableFrom(f.getType())) {
            try {
               return (IChunkArray)f.get(this);
            } catch (IllegalAccessException var6) {
               throw new RuntimeException(var6);
            }
         }
      }

      throw new RuntimeException(Arrays.toString((Object[])ClientChunkCache.class.getDeclaredFields()));
   }
}
