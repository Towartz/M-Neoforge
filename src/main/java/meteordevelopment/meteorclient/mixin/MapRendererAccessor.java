package meteordevelopment.meteorclient.mixin;

import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.gui.MapRenderer.MapInstance;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({MapRenderer.class})
public interface MapRendererAccessor {
   @Invoker("getOrCreateMapInstance")
   MapInstance invokeGetMapTexture(MapId var1, MapItemSavedData var2);
}
