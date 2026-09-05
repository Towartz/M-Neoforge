package meteordevelopment.meteorclient.systems.modules.movement;

import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerTickMovementEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.gui.GuiKeyEvents;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.mixin.CreativeInventoryScreenAccessor;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTabs;

public class GUIMove extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<GUIMove.Screens> screens = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("guis")).description("Which GUIs to move in."))
               .defaultValue(GUIMove.Screens.Both))
            .build()
      );
   public final Setting<Boolean> jump = this.sgGeneral
      .add(new BoolSetting.Builder().name("jump").description("Allows you to jump while in GUIs.").defaultValue(Boolean.valueOf(true)).onChanged(aBoolean -> {
         if (this.isActive() && !aBoolean) {
            this.set(this.mc.options.keyJump, false);
         }
      }).build());
   public final Setting<Boolean> sneak = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("sneak").description("Allows you to sneak while in GUIs.").defaultValue(Boolean.valueOf(true)).onChanged(aBoolean -> {
            if (this.isActive() && !aBoolean) {
               this.set(this.mc.options.keyShift, false);
            }
         }).build()
      );
   public final Setting<Boolean> sprint = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("sprint")
            .description("Allows you to sprint while in GUIs.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(aBoolean -> {
               if (this.isActive() && !aBoolean) {
                  this.set(this.mc.options.keySprint, false);
               }
            })
            .build()
      );
   private final Setting<Boolean> arrowsRotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("arrows-rotate")
            .description("Allows you to use your arrow keys to rotate while in GUIs.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Double> rotateSpeed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("rotate-speed").description("Rotation speed while in GUIs.").defaultValue(4.0).min(0.0).build());

   public GUIMove() {
      super(Categories.Movement, "gui-move", "Allows you to perform various actions while in GUIs.");
   }

   @Override
   public void onDeactivate() {
      this.set(this.mc.options.keyUp, false);
      this.set(this.mc.options.keyDown, false);
      this.set(this.mc.options.keyLeft, false);
      this.set(this.mc.options.keyRight, false);
      if (this.jump.get()) {
         this.set(this.mc.options.keyJump, false);
      }

      if (this.sneak.get()) {
         this.set(this.mc.options.keyShift, false);
      }

      if (this.sprint.get()) {
         this.set(this.mc.options.keySprint, false);
      }
   }

   public boolean disableSpace() {
      return this.isActive() && this.jump.get() && this.mc.options.keyJump.isDefault();
   }

   public boolean disableArrows() {
      return this.isActive() && this.arrowsRotate.get();
   }

   public boolean isScreenValid() {
      if (this.mc == null || this.mc.screen == null) {
         return false;
      }
      Screens mode = this.screens.get();
      boolean isWidget = this.mc.screen instanceof WidgetScreen;
      if (mode == Screens.Both) {
         return true;
      }
      if (mode == Screens.GUI) {
         return isWidget;
      }
      if (mode == Screens.Inventory) {
         return !isWidget;
      }
      return false;
   }

   @EventHandler
   private void onPlayerMoveEvent(PlayerTickMovementEvent event) {
      if (!this.skip() && this.isScreenValid()) {
         this.set(this.mc.options.keyUp, Input.isPressed(this.mc.options.keyUp));
         this.set(this.mc.options.keyDown, Input.isPressed(this.mc.options.keyDown));
         this.set(this.mc.options.keyLeft, Input.isPressed(this.mc.options.keyLeft));
         this.set(this.mc.options.keyRight, Input.isPressed(this.mc.options.keyRight));
         if (this.jump.get()) {
            this.set(this.mc.options.keyJump, Input.isPressed(this.mc.options.keyJump));
         }

         if (this.sneak.get()) {
            this.set(this.mc.options.keyShift, Input.isPressed(this.mc.options.keyShift));
         }

         if (this.sprint.get()) {
            this.set(this.mc.options.keySprint, Input.isPressed(this.mc.options.keySprint));
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!this.skip() && this.isScreenValid()) {
         float rotationDelta = Math.min((float)(this.rotateSpeed.get() * event.frameTime * 20.0), 100.0F);
         if (this.arrowsRotate.get()) {
            float yaw = this.mc.player.getYRot();
            float pitch = this.mc.player.getXRot();
            if (Input.isKeyPressed(263)) {
               yaw -= rotationDelta;
            }

            if (Input.isKeyPressed(262)) {
               yaw += rotationDelta;
            }

            if (Input.isKeyPressed(265)) {
               pitch -= rotationDelta;
            }

            if (Input.isKeyPressed(264)) {
               pitch += rotationDelta;
            }

            pitch = Mth.clamp(pitch, -90.0F, 90.0F);
            this.mc.player.setYRot(yaw);
            this.mc.player.setXRot(pitch);
         }
      }
   }

   private void set(KeyMapping bind, boolean pressed) {
      boolean wasPressed = bind.isDown();
      bind.setDown(pressed);
      Key key = ((KeyBindingAccessor)bind).getKey();
      if (wasPressed != pressed && key.getType() == Type.KEYSYM) {
         MeteorClient.EVENT_BUS.post(KeyEvent.get(key.getValue(), 0, pressed ? KeyAction.Press : KeyAction.Release));
      }
   }

   public boolean skip() {
      if (this.mc == null || this.mc.screen == null) {
         return true;
      }
      if (this.mc.screen instanceof ChatScreen
         || this.mc.screen instanceof SignEditScreen
         || this.mc.screen instanceof AnvilScreen
         || this.mc.screen instanceof AbstractCommandBlockEditScreen
         || this.mc.screen instanceof StructureBlockEditScreen) {
         return true;
      }
      if (this.mc.screen instanceof CreativeModeInventoryScreen && CreativeInventoryScreenAccessor.getSelectedTab() == CreativeModeTabs.searchTab()) {
         return true;
      }
      if (this.mc.screen instanceof WidgetScreen && !GuiKeyEvents.canUseKeys) {
         return true;
      }
      if (this.mc.screen.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
         return true;
      }
      return false;
   }

   public static enum Screens {
      GUI,
      Inventory,
      Both;
   }
}
