package meteordevelopment.meteorclient.systems.modules.combat;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.ActionType;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

public class KillAura extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargeting = this.settings.createGroup("Targeting");
   private final SettingGroup sgTiming = this.settings.createGroup("Timing");
   private final Setting<KillAura.Weapon> weapon = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("weapon"))
                  .description("Only attacks an entity when a specified weapon is in your hand."))
               .defaultValue(KillAura.Weapon.All))
            .build()
      );
   private final Setting<KillAura.RotationMode> rotation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("rotate"))
                  .description("Determines when you should rotate towards the target."))
               .defaultValue(KillAura.RotationMode.Always))
            .build()
      );
   private final Setting<Boolean> autoSwitch = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-switch")
            .description("Switches to your selected weapon when attacking the target.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> onlyOnClick = this.sgGeneral
      .add(new BoolSetting.Builder().name("only-on-click").description("Only attacks when holding left click.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> onlyOnLook = this.sgGeneral
      .add(new BoolSetting.Builder().name("only-on-look").description("Only attacks when looking at an entity.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> pauseOnCombat = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-baritone")
            .description("Freezes Baritone temporarily until you are finished attacking the entity.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<KillAura.ShieldMode> shieldMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shield-mode"))
                     .description("Will try and use an axe to break target shields."))
                  .defaultValue(KillAura.ShieldMode.Break))
               .visible(() -> this.autoSwitch.get() && this.weapon.get() != KillAura.Weapon.Axe))
            .build()
      );
   private final Setting<Set<EntityType<?>>> entities = this.sgTargeting
      .add(new EntityTypeListSetting.Builder().name("entities").description("Entities to attack.").onlyAttackable().defaultValue(EntityType.PLAYER).build());
   private final Setting<SortPriority> priority = this.sgTargeting
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("priority"))
                  .description("How to filter targets within range."))
               .defaultValue(SortPriority.ClosestAngle))
            .build()
      );
   private final Setting<Integer> maxTargets = this.sgTargeting
      .add(
         new IntSetting.Builder()
            .name("max-targets")
            .description("How many entities to target at once.")
            .defaultValue(Integer.valueOf(1))
            .min(1)
            .sliderRange(1, 5)
            .visible(() -> !this.onlyOnLook.get())
            .build()
      );
   private final Setting<Double> range = this.sgTargeting
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("The maximum range the entity can be to attack it.")
            .defaultValue(4.5)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Double> wallsRange = this.sgTargeting
      .add(
         new DoubleSetting.Builder()
            .name("walls-range")
            .description("The maximum range the entity can be attacked through walls.")
            .defaultValue(3.5)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<KillAura.EntityAge> mobAgeFilter = this.sgTargeting
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mob-age-filter"))
                  .description("Determines the age of the mobs to target (baby, adult, or both)."))
               .defaultValue(KillAura.EntityAge.Adult))
            .build()
      );
   private final Setting<Boolean> ignoreNamed = this.sgTargeting
      .add(
         new BoolSetting.Builder().name("ignore-named").description("Whether or not to attack mobs with a name.").defaultValue(Boolean.valueOf(false)).build()
      );
   private final Setting<Boolean> ignorePassive = this.sgTargeting
      .add(
         new BoolSetting.Builder()
            .name("ignore-passive")
            .description("Will only attack sometimes passive mobs if they are targeting you.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Boolean> ignoreTamed = this.sgTargeting
      .add(new BoolSetting.Builder().name("ignore-tamed").description("Will avoid attacking mobs you tamed.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> pauseOnLag = this.sgTiming
      .add(new BoolSetting.Builder().name("pause-on-lag").description("Pauses if the server is lagging.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> pauseOnUse = this.sgTiming
      .add(new BoolSetting.Builder().name("pause-on-use").description("Does not attack while using an item.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> pauseOnCA = this.sgTiming
      .add(new BoolSetting.Builder().name("pause-on-CA").description("Does not attack while CA is placing.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> tpsSync = this.sgTiming
      .add(
         new BoolSetting.Builder()
            .name("TPS-sync")
            .description("Tries to sync attack delay with the server's TPS.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Boolean> customDelay = this.sgTiming
      .add(
         new BoolSetting.Builder()
            .name("custom-delay")
            .description("Use a custom delay instead of the vanilla cooldown.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Integer> hitDelay = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("hit-delay")
            .description("How fast you hit the entity in ticks.")
            .defaultValue(Integer.valueOf(11))
            .min(0)
            .sliderMax(60)
            .visible(this.customDelay::get)
            .build()
      );
   private final Setting<Integer> switchDelay = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("switch-delay")
            .description("How many ticks to wait before hitting an entity after switching hotbar slots.")
            .defaultValue(Integer.valueOf(0))
            .min(0)
            .sliderMax(10)
            .build()
      );
   private static final Predicate<ItemStack> PRED_SWORD = stack -> stack.getItem() instanceof SwordItem;
   private static final Predicate<ItemStack> PRED_AXE = stack -> stack.getItem() instanceof AxeItem;
   private static final Predicate<ItemStack> PRED_MACE = stack -> stack.getItem() instanceof MaceItem;
   private static final Predicate<ItemStack> PRED_TRIDENT = stack -> stack.getItem() instanceof TridentItem;
   private static final Predicate<ItemStack> PRED_ALL_WEAPONS = stack -> stack.getItem() instanceof AxeItem
      || stack.getItem() instanceof SwordItem
      || stack.getItem() instanceof MaceItem
      || stack.getItem() instanceof TridentItem;
   private static final Predicate<ItemStack> PRED_ANY = stack -> true;

   private final List<Entity> targets = new ArrayList<>();
   private final Int2LongOpenHashMap angryMobs = new Int2LongOpenHashMap();
   private final Predicate<Entity> entityCheckPredicate = this::entityCheck;
   private int switchTimer;
   private int hitTimer;
   private boolean wasPathing = false;
   public boolean attacking;

   public KillAura() {
      super(Categories.Combat, "kill-aura", "Attacks specified entities around you.");
   }

   @Override
   public void onDeactivate() {
      this.targets.clear();
      this.angryMobs.clear();
      this.attacking = false;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player.isAlive() && PlayerUtils.getGameMode() != GameType.SPECTATOR) {
         if (!this.angryMobs.isEmpty()) {
            long now = System.currentTimeMillis();
            this.angryMobs.int2LongEntrySet().removeIf(entry -> entry.getLongValue() < now);
         }

         if (this.mc.player.getLastHurtByMob() != null) {
            this.angryMobs.put(this.mc.player.getLastHurtByMob().getId(), System.currentTimeMillis() + 30000L);
         }

         if (!this.pauseOnUse.get() || !this.mc.gameMode.isDestroying() && !this.mc.player.isUsingItem()) {
            if (!this.onlyOnClick.get() || this.mc.options.keyAttack.isDown()) {
               if (!(TickRate.INSTANCE.getTimeSinceLastTick() >= 3.0F) || !this.pauseOnLag.get()) {
                  if (!this.pauseOnCA.get() || !Modules.get().get(CrystalAura.class).isActive() || Modules.get().get(CrystalAura.class).kaTimer <= 0) {
                     if (this.onlyOnLook.get()) {
                        Entity targeted = this.mc.crosshairPickEntity;
                        if (targeted == null) {
                           return;
                        }

                        if (!this.entityCheck(targeted)) {
                           return;
                        }

                        this.targets.clear();
                        this.targets.add(this.mc.crosshairPickEntity);
                     } else {
                        this.targets.clear();
                        TargetUtils.getList(this.targets, this.entityCheckPredicate, this.priority.get(), this.maxTargets.get());
                     }

                     if (this.targets.isEmpty()) {
                        this.attacking = false;
                        if (this.wasPathing) {
                           PathManagers.get().resume();
                           this.wasPathing = false;
                        }
                     } else {
                        Entity primary = this.targets.getFirst();
                        if (this.autoSwitch.get()) {
                           Predicate<ItemStack> predicate = switch ((KillAura.Weapon)this.weapon.get()) {
                              case Sword -> PRED_SWORD;
                              case Axe -> PRED_AXE;
                              case Mace -> PRED_MACE;
                              case Trident -> PRED_TRIDENT;
                              case All -> PRED_ALL_WEAPONS;
                              default -> PRED_ANY;
                           };
                           FindItemResult weaponResult = InvUtils.findInHotbar(predicate);
                           if (this.shouldShieldBreak()) {
                              FindItemResult axeResult = InvUtils.findInHotbar(PRED_AXE);
                              if (axeResult.found()) {
                                 weaponResult = axeResult;
                              }
                           }

                           InvUtils.swap(weaponResult.slot(), false);
                        }

                        if (this.itemInHand()) {
                           this.attacking = true;
                           if (this.rotation.get() == KillAura.RotationMode.Always) {
                              Rotations.rotate(Rotations.getYaw(primary), Rotations.getPitch(primary, Target.Body));
                           }

                           if (this.pauseOnCombat.get() && PathManagers.get().isPathing() && !this.wasPathing) {
                              PathManagers.get().pause();
                              this.wasPathing = true;
                           }

                           if (this.delayCheck()) {
                              this.targets.forEach(this::attack);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (event.packet instanceof ServerboundSetCarriedItemPacket) {
         this.switchTimer = this.switchDelay.get();
      }

      if (event.packet instanceof IPlayerInteractEntityC2SPacket packet && packet.getType() == ActionType.ATTACK) {
         this.markEntityAndGroupAngry(packet.getEntity());
      }
   }

   private boolean shouldShieldBreak() {
      for (Entity target : this.targets) {
         if (target instanceof Player player
            && player.isDamageSourceBlocked(this.mc.level.damageSources().playerAttack(this.mc.player))
            && this.shieldMode.get() == KillAura.ShieldMode.Break) {
            return true;
         }
      }

      return false;
   }

   private boolean entityCheck(Entity entity) {
      if (!entity.equals(this.mc.player) && !entity.equals(this.mc.cameraEntity)) {
         if ((!(entity instanceof LivingEntity livingEntity) || !livingEntity.isDeadOrDying()) && entity.isAlive()) {
            AABB hitbox = entity.getBoundingBox();
            if (!PlayerUtils.isWithin(
               Mth.clamp(this.mc.player.getX(), hitbox.minX, hitbox.maxX),
               Mth.clamp(this.mc.player.getY(), hitbox.minY, hitbox.maxY),
               Mth.clamp(this.mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
               this.range.get()
            )) {
               return false;
            } else if (!this.entities.get().contains(entity.getType())) {
               return false;
            } else if (this.ignoreNamed.get() && entity.hasCustomName()) {
               return false;
            } else if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, this.wallsRange.get())) {
               return false;
            } else {
               if (this.ignoreTamed.get()
                  && entity instanceof OwnableEntity tameable
                  && tameable.getOwnerUUID() != null
                  && tameable.getOwnerUUID().equals(this.mc.player.getUUID())) {
                  return false;
               }

               if (this.ignorePassive.get()) {
                  if (entity instanceof EnderMan enderman) {
                     if (!enderman.isCreepy() && !this.isMobAngry(enderman)) {
                        return false;
                     }
                  } else if (entity instanceof ZombifiedPiglin piglin) {
                     if (!this.isMobAngry(piglin) && !piglin.isAggressive() && !piglin.isSprinting()) {
                        return false;
                     }
                  } else if (entity instanceof Piglin piglin) {
                     boolean isCalm = !this.isMobAngry(piglin)
                        && this.isWearingGold(this.mc.player)
                        && !piglin.isAggressive()
                        && piglin.getArmPose() != PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON
                        && piglin.getArmPose() != PiglinArmPose.CROSSBOW_HOLD
                        && piglin.getArmPose() != PiglinArmPose.CROSSBOW_CHARGE
                        && !piglin.isSprinting();
                     if (isCalm) {
                        return false;
                     }
                  } else if (entity instanceof Wolf wolf) {
                     if (!wolf.isAggressive() && !this.isMobAngry(wolf)) {
                        return false;
                     }
                  } else if (entity instanceof NeutralMob) {
                     if (!this.isMobAngry((LivingEntity) entity)) {
                        return false;
                     }
                  }
               }

               if (entity instanceof Player player) {
                  if (player.isCreative()) {
                     return false;
                  }

                  if (!Friends.get().shouldAttack(player)) {
                     return false;
                  }

                  if (this.shieldMode.get() == KillAura.ShieldMode.Ignore
                     && player.isDamageSourceBlocked(this.mc.level.damageSources().playerAttack(this.mc.player))) {
                     return false;
                  }
               }

               if (entity instanceof Animal animal) {
                  return switch ((KillAura.EntityAge)this.mobAgeFilter.get()) {
                     case Baby -> animal.isBaby();
                     case Adult -> !animal.isBaby();
                     case Both -> true;
                  };
               }

               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean delayCheck() {
      if (this.switchTimer > 0) {
         this.switchTimer--;
         return false;
      } else {
         float delay = this.customDelay.get() ? (float)this.hitDelay.get().intValue() : 0.5F;
         if (this.tpsSync.get()) {
            float tps = TickRate.INSTANCE.getTickRate();
            if (tps > 0.0F) {
               delay /= tps / 20.0F;
            }
         }

         if (Float.isNaN(delay) || delay <= 0.0F) {
            delay = 0.5F;
         }

         if (this.customDelay.get()) {
            if ((float)this.hitTimer < delay) {
               this.hitTimer++;
               return false;
            } else {
               return true;
            }
         } else {
            return this.mc.player.getAttackStrengthScale(delay) >= 1.0F;
         }
      }
   }

   private void attack(Entity target) {
      if (this.rotation.get() == KillAura.RotationMode.OnHit) {
         Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body));
      }

      this.markEntityAndGroupAngry(target);
      this.mc.gameMode.attack(this.mc.player, target);
      this.mc.player.swing(InteractionHand.MAIN_HAND);
      this.hitTimer = 0;
   }

   private void markEntityAndGroupAngry(Entity target) {
      if (!(target instanceof LivingEntity)) return;

      long expire = System.currentTimeMillis() + 30000L;
      this.angryMobs.put(target.getId(), expire);
      if (this.mc.level != null) {
         if (target instanceof ZombifiedPiglin) {
            for (Entity nearby : this.mc.level.entitiesForRendering()) {
               if (nearby instanceof ZombifiedPiglin && nearby.distanceToSqr(target) <= 256.0) {
                  this.angryMobs.put(nearby.getId(), expire);
               }
            }
         } else if (target instanceof Piglin) {
            for (Entity nearby : this.mc.level.entitiesForRendering()) {
               if (nearby instanceof Piglin && nearby.distanceToSqr(target) <= 256.0) {
                  this.angryMobs.put(nearby.getId(), expire);
               }
            }
         }
      }
   }

   private boolean isMobAngry(LivingEntity mob) {
      if (mob == null) return false;

      long angryUntil = this.angryMobs.get(mob.getId());
      if (angryUntil > System.currentTimeMillis()) {
         return true;
      }

      if (mob instanceof Mob m) {
         if (m.isAggressive()) return true;
         if (m.getTarget() != null && m.getTarget().equals(this.mc.player)) return true;
         if (m.getLastHurtByMob() != null && m.getLastHurtByMob().equals(this.mc.player)) return true;
      }

      if (mob instanceof NeutralMob nm) {
         if (nm.isAngry()) return true;
         if (this.mc.player != null && nm.isAngryAt(this.mc.player)) return true;
      }

      if (mob.isSprinting()) return true;

      return false;
   }

   private boolean isWearingGold(LivingEntity entity) {
      if (entity == null) return false;
      return PiglinAi.isWearingGold(entity);
   }

   private boolean itemInHand() {
      if (this.shouldShieldBreak()) {
         return this.mc.player.getMainHandItem().getItem() instanceof AxeItem;
      } else {
         return switch ((KillAura.Weapon)this.weapon.get()) {
            case Sword -> this.mc.player.getMainHandItem().getItem() instanceof SwordItem;
            case Axe -> this.mc.player.getMainHandItem().getItem() instanceof AxeItem;
            case Mace -> this.mc.player.getMainHandItem().getItem() instanceof MaceItem;
            case Trident -> this.mc.player.getMainHandItem().getItem() instanceof TridentItem;
            case All -> this.mc.player.getMainHandItem().getItem() instanceof AxeItem
            || this.mc.player.getMainHandItem().getItem() instanceof SwordItem
            || this.mc.player.getMainHandItem().getItem() instanceof MaceItem
            || this.mc.player.getMainHandItem().getItem() instanceof TridentItem;
            default -> true;
         };
      }
   }

   public Entity getTarget() {
      return !this.targets.isEmpty() ? this.targets.getFirst() : null;
   }

   @Override
   public String getInfoString() {
      return !this.targets.isEmpty() ? EntityUtils.getName(this.getTarget()) : null;
   }

   public static enum EntityAge {
      Baby,
      Adult,
      Both;
   }

   public static enum RotationMode {
      Always,
      OnHit,
      None;
   }

   public static enum ShieldMode {
      Ignore,
      Break,
      None;
   }

   public static enum Weapon {
      Sword,
      Axe,
      Mace,
      Trident,
      All,
      Any;
   }
}
