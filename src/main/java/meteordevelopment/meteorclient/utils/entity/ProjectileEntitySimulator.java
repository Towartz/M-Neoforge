package meteordevelopment.meteorclient.utils.entity;

import java.util.Objects;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.CrossbowItemAccessor;
import meteordevelopment.meteorclient.mixin.ProjectileInGroundAccessor;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.MissHitResult;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class ProjectileEntitySimulator {
   private static final MutableBlockPos blockPos = new MutableBlockPos();
   private static final Vec3 pos3d = new Vec3(0.0, 0.0, 0.0);
   private static final Vec3 prevPos3d = new Vec3(0.0, 0.0, 0.0);
   public final Vector3d pos = new Vector3d();
   private final Vector3d velocity = new Vector3d();
   private Entity simulatingEntity;
   private double gravity;
   private double airDrag;
   private double waterDrag;
   private float height;
   private float width;

   public boolean set(Entity user, ItemStack itemStack, double simulated, boolean accurate, float tickDelta) {
      Item item = itemStack.getItem();
      Objects.requireNonNull(item);
      switch (item) {
         case BowItem ignored:
            double charge = (double)BowItem.getPowerForTime(MeteorClient.mc.player.getTicksUsingItem());
            if (charge <= 0.1) {
               return false;
            }

            this.set(user, 0.0, charge * 3.0, simulated, 0.05, 0.6, accurate, tickDelta, EntityType.ARROW);
            break;
         case CrossbowItem ignoredx:
            ChargedProjectiles projectilesComponent = (ChargedProjectiles)itemStack.get(DataComponents.CHARGED_PROJECTILES);
            if (projectilesComponent == null) {
               return false;
            }

            if (projectilesComponent.contains(Items.FIREWORK_ROCKET)) {
               this.set(
                  user, 0.0, (double)CrossbowItemAccessor.getSpeed(projectilesComponent), simulated, 0.0, 0.6, accurate, tickDelta, EntityType.FIREWORK_ROCKET
               );
            } else {
               this.set(user, 0.0, (double)CrossbowItemAccessor.getSpeed(projectilesComponent), simulated, 0.05, 0.6, accurate, tickDelta, EntityType.ARROW);
            }
            break;
         case WindChargeItem ignoredxx:
            this.set(user, 0.0, 1.5, simulated, 0.0, 1.0, accurate, tickDelta, EntityType.WIND_CHARGE);
            this.airDrag = 1.0;
            break;
         case FishingRodItem ignoredxxx:
            this.setFishingBobber(user, tickDelta);
            break;
         case TridentItem ignoredxxxx:
            this.set(user, 0.0, 2.5, simulated, 0.05, 0.99, accurate, tickDelta, EntityType.TRIDENT);
            break;
         case SnowballItem ignoredxxxxx:
            this.set(user, 0.0, 1.5, simulated, 0.03, 0.8, accurate, tickDelta, EntityType.SNOWBALL);
            break;
         case EggItem ignoredxxxxxx:
            this.set(user, 0.0, 1.5, simulated, 0.03, 0.8, accurate, tickDelta, EntityType.EGG);
            break;
         case EnderpearlItem ignoredxxxxxxx:
            this.set(user, 0.0, 1.5, simulated, 0.03, 0.8, accurate, tickDelta, EntityType.ENDER_PEARL);
            break;
         case ExperienceBottleItem ignoredxxxxxxxx:
            this.set(user, -20.0, 0.7, simulated, 0.07, 0.8, accurate, tickDelta, EntityType.EXPERIENCE_BOTTLE);
            break;
         case ThrowablePotionItem ignoredxxxxxxxxx:
            this.set(user, -20.0, 0.5, simulated, 0.05, 0.8, accurate, tickDelta, EntityType.POTION);
            break;
         default:
            return false;
      }

      return true;
   }

   public void set(
      Entity user, double roll, double speed, double simulated, double gravity, double waterDrag, boolean accurate, float tickDelta, EntityType<?> type
   ) {
      Utils.set(this.pos, user, (double)tickDelta).add(0.0, (double)user.getEyeHeight(user.getPose()), 0.0);
      double yaw;
      double pitch;
      if (user == MeteorClient.mc.player && Rotations.rotating) {
         yaw = (double)Rotations.serverYaw;
         pitch = (double)Rotations.serverPitch;
      } else {
         yaw = (double)user.getViewYRot(tickDelta);
         pitch = (double)user.getViewXRot(tickDelta);
      }

      double x;
      double y;
      double z;
      if (simulated == 0.0) {
         x = -Math.sin(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);
         y = -Math.sin((pitch + roll) * 0.017453292);
         z = Math.cos(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);
      } else {
         Vec3 vec3d = user.getUpVector(1.0F);
         Quaterniond quaternion = new Quaterniond().setAngleAxis(simulated, vec3d.x, vec3d.y, vec3d.z);
         Vec3 vec3d2 = user.getViewVector(1.0F);
         Vector3d vector3f = new Vector3d(vec3d2.x, vec3d2.y, vec3d2.z);
         vector3f.rotate(quaternion);
         x = vector3f.x;
         y = vector3f.y;
         z = vector3f.z;
      }

      this.velocity.set(x, y, z).normalize().mul(speed);
      if (accurate) {
         Vec3 vel = user.getDeltaMovement();
         this.velocity.add(vel.x, user.onGround() ? 0.0 : vel.y, vel.z);
      }

      this.simulatingEntity = user;
      this.gravity = gravity;
      this.airDrag = 0.99;
      this.waterDrag = waterDrag;
      this.width = type.getWidth();
      this.height = type.getHeight();
   }

   public boolean set(Entity entity, boolean accurate) {
      if (entity instanceof ProjectileInGroundAccessor ppe && ppe.getInGround()) {
         return false;
      }

      if (entity instanceof Arrow) {
         this.set(entity, 0.05, 0.6, accurate);
      } else if (entity instanceof ThrownTrident) {
         this.set(entity, 0.05, 0.99, accurate);
      } else if (entity instanceof ThrownEnderpearl || entity instanceof Snowball || entity instanceof ThrownEgg) {
         this.set(entity, 0.03, 0.8, accurate);
      } else if (entity instanceof ThrownExperienceBottle) {
         this.set(entity, 0.07, 0.8, accurate);
      } else if (entity instanceof ThrownPotion) {
         this.set(entity, 0.05, 0.8, accurate);
      } else {
         if (!(entity instanceof WitherSkull) && !(entity instanceof LargeFireball) && !(entity instanceof DragonFireball) && !(entity instanceof WindCharge)) {
            return false;
         }

         this.set(entity, 0.0, 1.0, accurate);
         this.airDrag = 1.0;
      }

      if (entity.isNoGravity()) {
         this.gravity = 0.0;
      }

      return true;
   }

   public void set(Entity entity, double gravity, double waterDrag, boolean accurate) {
      this.pos.set(entity.getX(), entity.getY(), entity.getZ());
      double speed = entity.getDeltaMovement().length();
      this.velocity.set(entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z).normalize().mul(speed);
      if (accurate) {
         Vec3 vel = entity.getDeltaMovement();
         this.velocity.add(vel.x, entity.onGround() ? 0.0 : vel.y, vel.z);
      }

      this.simulatingEntity = entity;
      this.gravity = gravity;
      this.airDrag = 0.99;
      this.waterDrag = waterDrag;
      this.width = entity.getBbWidth();
      this.height = entity.getBbHeight();
   }

   public void setFishingBobber(Entity user, float tickDelta) {
      double yaw;
      double pitch;
      if (user == MeteorClient.mc.player && Rotations.rotating) {
         yaw = (double)Rotations.serverYaw;
         pitch = (double)Rotations.serverPitch;
      } else {
         yaw = (double)user.getViewYRot(tickDelta);
         pitch = (double)user.getViewXRot(tickDelta);
      }

      double h = Math.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
      double i = Math.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
      double j = -Math.cos(-pitch * (float) (Math.PI / 180.0));
      double k = Math.sin(-pitch * (float) (Math.PI / 180.0));
      Utils.set(this.pos, user, (double)tickDelta).sub(i * 0.3, 0.0, h * 0.3).add(0.0, (double)user.getEyeHeight(user.getPose()), 0.0);
      this.velocity.set(-i, Mth.clamp(-(k / j), -5.0, 5.0), -h);
      double l = this.velocity.length();
      this.velocity.mul(0.6 / l + 0.5, 0.6 / l + 0.5, 0.6 / l + 0.5);
      this.simulatingEntity = user;
      this.gravity = 0.03;
      this.airDrag = 0.92;
      this.waterDrag = 0.0;
      this.width = EntityType.FISHING_BOBBER.getWidth();
      this.height = EntityType.FISHING_BOBBER.getHeight();
   }

   public HitResult tick() {
      ((IVec3d)prevPos3d).set(this.pos);
      this.pos.add(this.velocity);
      this.velocity.mul(this.isTouchingWater() ? this.waterDrag : this.airDrag);
      this.velocity.sub(0.0, this.gravity, 0.0);
      if (this.pos.y < (double)MeteorClient.mc.level.getMinBuildHeight()) {
         return MissHitResult.INSTANCE;
      } else {
         int chunkX = SectionPos.posToSectionCoord(this.pos.x);
         int chunkZ = SectionPos.posToSectionCoord(this.pos.z);
         if (!MeteorClient.mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return MissHitResult.INSTANCE;
         } else {
            ((IVec3d)pos3d).set(this.pos);
            if (pos3d.equals(prevPos3d)) {
               return MissHitResult.INSTANCE;
            } else {
               HitResult hitResult = this.getCollision();
               return hitResult.getType() == Type.MISS ? null : hitResult;
            }
         }
      }
   }

   private boolean isTouchingWater() {
      blockPos.set(this.pos.x, this.pos.y, this.pos.z);
      FluidState fluidState = MeteorClient.mc.level.getFluidState(blockPos);
      return fluidState.getType() != Fluids.WATER && fluidState.getType() != Fluids.FLOWING_WATER
         ? false
         : this.pos.y - (double)((int)this.pos.y) <= (double)fluidState.getOwnHeight();
   }

   private HitResult getCollision() {
      HitResult hitResult = MeteorClient.mc
         .level
         .clip(new ClipContext(prevPos3d, pos3d, Block.COLLIDER, this.waterDrag == 0.0 ? Fluid.ANY : Fluid.NONE, this.simulatingEntity));
      if (hitResult.getType() != Type.MISS) {
         ((IVec3d)pos3d).set(hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z);
      }

      AABB box = new AABB(
            prevPos3d.x - (double)(this.width / 2.0F),
            prevPos3d.y,
            prevPos3d.z - (double)(this.width / 2.0F),
            prevPos3d.x + (double)(this.width / 2.0F),
            prevPos3d.y + (double)this.height,
            prevPos3d.z + (double)(this.width / 2.0F)
         )
         .expandTowards(this.velocity.x, this.velocity.y, this.velocity.z)
         .inflate(1.0);
      HitResult hitResult2 = ProjectileUtil.getEntityHitResult(
         MeteorClient.mc.level,
         this.simulatingEntity == MeteorClient.mc.player ? null : this.simulatingEntity,
         prevPos3d,
         pos3d,
         box,
         entity -> !entity.isSpectator() && entity.isAlive() && entity.isPickable()
      );
      if (hitResult2 != null) {
         hitResult = hitResult2;
      }

      return hitResult;
   }
}
