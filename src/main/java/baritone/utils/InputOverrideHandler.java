package baritone.utils;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.input.Input;
import baritone.behavior.Behavior;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.player.KeyboardInput;

public final class InputOverrideHandler extends Behavior implements IInputOverrideHandler {
   private final Map<Input, Boolean> inputForceStateMap = new HashMap<>();
   private final BlockBreakHelper blockBreakHelper;
   private final BlockPlaceHelper blockPlaceHelper;

   public InputOverrideHandler(Baritone baritone) {
      super(baritone);
      this.blockBreakHelper = new BlockBreakHelper(baritone.getPlayerContext());
      this.blockPlaceHelper = new BlockPlaceHelper(baritone.getPlayerContext());
   }

   @Override
   public final boolean isInputForcedDown(Input input) {
      return input == null ? false : this.inputForceStateMap.getOrDefault(input, false);
   }

   @Override
   public final void setInputForceState(Input input, boolean forced) {
      this.inputForceStateMap.put(input, forced);
   }

   @Override
   public final void clearAllKeys() {
      this.inputForceStateMap.clear();
   }

   @Override
   public final void onTick(TickEvent event) {
      if (event.getType() != TickEvent.Type.OUT) {
         if (this.isInputForcedDown(Input.CLICK_LEFT)) {
            this.setInputForceState(Input.CLICK_RIGHT, false);
         }

         this.blockBreakHelper.tick(this.isInputForcedDown(Input.CLICK_LEFT));
         this.blockPlaceHelper.tick(this.isInputForcedDown(Input.CLICK_RIGHT));
         if (this.inControl()) {
            if (this.ctx.player().input.getClass() != PlayerMovementInput.class) {
               this.ctx.player().input = new PlayerMovementInput(this);
            }
         } else if (this.ctx.player().input.getClass() == PlayerMovementInput.class) {
            this.ctx.player().input = new KeyboardInput(this.ctx.minecraft().options);
         }
      }
   }

   private boolean inControl() {
      for (Input input : new Input[]{Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT, Input.SNEAK, Input.JUMP}) {
         if (this.isInputForcedDown(input)) {
            return true;
         }
      }

      return this.baritone.getPathingBehavior().isPathing() || this.baritone != BaritoneAPI.getProvider().getPrimaryBaritone();
   }

   public BlockBreakHelper getBlockBreakHelper() {
      return this.blockBreakHelper;
   }
}
