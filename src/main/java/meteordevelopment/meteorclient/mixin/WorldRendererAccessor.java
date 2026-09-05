package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LevelRenderer.class})
public interface WorldRendererAccessor {
   @Accessor("entityTarget")
   void setEntityOutlinesFramebuffer(RenderTarget var1);

   @Accessor("destroyingBlocks")
   Int2ObjectMap<BlockDestructionProgress> getBlockBreakingInfos();
}
