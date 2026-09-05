package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({BufferBuilder.class})
public interface BufferBuilderAccessor {
   @Accessor("buffer")
   ByteBufferBuilder meteor$getAllocator();

   @Accessor("format")
   VertexFormat getVertexFormat();
}
