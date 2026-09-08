package baritone.behavior;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.behavior.ILookBehavior;
import baritone.api.behavior.look.IAimProcessor;
import baritone.api.behavior.look.ITickableAimProcessor;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.behavior.look.ForkableRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;

public final class LookBehavior extends Behavior implements ILookBehavior {
   private LookBehavior.Target target;
   private Rotation serverRotation;
   private Rotation prevRotation;
   private final LookBehavior.AimProcessor processor;
   private final Deque<Float> smoothYawBuffer;
   private final Deque<Float> smoothPitchBuffer;

   public LookBehavior(Baritone baritone) {
      super(baritone);
      this.processor = new LookBehavior.AimProcessor(this, baritone.getPlayerContext());
      this.smoothYawBuffer = new ArrayDeque<>();
      this.smoothPitchBuffer = new ArrayDeque<>();
   }

   @Override
   public void updateTarget(Rotation rotation, boolean blockInteract) {
      this.target = new LookBehavior.Target(rotation, LookBehavior.Target.Mode.resolve(this.ctx, blockInteract), blockInteract);
   }

   @Override
   public IAimProcessor getAimProcessor() {
      return this.processor;
   }

   @Override
   public void onTick(TickEvent event) {
      if (event.getType() == TickEvent.Type.IN) {
         this.processor.tick();
      }
   }

   @Override
   public void onPlayerUpdate(PlayerUpdateEvent event) {
      if (this.target != null) {
         switch (event.getState()) {
            case PRE:
               if (this.target.mode == LookBehavior.Target.Mode.NONE) {
                  return;
               }

               this.prevRotation = new Rotation(this.ctx.player().getYRot(), this.ctx.player().getXRot());
               Rotation actual = this.processor.peekRotation(this.target.rotation);
               this.ctx.player().setYRot(actual.getYaw());
               this.ctx.player().setXRot(actual.getPitch());
               break;
            case POST:
               if (this.prevRotation != null) {
                  this.smoothYawBuffer.addLast(this.target.rotation.getYaw());

                  while (this.smoothYawBuffer.size() > Baritone.settings().smoothLookTicks.value) {
                     this.smoothYawBuffer.removeFirst();
                  }

                  this.smoothPitchBuffer.addLast(this.target.rotation.getPitch());

                  while (this.smoothPitchBuffer.size() > Baritone.settings().smoothLookTicks.value) {
                     this.smoothPitchBuffer.removeFirst();
                  }

                  if (this.target.mode == LookBehavior.Target.Mode.SERVER) {
                     this.ctx.player().setYRot(this.prevRotation.getYaw());
                     this.ctx.player().setXRot(this.prevRotation.getPitch());
                  } else if (!this.target.blockInteract && (this.ctx.player().isFallFlying() ? Baritone.settings().elytraSmoothLook.value : Baritone.settings().smoothLook.value)) {
                     this.ctx
                        .player()
                        .setYRot(
                           (float)this.smoothYawBuffer.stream().mapToDouble(d -> (double)d.floatValue()).average().orElse((double)this.prevRotation.getYaw())
                        );
                     if (this.ctx.player().isFallFlying()) {
                        this.ctx
                           .player()
                           .setXRot(
                              (float)this.smoothPitchBuffer
                                 .stream()
                                 .mapToDouble(d -> (double)d.floatValue())
                                 .average()
                                 .orElse((double)this.prevRotation.getPitch())
                           );
                     }
                  }

                  this.prevRotation = null;
               }

               this.target = null;
         }
      }
   }

