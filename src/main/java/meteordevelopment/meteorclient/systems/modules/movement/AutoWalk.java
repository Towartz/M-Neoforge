package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.NopPathManager;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;

public class AutoWalk extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<AutoWalk.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                     .description("Walking mode."))
                  .defaultValue(AutoWalk.Mode.Smart))
               .onChanged(mode1 -> {
                  if (this.isActive()) {
                     if (mode1 == AutoWalk.Mode.Simple) {
                        PathManagers.get().stop();
                     } else {
                        this.createGoal();
                     }

                     this.unpress();
                  }
               }))
            .build()
      );
   private final Setting<AutoWalk.Direction> direction = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder()
                           .name("simple-direction"))
                        .description("The direction to walk in simple mode."))
                     .defaultValue(AutoWalk.Direction.Forwards))
                  .onChanged(direction1 -> {
                     if (this.isActive()) {
                        this.unpress();
                     }
                  }))
               .visible(() -> this.mode.get() == AutoWalk.Mode.Simple))
            .build()
      );

   public AutoWalk() {
      super(Categories.Movement, "auto-walk", "Automatically walks forward.");
   }

   @Override
   public void onActivate() {
      if (this.mode.get() == AutoWalk.Mode.Smart) {
         this.createGoal();
      }
   }

   @Override
   public void onDeactivate() {
      if (this.mode.get() == AutoWalk.Mode.Simple) {
         this.unpress();
      } else {
         PathManagers.get().stop();
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onTick(TickEvent.Pre event) {
      if (this.mode.get() == AutoWalk.Mode.Simple) {
         switch ((AutoWalk.Direction)this.direction.get()) {
            case Forwards:
               this.setPressed(this.mc.options.keyUp, true);
               break;
            case Backwards:
               this.setPressed(this.mc.options.keyDown, true);
               break;
            case Left:
               this.setPressed(this.mc.options.keyLeft, true);
               break;
            case Right:
               this.setPressed(this.mc.options.keyRight, true);
         }
      } else if (PathManagers.get() instanceof NopPathManager) {
         this.info("Smart mode requires Baritone", new Object[0]);
         this.toggle();
      }
   }

   private void unpress() {
      this.setPressed(this.mc.options.keyUp, false);
      this.setPressed(this.mc.options.keyDown, false);
      this.setPressed(this.mc.options.keyLeft, false);
      this.setPressed(this.mc.options.keyRight, false);
   }

   private void setPressed(KeyMapping key, boolean pressed) {
      key.setDown(pressed);
      Input.setKeyState(key, pressed);
   }

   private void createGoal() {
      PathManagers.get().moveInDirection(this.mc.player.getYRot());
   }

   public static enum Direction {
      Forwards,
      Backwards,
      Left,
      Right;
   }

   public static enum Mode {
      Simple,
      Smart;
   }
}
