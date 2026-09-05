package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;

public class AntiVoid extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<AntiVoid.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                     .description("The method to prevent you from falling into the void."))
                  .defaultValue(AntiVoid.Mode.Jump))
               .onChanged(a -> this.onActivate()))
            .build()
      );
   private boolean wasFlightEnabled;
   private boolean hasRun;

   public AntiVoid() {
      super(Categories.Movement, "anti-void", "Attempts to prevent you from falling into the void.");
   }

   @Override
   public void onActivate() {
      if (this.mode.get() == AntiVoid.Mode.Flight) {
         this.wasFlightEnabled = Modules.get().isActive(Flight.class);
      }
   }

   @Override
   public void onDeactivate() {
      if (!this.wasFlightEnabled && this.mode.get() == AntiVoid.Mode.Flight && Utils.canUpdate() && Modules.get().isActive(Flight.class)) {
         Modules.get().get(Flight.class).toggle();
      }
   }

   @EventHandler
   private void onPreTick(TickEvent.Pre event) {
      int minY = this.mc.level.getMinBuildHeight();
      if (!(this.mc.player.getY() > (double)minY) && !(this.mc.player.getY() < (double)(minY - 15))) {
         switch ((AntiVoid.Mode)this.mode.get()) {
            case Flight:
               if (!Modules.get().isActive(Flight.class)) {
                  Modules.get().get(Flight.class).toggle();
               }

               this.hasRun = true;
               break;
            case Jump:
               this.mc.player.jumpFromGround();
         }
      } else {
         if (this.hasRun && this.mode.get() == AntiVoid.Mode.Flight && Modules.get().isActive(Flight.class)) {
            Modules.get().get(Flight.class).toggle();
            this.hasRun = false;
         }
      }
   }

   public static enum Mode {
      Flight,
      Jump;
   }
}
