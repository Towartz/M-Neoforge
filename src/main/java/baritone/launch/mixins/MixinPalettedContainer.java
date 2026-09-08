package baritone.launch.mixins;

import baritone.utils.accessor.IPalettedContainer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({PalettedContainer.class})
public abstract class MixinPalettedContainer<T> implements IPalettedContainer<T> {
   private static final MethodHandle DATA_GETTER;

   @Override
   public Palette<T> getPalette() {
      return this.data().getPalette();
   }

   @Override
   public BitStorage getStorage() {
      return this.data().getStorage();
   }

   @Unique
   private IPalettedContainer.IData<T> data() {
      try {
         return (IPalettedContainer.IData<T>)(Object)DATA_GETTER.invoke((PalettedContainer)(Object)this);
      } catch (Throwable var2) {
         throw sneaky(var2);
      }
   }

   @SuppressWarnings("unchecked")
   private static <E extends Throwable> E sneaky(Throwable t) throws E {
      throw (E) t;
   }

   static {
      Field dataField = null;

      for (Field field : PalettedContainer.class.getDeclaredFields()) {
         Class<?> fieldType = field.getType();
         if (IPalettedContainer.IData.class.isAssignableFrom(fieldType) && (field.getModifiers() & 24) == 0 && !field.isSynthetic()) {
            if (dataField != null) {
               throw new IllegalStateException("PalettedContainer has more than one Data field.");
            }

            dataField = field;
         }
      }

      if (dataField == null) {
         throw new IllegalStateException("PalettedContainer has no Data field.");
      } else {
         MethodHandle rawGetter;
         try {
            rawGetter = MethodHandles.lookup().unreflectGetter(dataField);
         } catch (IllegalAccessException var6) {
            throw new IllegalStateException("PalettedContainer may not access its own field?!", var6);
         }

         MethodType getterType = MethodType.methodType(IPalettedContainer.IData.class, PalettedContainer.class);
         DATA_GETTER = MethodHandles.explicitCastArguments(rawGetter, getterType);
      }
   }
}
