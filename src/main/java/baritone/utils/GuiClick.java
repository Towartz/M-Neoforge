package baritone.utils;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.command.IBaritoneChatControl;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.Collections;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class GuiClick extends Screen implements Helper {
   private Matrix4f projectionViewMatrix;
   private BlockPos clickStart;
   private BlockPos currentMouseOver;

   public GuiClick() {
      super(Component.literal("CLICK"));
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void render(GuiGraphics stack, int mouseX, int mouseY, float partialTicks) {
      double mx = mc.mouseHandler.xpos();
      double my = mc.mouseHandler.ypos();
      my = (double)mc.getWindow().getScreenHeight() - my;
      my *= (double)mc.getWindow().getHeight() / (double)mc.getWindow().getScreenHeight();
      mx *= (double)mc.getWindow().getWidth() / (double)mc.getWindow().getScreenWidth();
      Vec3 near = this.toWorld(mx, my, 0.0);
      Vec3 far = this.toWorld(mx, my, 1.0);
      if (near != null && far != null) {
         Vec3 viewerPos = new Vec3(PathRenderer.posX(), PathRenderer.posY(), PathRenderer.posZ());
         LocalPlayer player = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().player();
         HitResult result = player.level().clip(new ClipContext(near.add(viewerPos), far.add(viewerPos), Block.OUTLINE, Fluid.NONE, player));
         if (result != null && result.getType() == Type.BLOCK) {
            this.currentMouseOver = ((BlockHitResult)result).getBlockPos();
         }
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
      if (this.currentMouseOver != null) {
         if (mouseButton == 0) {
            if (this.clickStart != null && !this.clickStart.equals(this.currentMouseOver)) {
               BaritoneAPI.getProvider().getPrimaryBaritone().getSelectionManager().removeAllSelections();
               BaritoneAPI.getProvider()
                  .getPrimaryBaritone()
                  .getSelectionManager()
                  .addSelection(BetterBlockPos.from(this.clickStart), BetterBlockPos.from(this.currentMouseOver));
               MutableComponent component = Component.literal("Selection made! For usage: " + Baritone.settings().prefix.value + "help sel");
               component.setStyle(
                  component.getStyle()
                     .withColor(ChatFormatting.WHITE)
                     .withClickEvent(new ClickEvent(Action.RUN_COMMAND, IBaritoneChatControl.FORCE_COMMAND_PREFIX + "help sel"))
               );
               Helper.HELPER.logDirect(component);
               this.clickStart = null;
            } else {
               BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.currentMouseOver));
            }
         } else if (mouseButton == 1) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.currentMouseOver.above()));
         }
      }

      this.clickStart = null;
      return super.mouseReleased(mouseX, mouseY, mouseButton);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
      this.clickStart = this.currentMouseOver;
      return super.mouseClicked(mouseX, mouseY, mouseButton);
   }

   public void onRender(PoseStack modelViewStack, Matrix4f projectionMatrix) {
      this.projectionViewMatrix = new Matrix4f(projectionMatrix);
      this.projectionViewMatrix.mul(modelViewStack.last().pose());
      this.projectionViewMatrix.invert();
      if (this.currentMouseOver != null) {
         Entity e = mc.getCameraEntity();
         PathRenderer.drawManySelectionBoxes(modelViewStack, e, Collections.singletonList(this.currentMouseOver), Color.CYAN);
         if (this.clickStart != null && !this.clickStart.equals(this.currentMouseOver)) {
            BufferBuilder bufferBuilder = IRenderer.startLines(Color.RED, Baritone.settings().pathRenderLineWidthPixels.value, true);
            BetterBlockPos a = new BetterBlockPos(this.currentMouseOver);
            BetterBlockPos b = new BetterBlockPos(this.clickStart);
            IRenderer.emitAABB(
               bufferBuilder,
               modelViewStack,
               new AABB(
                  (double)Math.min(a.x, b.x),
                  (double)Math.min(a.y, b.y),
                  (double)Math.min(a.z, b.z),
                  (double)(Math.max(a.x, b.x) + 1),
                  (double)(Math.max(a.y, b.y) + 1),
                  (double)(Math.max(a.z, b.z) + 1)
               )
            );
            IRenderer.endLines(bufferBuilder, true);
         }
      }
   }

   private Vec3 toWorld(double x, double y, double z) {
      if (this.projectionViewMatrix == null) {
         return null;
      } else {
         x /= (double)mc.getWindow().getWidth();
         y /= (double)mc.getWindow().getHeight();
         x = x * 2.0 - 1.0;
         y = y * 2.0 - 1.0;
         Vector4f pos = new Vector4f((float)x, (float)y, (float)z, 1.0F);
         this.projectionViewMatrix.transform(pos);
         if (pos.w() == 0.0F) {
            return null;
         } else {
            pos.mul(1.0F / pos.w());
            return new Vec3((double)pos.x(), (double)pos.y(), (double)pos.z());
         }
      }
   }
}
