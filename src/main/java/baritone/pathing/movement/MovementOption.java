package baritone.pathing.movement;

import baritone.api.utils.input.Input;
import java.util.stream.Stream;
import net.minecraft.util.Mth;

public record MovementOption(Input input1, Input input2, float motionX, float motionZ) {
   private static final float SPRINT_MULTIPLIER = 1.3F;

   public MovementOption(Input input1, float motionX, float motionZ) {
      this(input1, null, motionX, motionZ);
   }

   public void setInputs(MovementState movementState) {
      if (this.input1 != null) {
         movementState.setInput(this.input1, true);
      }

      if (this.input2 != null) {
         movementState.setInput(this.input2, true);
      }
   }

   public float distanceToSq(float otherX, float otherZ) {
      return Mth.abs(this.motionX() - otherX) + Mth.abs(this.motionZ() - otherZ);
   }

   public static Stream<MovementOption> getOptions(float motionX, float motionZ, boolean canSprint) {
      return Stream.of(
         new MovementOption(Input.MOVE_FORWARD, canSprint ? motionX * 1.3F : motionX, canSprint ? motionZ * 1.3F : motionZ),
         new MovementOption(Input.MOVE_BACK, -motionX, -motionZ),
         new MovementOption(Input.MOVE_LEFT, -motionZ, motionX),
         new MovementOption(Input.MOVE_RIGHT, motionZ, -motionX),
         new MovementOption(
            Input.MOVE_FORWARD, Input.MOVE_LEFT, (canSprint ? motionX * 1.3F : motionX) - motionZ, (canSprint ? motionZ * 1.3F : motionZ) + motionX
         ),
         new MovementOption(
            Input.MOVE_FORWARD, Input.MOVE_RIGHT, (canSprint ? motionX * 1.3F : motionX) + motionZ, (canSprint ? motionZ * 1.3F : motionZ) - motionX
         ),
         new MovementOption(Input.MOVE_BACK, Input.MOVE_LEFT, -motionX - motionZ, -motionZ + motionX),
         new MovementOption(Input.MOVE_BACK, Input.MOVE_RIGHT, -motionX + motionZ, -motionZ - motionX)
      );
   }
}
