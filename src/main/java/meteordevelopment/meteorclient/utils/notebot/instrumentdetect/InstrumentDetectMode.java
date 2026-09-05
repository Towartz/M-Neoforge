package meteordevelopment.meteorclient.utils.notebot.instrumentdetect;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public enum InstrumentDetectMode {
   BlockState((noteBlock, blockPos) -> (NoteBlockInstrument)noteBlock.getValue(NoteBlock.INSTRUMENT)),
   BelowBlock((noteBlock, blockPos) -> Minecraft.getInstance().level.getBlockState(blockPos.below()).instrument());

   private final InstrumentDetectFunction instrumentDetectFunction;

   private InstrumentDetectMode(InstrumentDetectFunction instrumentDetectFunction) {
      this.instrumentDetectFunction = instrumentDetectFunction;
   }

   public InstrumentDetectFunction getInstrumentDetectFunction() {
      return this.instrumentDetectFunction;
   }
}
