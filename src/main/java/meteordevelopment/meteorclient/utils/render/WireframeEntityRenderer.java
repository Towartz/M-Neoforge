package meteordevelopment.meteorclient.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Axis;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.mixin.AgeableListModelAccessor;
import meteordevelopment.meteorclient.mixin.LivingEntityRendererAccessor;
import meteordevelopment.meteorclient.mixin.PlayerRendererAccessor;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Chams;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.LlamaModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.WaterPatchModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.geom.ModelPart.Polygon;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

public class WireframeEntityRenderer {
   private static final PoseStack matrices = new PoseStack();
   private static final Vector4f pos1 = new Vector4f();
   private static final Vector4f pos2 = new Vector4f();
   private static final Vector4f pos3 = new Vector4f();
   private static final Vector4f pos4 = new Vector4f();
   private static double offsetX;
   private static double offsetY;
   private static double offsetZ;
   private static Color sideColor;
   private static Color lineColor;
   private static ShapeMode shapeMode;

   private WireframeEntityRenderer() {
   }

   public static void render(Render3DEvent event, Entity entity, double scale, Color sideColor, Color lineColor, ShapeMode shapeMode) {
      WireframeEntityRenderer.sideColor = sideColor;
      WireframeEntityRenderer.lineColor = lineColor;
      WireframeEntityRenderer.shapeMode = shapeMode;
      offsetX = Mth.lerp((double)event.tickDelta, entity.xOld, entity.getX());
      offsetY = Mth.lerp((double)event.tickDelta, entity.yOld, entity.getY());
      offsetZ = Mth.lerp((double)event.tickDelta, entity.zOld, entity.getZ());
      matrices.pushPose();
      matrices.scale((float)scale, (float)scale, (float)scale);
      EntityRenderer<?> entityRenderer = MeteorClient.mc.getEntityRenderDispatcher().getRenderer(entity);
      if (entityRenderer instanceof LivingEntityRenderer renderer) {
         LivingEntity livingEntity = (LivingEntity)entity;
         EntityModel<LivingEntity> model = renderer.getModel();
         if (entityRenderer instanceof PlayerRenderer r) {
            PlayerModel<AbstractClientPlayer> playerModel = (PlayerModel<AbstractClientPlayer>)r.getModel();
            playerModel.crouching = entity.isCrouching();
            ArmPose armPose = PlayerRendererAccessor.meteor$getArmPose((AbstractClientPlayer)entity, InteractionHand.MAIN_HAND);
            ArmPose armPose2 = PlayerRendererAccessor.meteor$getArmPose((AbstractClientPlayer)entity, InteractionHand.OFF_HAND);
            if (armPose.isTwoHanded()) {
               armPose2 = livingEntity.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
            }

            if (livingEntity.getMainArm() == HumanoidArm.RIGHT) {
               playerModel.rightArmPose = armPose;
               playerModel.leftArmPose = armPose2;
            } else {
               playerModel.rightArmPose = armPose2;
               playerModel.leftArmPose = armPose;
            }
         }

         model.attackTime = livingEntity.getAttackAnim(event.tickDelta);
         model.riding = livingEntity.isPassenger();
         model.young = livingEntity.isBaby();
         float bodyYaw = Mth.rotLerp(event.tickDelta, livingEntity.yBodyRotO, livingEntity.yBodyRot);
         float headYaw = Mth.rotLerp(event.tickDelta, livingEntity.yHeadRotO, livingEntity.yHeadRot);
         float yaw = headYaw - bodyYaw;
         if (livingEntity.isPassenger() && livingEntity.getVehicle() instanceof LivingEntity livingEntity2) {
            bodyYaw = Mth.rotLerp(event.tickDelta, livingEntity2.yBodyRotO, livingEntity2.yBodyRot);
            yaw = headYaw - bodyYaw;
            float animationProgress = Mth.wrapDegrees(yaw);
            if (animationProgress < -85.0F) {
               animationProgress = -85.0F;
            }

            if (animationProgress >= 85.0F) {
               animationProgress = 85.0F;
            }

            bodyYaw = headYaw - animationProgress;
            if (animationProgress * animationProgress > 2500.0F) {
               bodyYaw = (float)((double)bodyYaw + (double)animationProgress * 0.2);
            }

            yaw = headYaw - bodyYaw;
         }

         float pitch = Mth.lerp(event.tickDelta, livingEntity.xRotO, livingEntity.getXRot());
         float animationProgressx = ((LivingEntityRendererAccessor)renderer).meteor$getBob(livingEntity, event.tickDelta);
         float limbDistance = 0.0F;
         float limbAngle = 0.0F;
         if (!livingEntity.isPassenger() && livingEntity.isAlive()) {
            limbDistance = livingEntity.walkAnimation.speed(event.tickDelta);
            limbAngle = livingEntity.walkAnimation.position(event.tickDelta);
            if (livingEntity.isBaby()) {
               limbAngle *= 3.0F;
            }

            if (limbDistance > 1.0F) {
               limbDistance = 1.0F;
            }
         }

         model.prepareMobModel(livingEntity, limbAngle, limbDistance, event.tickDelta);
         model.setupAnim(livingEntity, limbAngle, limbDistance, animationProgressx, yaw, pitch);
         ((LivingEntityRendererAccessor)renderer).meteor$setupRotations(livingEntity, matrices, animationProgressx, bodyYaw, event.tickDelta, livingEntity.getScale());
         matrices.scale(-1.0F, -1.0F, 1.0F);
         ((LivingEntityRendererAccessor)renderer).meteor$scale(livingEntity, matrices, event.tickDelta);
         matrices.translate(0.0, -1.501F, 0.0);
         if (model instanceof AgeableListModel m) {
            if (m.young) {
               matrices.pushPose();
               if (m.scaleHead) {
                  float g = 1.5F / m.babyHeadScale;
                  matrices.scale(g, g, g);
               }

               matrices.translate(0.0, (double)(m.babyYHeadOffset / 16.0F), (double)(m.babyZHeadOffset / 16.0F));
               if (model instanceof HumanoidModel mo) {
                  render(event.renderer, mo.head);
               } else {
                  ((AgeableListModelAccessor)m).meteor$headParts().forEach(modelPart -> render(event.renderer, (ModelPart)modelPart));
               }

               matrices.popPose();
               matrices.pushPose();
               float g = 1.0F / m.babyBodyScale;
               matrices.scale(g, g, g);
               matrices.translate(0.0, (double)(m.bodyYOffset / 16.0F), 0.0);
               if (model instanceof HumanoidModel mo) {
                  render(event.renderer, mo.body);
                  render(event.renderer, mo.leftArm);
                  render(event.renderer, mo.rightArm);
                  render(event.renderer, mo.leftLeg);
                  render(event.renderer, mo.rightLeg);
               } else {
                  ((AgeableListModelAccessor)m).meteor$bodyParts().forEach(modelPart -> render(event.renderer, (ModelPart)modelPart));
               }

               matrices.popPose();
            } else if (model instanceof HumanoidModel mo) {
               render(event.renderer, mo.head);
               render(event.renderer, mo.body);
               render(event.renderer, mo.leftArm);
               render(event.renderer, mo.rightArm);
               render(event.renderer, mo.leftLeg);
               render(event.renderer, mo.rightLeg);
            } else {
               ((AgeableListModelAccessor)m).meteor$headParts().forEach(modelPart -> render(event.renderer, (ModelPart)modelPart));
               ((AgeableListModelAccessor)m).meteor$bodyParts().forEach(modelPart -> render(event.renderer, (ModelPart)modelPart));
            }
         } else if (model instanceof HierarchicalModel mx) {
            render(event.renderer, mx.root());
         } else if (model instanceof ListModel mx) {
            mx.parts().forEach(modelPart -> render(event.renderer, (ModelPart)modelPart));
         } else if (model instanceof LlamaModel mx) {
            if (mx.young) {
               matrices.pushPose();
               matrices.scale(0.71428573F, 0.64935064F, 0.7936508F);
               matrices.translate(0.0, 1.3125, 0.22F);
               render(event.renderer, mx.head);
               matrices.popPose();
               matrices.pushPose();
               matrices.scale(0.625F, 0.45454544F, 0.45454544F);
               matrices.translate(0.0, 2.0625, 0.0);
               render(event.renderer, mx.body);
               matrices.popPose();
               matrices.pushPose();
               matrices.scale(0.45454544F, 0.41322312F, 0.45454544F);
               matrices.translate(0.0, 2.0625, 0.0);
               render(event.renderer, mx.rightHindLeg);
               render(event.renderer, mx.leftHindLeg);
               render(event.renderer, mx.rightFrontLeg);
               render(event.renderer, mx.leftFrontLeg);
               render(event.renderer, mx.rightChest);
               render(event.renderer, mx.leftChest);
               matrices.popPose();
            } else {
               render(event.renderer, mx.head);
               render(event.renderer, mx.body);
               render(event.renderer, mx.rightHindLeg);
               render(event.renderer, mx.leftHindLeg);
               render(event.renderer, mx.rightFrontLeg);
               render(event.renderer, mx.leftFrontLeg);
               render(event.renderer, mx.rightChest);
               render(event.renderer, mx.leftChest);
            }
         } else if (model instanceof RabbitModel mxx) {
            if (mxx.young) {
               matrices.pushPose();
               matrices.scale(0.56666666F, 0.56666666F, 0.56666666F);
               matrices.translate(0.0, 1.375, 0.125);
               render(event.renderer, mxx.head);
               render(event.renderer, mxx.leftEar);
               render(event.renderer, mxx.rightEar);
               render(event.renderer, mxx.nose);
               matrices.popPose();
               matrices.pushPose();
               matrices.scale(0.4F, 0.4F, 0.4F);
               matrices.translate(0.0, 2.25, 0.0);
               render(event.renderer, mxx.leftRearFoot);
               render(event.renderer, mxx.rightRearFoot);
               render(event.renderer, mxx.leftHaunch);
               render(event.renderer, mxx.rightHaunch);
               render(event.renderer, mxx.body);
               render(event.renderer, mxx.leftFrontLeg);
               render(event.renderer, mxx.rightFrontLeg);
               render(event.renderer, mxx.tail);
               matrices.popPose();
            } else {
               matrices.pushPose();
               matrices.scale(0.6F, 0.6F, 0.6F);
               matrices.translate(0.0, 1.0, 0.0);
               render(event.renderer, mxx.leftRearFoot);
               render(event.renderer, mxx.rightRearFoot);
               render(event.renderer, mxx.leftHaunch);
               render(event.renderer, mxx.rightHaunch);
               render(event.renderer, mxx.body);
               render(event.renderer, mxx.leftFrontLeg);
               render(event.renderer, mxx.rightFrontLeg);
               render(event.renderer, mxx.head);
               render(event.renderer, mxx.rightEar);
               render(event.renderer, mxx.leftEar);
               render(event.renderer, mxx.tail);
               render(event.renderer, mxx.nose);
               matrices.popPose();
            }
         }
      }

      if (entityRenderer instanceof EndCrystalRenderer renderer) {
         EndCrystal crystalEntity = (EndCrystal)entity;
         Chams chams = Modules.get().get(Chams.class);
         boolean chamsEnabled = chams.isActive() && chams.crystals.get();
         matrices.pushPose();
         float h;
         if (chamsEnabled) {
            float f = (float)crystalEntity.time + event.tickDelta;
            float g = Mth.sin(f * 0.2F) / 2.0F + 0.5F;
            g = (g * g + g) * 0.4F * chams.crystalsBounce.get().floatValue();
            h = g - 1.4F;
         } else {
            h = EndCrystalRenderer.getY(crystalEntity, event.tickDelta);
         }

         float j = ((float)crystalEntity.time + event.tickDelta) * 3.0F;
         matrices.pushPose();
         if (chamsEnabled) {
            matrices.scale(
               2.0F * chams.crystalsScale.get().floatValue(), 2.0F * chams.crystalsScale.get().floatValue(), 2.0F * chams.crystalsScale.get().floatValue()
            );
         } else {
            matrices.scale(2.0F, 2.0F, 2.0F);
         }

         matrices.translate(0.0, -0.5, 0.0);
         if (crystalEntity.showsBottom()) {
            render(event.renderer, renderer.base);
         }

         if (chamsEnabled) {
            matrices.mulPose(Axis.YP.rotationDegrees(j * chams.crystalsRotationSpeed.get().floatValue()));
         } else {
            matrices.mulPose(Axis.YP.rotationDegrees(j));
         }

         matrices.translate(0.0, (double)(1.5F + h / 2.0F), 0.0);
         matrices.mulPose(new Quaternionf().setAngleAxis(60.0F, EndCrystalRenderer.SIN_45, 0.0F, EndCrystalRenderer.SIN_45));
         if (!chamsEnabled || chams.renderFrame1.get()) {
            render(event.renderer, renderer.glass);
         }

         matrices.scale(0.875F, 0.875F, 0.875F);
         matrices.mulPose(new Quaternionf().setAngleAxis(60.0F, EndCrystalRenderer.SIN_45, 0.0F, EndCrystalRenderer.SIN_45));
         if (chamsEnabled) {
            matrices.mulPose(Axis.YP.rotationDegrees(j * chams.crystalsRotationSpeed.get().floatValue()));
         } else {
            matrices.mulPose(Axis.YP.rotationDegrees(j));
         }

         if (!chamsEnabled || chams.renderFrame2.get()) {
            render(event.renderer, renderer.glass);
         }

         matrices.scale(0.875F, 0.875F, 0.875F);
         matrices.mulPose(new Quaternionf().setAngleAxis(60.0F, EndCrystalRenderer.SIN_45, 0.0F, EndCrystalRenderer.SIN_45));
         if (chamsEnabled) {
            matrices.mulPose(Axis.YP.rotationDegrees(j * chams.crystalsRotationSpeed.get().floatValue()));
         } else {
            matrices.mulPose(Axis.YP.rotationDegrees(j));
         }

         if (!chamsEnabled || chams.renderCore.get()) {
            render(event.renderer, renderer.cube);
         }

         matrices.popPose();
         matrices.popPose();
      } else if (entityRenderer instanceof BoatRenderer renderer) {
         Boat boatEntity = (Boat)entity;
         matrices.pushPose();
         matrices.translate(0.0, 0.375, 0.0);
         matrices.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.lerp(event.tickDelta, entity.yRotO, entity.getYRot())));
         float hx = (float)boatEntity.getHurtTime() - event.tickDelta;
         float jx = boatEntity.getDamage() - event.tickDelta;
         if (jx < 0.0F) {
            jx = 0.0F;
         }

