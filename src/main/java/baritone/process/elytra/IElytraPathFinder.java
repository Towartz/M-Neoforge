package baritone.process.elytra;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;

public interface IElytraPathFinder {
   CompletableFuture<UnpackedSegment> pathFindAsync(BlockPos var1, BlockPos var2);
}