   @Override
   public void onSendPacket(PacketEvent event) {
      if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
         ServerboundMovePlayerPacket packet = (ServerboundMovePlayerPacket)event.getPacket();
         if (packet instanceof Rot || packet instanceof PosRot) {
            this.serverRotation = new Rotation(packet.getYRot(0.0F), packet.getXRot(0.0F));
         }
      }
   }

   @Override
   public void onWorldEvent(WorldEvent event) {
      this.serverRotation = null;
      this.target = null;
   }

   public void pig() {
      if (this.target != null) {
         Rotation actual = this.processor.peekRotation(this.target.rotation);
         this.ctx.player().setYRot(actual.getYaw());
      }
   }

   public Optional<Rotation> getEffectiveRotation() {
      return Baritone.settings().freeLook.value ? Optional.ofNullable(this.serverRotation) : Optional.empty();
   }

   @Override
   public void onPlayerRotationMove(RotationMoveEvent event) {
      if (this.target != null) {
         Rotation actual = this.processor.peekRotation(this.target.rotation);
         event.setYaw(actual.getYaw());
         event.setPitch(actual.getPitch());
      }
   }

   private abstract static class AbstractAimProcessor implements ITickableAimProcessor {
      protected final IPlayerContext ctx;
      private final ForkableRandom rand;
      private double randomYawOffset;
      private double randomPitchOffset;

      public AbstractAimProcessor(IPlayerContext ctx) {
         this.ctx = ctx;
         this.rand = new ForkableRandom();
      }

      private AbstractAimProcessor(LookBehavior.AbstractAimProcessor source) {
         this.ctx = source.ctx;
         this.rand = source.rand.fork();
         this.randomYawOffset = source.randomYawOffset;
         this.randomPitchOffset = source.randomPitchOffset;
      }

      @Override
      public final Rotation peekRotation(Rotation rotation) {
         Rotation prev = this.getPrevRotation();
         float desiredYaw = rotation.getYaw();
         float desiredPitch = rotation.getPitch();

         // When interacting with blocks, aim directly and stably without nudge or noise to prevent crosshair jitter
         if (this.isBlockInteracting()) {
            return new Rotation(desiredYaw, desiredPitch).clamp();
         }

         if (desiredPitch == prev.getPitch()) {
            desiredPitch = this.nudgeToLevel(desiredPitch);
         }

         desiredYaw = (float)((double)desiredYaw + this.randomYawOffset);
         desiredPitch = (float)((double)desiredPitch + this.randomPitchOffset);
         return new Rotation(this.calculateMouseMove(prev.getYaw(), desiredYaw), this.calculateMouseMove(prev.getPitch(), desiredPitch)).clamp();
      }

      protected boolean isBlockInteracting() {
         return false;
      }

      @Override
      public final void tick() {
         this.randomYawOffset = (this.rand.nextDouble() - 0.5) * Baritone.settings().randomLooking.value;
         this.randomPitchOffset = (this.rand.nextDouble() - 0.5) * Baritone.settings().randomLooking.value;
         double random = this.rand.nextDouble() - 0.5;
         if (Math.abs(random) < 0.1) {
            random *= 4.0;
         }

         this.randomYawOffset = this.randomYawOffset + random * Baritone.settings().randomLooking113.value;
      }

      @Override
      public final void advance(int ticks) {
         for (int i = 0; i < ticks; i++) {
            this.tick();
         }
      }

      @Override
      public Rotation nextRotation(Rotation rotation) {
         Rotation actual = this.peekRotation(rotation);
         this.tick();
         return actual;
      }

      @Override
      public final ITickableAimProcessor fork() {
         return new LookBehavior.AbstractAimProcessor(this) {
            private Rotation prev = AbstractAimProcessor.this.getPrevRotation();

            @Override
            public Rotation nextRotation(Rotation rotation) {
               return this.prev = super.nextRotation(rotation);
            }

            @Override
            protected Rotation getPrevRotation() {
               return this.prev;
            }
         };
      }

      protected abstract Rotation getPrevRotation();

      private float nudgeToLevel(float pitch) {
         if (pitch < -20.0F) {
            return pitch + 1.0F;
         } else {
            return pitch > 10.0F ? pitch - 1.0F : pitch;
         }
      }

      private float calculateMouseMove(float current, float target) {
         float delta = target - current;
         double deltaPx = this.angleToMouse(delta);
         return current + this.mouseToAngle(deltaPx);
      }

      private double angleToMouse(float angleDelta) {
         float minAngleChange = this.mouseToAngle(1.0);
         return (double)Math.round(angleDelta / minAngleChange);
      }

      private float mouseToAngle(double mouseDelta) {
         double f = (Double)this.ctx.minecraft().options.sensitivity().get() * 0.6F + 0.2F;
         return (float)(mouseDelta * f * f * f * 8.0) * 0.15F;
      }
   }

   private static final class AimProcessor extends LookBehavior.AbstractAimProcessor {
      private final LookBehavior lookBehavior;

      public AimProcessor(LookBehavior lookBehavior, IPlayerContext ctx) {
         super(ctx);
         this.lookBehavior = lookBehavior;
      }

      @Override
      protected boolean isBlockInteracting() {
         return this.lookBehavior.target != null && this.lookBehavior.target.blockInteract;
      }

      @Override
      protected Rotation getPrevRotation() {
         return this.ctx.playerRotations();
      }
   }

   private static class Target {
      public final Rotation rotation;
      public final LookBehavior.Target.Mode mode;
      public final boolean blockInteract;

      public Target(Rotation rotation, LookBehavior.Target.Mode mode, boolean blockInteract) {
         this.rotation = rotation;
         this.mode = mode;
         this.blockInteract = blockInteract;
      }

      static enum Mode {
         CLIENT,
         SERVER,
         NONE;

         static LookBehavior.Target.Mode resolve(IPlayerContext ctx, boolean blockInteract) {
            Settings settings = Baritone.settings();
            boolean antiCheat = settings.antiCheatCompatibility.value;
            boolean blockFreeLook = settings.blockFreeLook.value;
            if (ctx.player().isFallFlying()) {
               return settings.elytraFreeLook.value ? SERVER : CLIENT;
            } else if (settings.freeLook.value) {
               if (blockInteract) {
                  return blockFreeLook ? SERVER : CLIENT;
               } else {
                  return antiCheat ? SERVER : NONE;
               }
            } else {
               return CLIENT;
            }
         }
      }
   }
}