         if (hx > 0.0F) {
            matrices.mulPose(Axis.XP.rotationDegrees(Mth.sin(hx) * hx * jx / 10.0F * (float)boatEntity.getHurtDir()));
         }

         float k = boatEntity.getBubbleAngle(event.tickDelta);
         if (!Mth.equal(k, 0.0F)) {
            matrices.mulPose(new Quaternionf().setAngleAxis(boatEntity.getBubbleAngle(event.tickDelta), 1.0F, 0.0F, 1.0F));
         }

         ListModel<Boat> boatEntityModel = (ListModel<Boat>)((Pair)renderer.boatResources.get(boatEntity.getVariant())).getSecond();
         matrices.scale(-1.0F, -1.0F, 1.0F);
         matrices.mulPose(Axis.YP.rotationDegrees(90.0F));
         boatEntityModel.setupAnim(boatEntity, event.tickDelta, 0.0F, -0.1F, 0.0F, 0.0F);
         boatEntityModel.parts().forEach(modelPart -> render(event.renderer, modelPart));
         if (!boatEntity.isUnderWater() && boatEntityModel instanceof WaterPatchModel modelWithWaterPatch) {
            render(event.renderer, modelWithWaterPatch.waterPatch());
         }

         matrices.popPose();
      } else if (entityRenderer instanceof ItemEntityRenderer) {
         double dx = (entity.getX() - entity.xo) * (double)event.tickDelta;
         double dy = (entity.getY() - entity.yo) * (double)event.tickDelta;
         double dz = (entity.getZ() - entity.zo) * (double)event.tickDelta;
         AABB box = entity.getBoundingBox();
         event.renderer.box(dx + box.minX, dy + box.minY, dz + box.minZ, dx + box.maxX, dy + box.maxY, dz + box.maxZ, sideColor, lineColor, shapeMode, 0);
      }

      matrices.popPose();
   }

   private static void render(Renderer3D renderer, ModelPart part) {
      if (part.visible && (!part.cubes.isEmpty() || !part.children.isEmpty())) {
         matrices.pushPose();
         part.translateAndRotate(matrices);

         for (Cube cuboid : part.cubes) {
            render(renderer, cuboid, offsetX, offsetY, offsetZ);
         }

         for (ModelPart child : part.children.values()) {
            render(renderer, child);
         }

         matrices.popPose();
      }
   }

   private static void render(Renderer3D renderer, Cube cuboid, double offsetX, double offsetY, double offsetZ) {
      Matrix4f matrix = matrices.last().pose();

      for (Polygon quad : cuboid.polygons) {
         pos1.set(quad.vertices[0].pos.x / 16.0F, quad.vertices[0].pos.y / 16.0F, quad.vertices[0].pos.z / 16.0F, 1.0F);
         pos1.mul(matrix);
         pos2.set(quad.vertices[1].pos.x / 16.0F, quad.vertices[1].pos.y / 16.0F, quad.vertices[1].pos.z / 16.0F, 1.0F);
         pos2.mul(matrix);
         pos3.set(quad.vertices[2].pos.x / 16.0F, quad.vertices[2].pos.y / 16.0F, quad.vertices[2].pos.z / 16.0F, 1.0F);
         pos3.mul(matrix);
         pos4.set(quad.vertices[3].pos.x / 16.0F, quad.vertices[3].pos.y / 16.0F, quad.vertices[3].pos.z / 16.0F, 1.0F);
         pos4.mul(matrix);
         if (shapeMode.sides()) {
            renderer.triangles
               .quad(
                  renderer.triangles.vec3(offsetX + (double)pos1.x, offsetY + (double)pos1.y, offsetZ + (double)pos1.z).color(sideColor).next(),
                  renderer.triangles.vec3(offsetX + (double)pos2.x, offsetY + (double)pos2.y, offsetZ + (double)pos2.z).color(sideColor).next(),
                  renderer.triangles.vec3(offsetX + (double)pos3.x, offsetY + (double)pos3.y, offsetZ + (double)pos3.z).color(sideColor).next(),
                  renderer.triangles.vec3(offsetX + (double)pos4.x, offsetY + (double)pos4.y, offsetZ + (double)pos4.z).color(sideColor).next()
               );
         }

         if (shapeMode.lines()) {
            renderer.line(
               offsetX + (double)pos1.x,
               offsetY + (double)pos1.y,
               offsetZ + (double)pos1.z,
               offsetX + (double)pos2.x,
               offsetY + (double)pos2.y,
               offsetZ + (double)pos2.z,
               lineColor
            );
            renderer.line(
               offsetX + (double)pos2.x,
               offsetY + (double)pos2.y,
               offsetZ + (double)pos2.z,
               offsetX + (double)pos3.x,
               offsetY + (double)pos3.y,
               offsetZ + (double)pos3.z,
               lineColor
            );
            renderer.line(
               offsetX + (double)pos3.x,
               offsetY + (double)pos3.y,
               offsetZ + (double)pos3.z,
               offsetX + (double)pos4.x,
               offsetY + (double)pos4.y,
               offsetZ + (double)pos4.z,
               lineColor
            );
            renderer.line(
               offsetX + (double)pos1.x,
               offsetY + (double)pos1.y,
               offsetZ + (double)pos1.z,
               offsetX + (double)pos1.x,
               offsetY + (double)pos1.y,
               offsetZ + (double)pos1.z,
               lineColor
            );
         }
      }
   }
}
