package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.entity.DamageEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class Freecam extends Module {
   public enum FlightMode {
      Horizontal("Horizontal"),
      ThreeDimensional("3D");

      private final String title;

      FlightMode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public enum ControlMode {
      Camera("Camera"),
      Player("Player");

      private final String title;

      ControlMode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgMovement = this.settings.createGroup("Movement");
   private final SettingGroup sgVisual = this.settings.createGroup("Visual");
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");

   // General
   private final Setting<Double> speed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description("Your speed while in freecam.")
            .onChanged(aDouble -> this.speedValue = aDouble)
            .defaultValue(1.0)
            .min(0.0)
            .build()
      );
   private final Setting<Double> speedScrollSensitivity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed-scroll-sensitivity")
            .description("Allows you to change speed value using scroll wheel. 0 to disable.")
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(2.0)
            .build()
      );
   public final Setting<Freecam.ControlMode> controlMode = this.sgGeneral
      .add(
         new EnumSetting.Builder<Freecam.ControlMode>()
            .name("control-mode")
            .description("Controls whether inputs move the freecam camera or the real player.")
            .defaultValue(Freecam.ControlMode.Camera)
            .onChanged(mode -> {
               if (this.isActive()) this.unpress();
            })
            .build()
      );
   public final Setting<Keybind> toggleControlMode = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("toggle-control-mode")
            .description("Keybind to quickly toggle between Camera and Player control.")
            .defaultValue(Keybind.none())
            .build()
      );
   private final Setting<Boolean> toggleOnDamage = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("toggle-on-damage").description("Disables freecam when you take damage.").defaultValue(Boolean.valueOf(false)).build()
      );
   private final Setting<Boolean> toggleOnDeath = this.sgGeneral
      .add(new BoolSetting.Builder().name("toggle-on-death").description("Disables freecam when you die.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> toggleOnLog = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("toggle-on-log")
            .description("Disables freecam when you disconnect from a server.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public final Setting<Boolean> teleportOnDisable = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("teleport-on-disable")
            .description("Teleports the player to the camera position when disabling freecam.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );

   // Movement
   public final Setting<Freecam.FlightMode> flightMode = this.sgMovement
      .add(
         new EnumSetting.Builder<Freecam.FlightMode>()
            .name("flight-mode")
            .description("How the camera moves forward.")
            .defaultValue(Freecam.FlightMode.ThreeDimensional)
            .build()
      );
   public final Setting<Double> verticalSpeed = this.sgMovement
      .add(
         new DoubleSetting.Builder()
            .name("vertical-speed")
            .description("Vertical speed when ascending/descending with Space or Shift.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderMax(5.0)
            .build()
      );
   public final Setting<Double> sprintMultiplier = this.sgMovement
      .add(
         new DoubleSetting.Builder()
            .name("sprint-multiplier")
            .description("Speed multiplier applied when holding sprint.")
            .defaultValue(2.0)
            .min(1.0)
            .sliderMax(10.0)
            .build()
      );

   // Visual
   public final Setting<Boolean> fullbright = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("auto-fullbright")
            .description("Automatically lights up caves and unlit spaces while in freecam.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(v -> {
               if (this.isActive() && this.mc.levelRenderer != null) {
                  this.mc.levelRenderer.allChanged();
               }
            })
            .build()
      );
   public final Setting<Boolean> renderTracer = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("render-tracer")
            .description("Draws a tracer line from the freecam camera back to your real player character.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public final Setting<SettingColor> tracerColor = this.sgVisual
      .add(
         new ColorSetting.Builder()
            .name("tracer-color")
            .description("The color of the line pointing to your player character.")
            .defaultValue(new SettingColor(245, 50, 50, 200))
            .visible(this.renderTracer::get)
            .build()
      );
   public final Setting<Boolean> renderBox = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("render-box")
            .description("Draws an ESP bounding box around your player character.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public final Setting<SettingColor> boxColor = this.sgVisual
      .add(
         new ColorSetting.Builder()
            .name("box-color")
            .description("The fill color of your player's bounding box.")
            .defaultValue(new SettingColor(245, 50, 50, 45))
            .visible(this.renderBox::get)
            .build()
      );
   public final Setting<SettingColor> boxOutlineColor = this.sgVisual
      .add(
         new ColorSetting.Builder()
            .name("box-outline-color")
            .description("The outline color of your player's bounding box.")
            .defaultValue(new SettingColor(245, 50, 50, 220))
            .visible(this.renderBox::get)
            .build()
      );
   private final Setting<Boolean> reloadChunks = this.sgVisual
      .add(new BoolSetting.Builder().name("reload-chunks").description("Disables cave culling.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> renderHands = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("show-hands")
            .description("Whether or not to render your hands in freecam.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Boolean> staticView = this.sgVisual
      .add(new BoolSetting.Builder().name("static").description("Disables settings that move the view.").defaultValue(Boolean.valueOf(true)).build());

   // Safety
   public final Setting<Boolean> allowInteract = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("allow-interact")
            .description("Allows interacting with blocks/entities through the camera. Keep disabled to prevent ghost breaks and reach bans.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("rotate-player")
            .description("Rotates the real player to face what the camera crosshair is looking at.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   public final Vector3d pos = new Vector3d();
   public final Vector3d prevPos = new Vector3d();
   private CameraType perspective;
   private double speedValue;
   public float yaw;
   public float pitch;
   public float prevYaw;
   public float prevPitch;
   private double fovScale;
   private boolean bobView;
   private boolean forward;
   private boolean backward;
   private boolean right;
   private boolean left;
   private boolean up;
   private boolean down;

   public Freecam() {
      super(Categories.Render, "freecam", "Allows the camera to move away from the player.");
   }

   @Override
   public void onActivate() {
      this.fovScale = (Double)this.mc.options.fovEffectScale().get();
      this.bobView = (Boolean)this.mc.options.bobView().get();
      if (this.staticView.get()) {
         this.mc.options.fovEffectScale().set(0.0);
         this.mc.options.bobView().set(false);
      }

      this.yaw = this.mc.player.getYRot();
      this.pitch = this.mc.player.getXRot();
      this.perspective = this.mc.options.getCameraType();
      this.speedValue = this.speed.get();
      Utils.set(this.pos, this.mc.gameRenderer.getMainCamera().getPosition());
      Utils.set(this.prevPos, this.mc.gameRenderer.getMainCamera().getPosition());
      if (this.mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
         this.yaw += 180.0F;
         this.pitch *= -1.0F;
      }

      this.prevYaw = this.yaw;
      this.prevPitch = this.pitch;
      this.forward = this.mc.options.keyUp.isDown();
      this.backward = this.mc.options.keyDown.isDown();
      this.right = this.mc.options.keyRight.isDown();
      this.left = this.mc.options.keyLeft.isDown();
      this.up = this.mc.options.keyJump.isDown();
      this.down = this.mc.options.keyShift.isDown();
      this.unpress();
      if (this.reloadChunks.get() || this.fullbright.get()) {
         this.mc.levelRenderer.allChanged();
      }
   }

   @Override
   public void onDeactivate() {
      if (this.reloadChunks.get() || this.fullbright.get()) {
         this.mc.levelRenderer.allChanged();
      }

      this.mc.options.setCameraType(this.perspective);
      if (this.staticView.get()) {
         this.mc.options.fovEffectScale().set(this.fovScale);
         this.mc.options.bobView().set(this.bobView);
      }

      if (this.teleportOnDisable.get() && this.mc.player != null) {
         this.mc.player.setPos(this.pos.x, this.pos.y, this.pos.z);
         this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
      }
   }

   @EventHandler
   private void onOpenScreen(OpenScreenEvent event) {
      this.unpress();
      this.prevPos.set(this.pos);
      this.prevYaw = this.yaw;
      this.prevPitch = this.pitch;
   }

   private void unpress() {
      this.forward = false;
      this.backward = false;
      this.right = false;
      this.left = false;
      this.up = false;
      this.down = false;
      this.mc.options.keyUp.setDown(false);
      this.mc.options.keyDown.setDown(false);
      this.mc.options.keyRight.setDown(false);
      this.mc.options.keyLeft.setDown(false);
      this.mc.options.keyJump.setDown(false);
      this.mc.options.keyShift.setDown(false);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.cameraEntity.isInWall()) {
         this.mc.getCameraEntity().noPhysics = true;
      }

      if (!this.perspective.isFirstPerson()) {
         this.mc.options.setCameraType(CameraType.FIRST_PERSON);
      }

      this.prevYaw = this.yaw;
      this.prevPitch = this.pitch;

      if (this.controlMode.get() == ControlMode.Player) {
         this.prevPos.set(this.pos);
         return;
      }

      Vec3 forward;
      if (this.flightMode.get() == FlightMode.ThreeDimensional) {
         forward = Vec3.directionFromRotation(this.pitch, this.yaw);
      } else {
         forward = Vec3.directionFromRotation(0.0F, this.yaw);
      }
      Vec3 right = Vec3.directionFromRotation(0.0F, this.yaw + 90.0F);
      double velX = 0.0;
      double velY = 0.0;
      double velZ = 0.0;
      if (this.rotate.get() && this.mc.hitResult != null) {
         if (this.mc.hitResult instanceof EntityHitResult entityHit) {
            BlockPos crossHairPos = entityHit.getEntity().blockPosition();
            Rotations.rotate(Rotations.getYaw(crossHairPos), Rotations.getPitch(crossHairPos), 0, null);
         } else if (this.mc.hitResult instanceof BlockHitResult blockHit) {
            BlockPos crossHairPos = blockHit.getBlockPos();
            if (this.mc.level != null && !this.mc.level.getBlockState(crossHairPos).isAir()) {
               Vec3 crossHairPosition = blockHit.getLocation();
               Rotations.rotate(Rotations.getYaw(crossHairPosition), Rotations.getPitch(crossHairPosition), 0, null);
            }
         }
      }

      double s = 0.5;
      if (this.mc.options.keySprint.isDown()) {
         s = 0.5 * this.sprintMultiplier.get();
      }

      boolean a = false;
      if (this.forward) {
         velX += forward.x * s * this.speedValue;
         velY += forward.y * s * this.speedValue;
         velZ += forward.z * s * this.speedValue;
         a = true;
      }

      if (this.backward) {
         velX -= forward.x * s * this.speedValue;
         velY -= forward.y * s * this.speedValue;
         velZ -= forward.z * s * this.speedValue;
         a = true;
      }

      boolean b = false;
      if (this.right) {
         velX += right.x * s * this.speedValue;
         velZ += right.z * s * this.speedValue;
         b = true;
      }

      if (this.left) {
         velX -= right.x * s * this.speedValue;
         velZ -= right.z * s * this.speedValue;
         b = true;
      }

      if (a && b) {
         double diagonal = 1.0 / Math.sqrt(2.0);
         velX *= diagonal;
         velZ *= diagonal;
      }

      double vSpeed = this.verticalSpeed.get() * s;
      if (this.up) {
         velY += vSpeed;
      }

      if (this.down) {
         velY -= vSpeed;
      }

      this.prevPos.set(this.pos);
      this.pos.set(this.pos.x + velX, this.pos.y + velY, this.pos.z + velZ);
   }

   @EventHandler
   public void onKey(KeyEvent event) {
      if (event.action == KeyAction.Press && this.toggleControlMode.get().matches(true, event.key, event.modifiers)) {
         ControlMode newMode = this.controlMode.get() == ControlMode.Camera ? ControlMode.Player : ControlMode.Camera;
         this.controlMode.set(newMode);
         this.unpress();
         this.info("Control mode set to (highlight)%s(default).", newMode.name());
         event.cancel();
         return;
      }

      if (this.controlMode.get() == ControlMode.Player) {
         return;
      }

      if (!Input.isKeyPressed(292)) {
         if (!this.checkGuiMove()) {
            boolean cancel = true;
            if (this.mc.options.keyUp.matches(event.key, 0)) {
               this.forward = event.action != KeyAction.Release;
               this.mc.options.keyUp.setDown(false);
            } else if (this.mc.options.keyDown.matches(event.key, 0)) {
               this.backward = event.action != KeyAction.Release;
               this.mc.options.keyDown.setDown(false);
            } else if (this.mc.options.keyRight.matches(event.key, 0)) {
               this.right = event.action != KeyAction.Release;
               this.mc.options.keyRight.setDown(false);
            } else if (this.mc.options.keyLeft.matches(event.key, 0)) {
               this.left = event.action != KeyAction.Release;
               this.mc.options.keyLeft.setDown(false);
            } else if (this.mc.options.keyJump.matches(event.key, 0)) {
               this.up = event.action != KeyAction.Release;
               this.mc.options.keyJump.setDown(false);
            } else if (this.mc.options.keyShift.matches(event.key, 0)) {
               this.down = event.action != KeyAction.Release;
               this.mc.options.keyShift.setDown(false);
            } else {
               cancel = false;
            }

            if (cancel) {
               event.cancel();
            }
         }
      }
   }

   @EventHandler
   private void onMouseButton(MouseButtonEvent event) {
      if (this.controlMode.get() == ControlMode.Player) {
         return;
      }

      if (!this.checkGuiMove()) {
         boolean cancel = true;
         if (this.mc.options.keyUp.matchesMouse(event.button)) {
            this.forward = event.action != KeyAction.Release;
            this.mc.options.keyUp.setDown(false);
         } else if (this.mc.options.keyDown.matchesMouse(event.button)) {
            this.backward = event.action != KeyAction.Release;
            this.mc.options.keyDown.setDown(false);
         } else if (this.mc.options.keyRight.matchesMouse(event.button)) {
            this.right = event.action != KeyAction.Release;
            this.mc.options.keyRight.setDown(false);
         } else if (this.mc.options.keyLeft.matchesMouse(event.button)) {
            this.left = event.action != KeyAction.Release;
            this.mc.options.keyLeft.setDown(false);
         } else if (this.mc.options.keyJump.matchesMouse(event.button)) {
            this.up = event.action != KeyAction.Release;
            this.mc.options.keyJump.setDown(false);
         } else if (this.mc.options.keyShift.matchesMouse(event.button)) {
            this.down = event.action != KeyAction.Release;
            this.mc.options.keyShift.setDown(false);
         } else {
            cancel = false;
         }

         if (cancel) {
            event.cancel();
         }
      }
   }

   @EventHandler(
      priority = -100
   )
   private void onMouseScroll(MouseScrollEvent event) {
      if (this.speedScrollSensitivity.get() > 0.0 && this.mc.screen == null) {
         this.speedValue = this.speedValue + event.value * 0.25 * this.speedScrollSensitivity.get() * this.speedValue;
         if (this.speedValue < 0.1) {
            this.speedValue = 0.1;
         }

         event.cancel();
      }
   }

   @EventHandler
   private void onChunkOcclusion(ChunkOcclusionEvent event) {
      event.cancel();
   }

   @EventHandler
   private void onDamage(DamageEvent event) {
      if (event.entity.getUUID() != null) {
         if (event.entity.getUUID().equals(this.mc.player.getUUID())) {
            if (this.toggleOnDamage.get()) {
               this.toggle();
               this.info("Toggled off because you took damage.", new Object[0]);
            }
         }
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      if (this.toggleOnLog.get()) {
         this.toggle();
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundPlayerCombatKillPacket packet) {
         Entity entity = this.mc.level.getEntity(packet.playerId());
         if (entity == this.mc.player && this.toggleOnDeath.get()) {
            this.toggle();
            this.info("Toggled off because you died.", new Object[0]);
         }
      }
   }

   private boolean checkGuiMove() {
      if (this.mc.screen == null) return false;
      GUIMove guiMove = Modules.get().get(GUIMove.class);
      return guiMove == null || !guiMove.isActive() || guiMove.skip() || !guiMove.isScreenValid();
   }

   public void changeLookDirection(double deltaX, double deltaY) {
      this.yaw = (float)((double)this.yaw + deltaX);
      this.pitch = (float)((double)this.pitch + deltaY);
      this.pitch = Mth.clamp(this.pitch, -90.0F, 90.0F);
   }

   public boolean renderHands() {
      return !this.isActive() || this.renderHands.get();
   }

   public double getX(float tickDelta) {
      return Mth.lerp((double)tickDelta, this.prevPos.x, this.pos.x);
   }

   public double getY(float tickDelta) {
      return Mth.lerp((double)tickDelta, this.prevPos.y, this.pos.y);
   }

   public double getZ(float tickDelta) {
      return Mth.lerp((double)tickDelta, this.prevPos.z, this.pos.z);
   }

   public double getYaw(float tickDelta) {
      return (double)this.yaw;
   }

   public double getPitch(float tickDelta) {
      return (double)this.pitch;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player == null) return;

      double targetX = Mth.lerp((double)event.tickDelta, this.mc.player.xo, this.mc.player.getX());
      double targetY = Mth.lerp((double)event.tickDelta, this.mc.player.yo, this.mc.player.getY());
      double targetZ = Mth.lerp((double)event.tickDelta, this.mc.player.zo, this.mc.player.getZ());
      double eyeY = targetY + (double)this.mc.player.getEyeHeight(this.mc.player.getPose());

      if (this.renderTracer.get()) {
         double camX = this.getX(event.tickDelta);
         double camY = this.getY(event.tickDelta);
         double camZ = this.getZ(event.tickDelta);
         event.renderer.line(camX, camY, camZ, targetX, eyeY, targetZ, this.tracerColor.get());
      }

      if (this.renderBox.get()) {
         AABB box = this.mc.player.getBoundingBox();
         double dx = targetX - this.mc.player.getX();
         double dy = targetY - this.mc.player.getY();
         double dz = targetZ - this.mc.player.getZ();
         event.renderer.box(
            dx + box.minX, dy + box.minY, dz + box.minZ,
            dx + box.maxX, dy + box.maxY, dz + box.maxZ,
            this.boxColor.get(), this.boxOutlineColor.get(),
            ShapeMode.Both, 0
         );
      }
   }

   @EventHandler
   private void onStartBreakingBlock(StartBreakingBlockEvent event) {
      if (this.controlMode.get() == ControlMode.Camera && !this.allowInteract.get()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onInteractBlock(InteractBlockEvent event) {
      if (this.controlMode.get() == ControlMode.Camera && !this.allowInteract.get()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onAttackEntity(AttackEntityEvent event) {
      if (this.controlMode.get() == ControlMode.Camera && !this.allowInteract.get()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onInteractEntity(InteractEntityEvent event) {
      if (this.controlMode.get() == ControlMode.Camera && !this.allowInteract.get()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onInteractItem(InteractItemEvent event) {
      if (this.controlMode.get() == ControlMode.Camera && !this.allowInteract.get()) {
         event.toReturn = InteractionResult.FAIL;
      }
   }
}
