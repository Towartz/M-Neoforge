package net.caffeinemc.mods.sodium.client.model.color;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos;
public interface ColorProvider<T> {
    void getColor(LevelSlice slice, BlockPos pos, BlockPos.MutableBlockPos scratch, T state, ModelQuadView quad, int[] output);
}
