package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.Window;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.ItemUseCrosshairTargetEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.game.ResolutionChangedEvent;
import meteordevelopment.meteorclient.events.game.ResourcePacksReloadedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.mixininterface.IMinecraftClient;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.FastUse;
import meteordevelopment.meteorclient.systems.modules.player.Multitask;
import meteordevelopment.meteorclient.systems.modules.render.UnfocusedCPU;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.CPSUtils;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.network.OnlinePlayers;
import meteordevelopment.starscript.Script;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(
   value = {Minecraft.class},
   priority = 1001
)
public abstract class MinecraftClientMixin implements IMinecraftClient {
   @Unique
   private boolean doItemUseCalled;
   @Unique
   private boolean rightClick;
   @Unique
   private long lastTime;
   @Unique
   private boolean firstFrame;
   @Shadow
   public ClientLevel level;
   @Shadow
   @Final
   public MouseHandler mouseHandler;
   @Shadow
   @Final
   private Window window;
   @Shadow
   public Screen screen;
   @Shadow
   @Final
   public Options options;
   @Shadow
   @Nullable
   public MultiPlayerGameMode gameMode;
   @Shadow
   private int rightClickDelay;
   @Shadow
   @Nullable
   public LocalPlayer player;

   @Shadow
   protected abstract void startUseItem();

   @Shadow
   public abstract ProfilerFiller getProfiler();

   @Shadow
   public abstract boolean isWindowActive();

   @Inject(
      method = {"<init>(Lnet/minecraft/client/main/GameConfig;)V"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo info) {
      MeteorClient.INSTANCE.onInitializeClient();
      this.firstFrame = true;
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"tick"}
   )
   private void onPreTick(CallbackInfo info) {
      OnlinePlayers.update();
      this.doItemUseCalled = false;
      this.getProfiler().push("meteor-client_pre_update");
      MeteorClient.EVENT_BUS.post(TickEvent.Pre.get());
      this.getProfiler().pop();
      if (this.rightClick && !this.doItemUseCalled && this.gameMode != null) {
         this.startUseItem();
      }

      this.rightClick = false;
   }

   @Inject(
      at = {@At("TAIL")},
      method = {"tick"}
   )
   private void onTick(CallbackInfo info) {
      this.getProfiler().push("meteor-client_post_update");
      MeteorClient.EVENT_BUS.post(TickEvent.Post.get());
      this.getProfiler().pop();
   }

   @Inject(
      method = {"doAttack"},
      at = {@At("HEAD")}
   )
   private void onAttack(CallbackInfoReturnable<Boolean> cir) {
      CPSUtils.onAttack();
   }

   @Inject(
      method = {"doItemUse"},
      at = {@At("HEAD")}
   )
   private void onDoItemUse(CallbackInfo info) {
      this.doItemUseCalled = true;
      FastUse fastUse = Modules.get().get(FastUse.class);
      if (fastUse != null && fastUse.isActive() && this.player != null) {
         ItemStack stack = this.player.getMainHandItem();
         if (stack == null || stack.isEmpty()) stack = this.player.getOffhandItem();
         if (stack != null) {
            this.rightClickDelay = fastUse.getItemUseCooldown(stack);
         }
      }
   }

   @Inject(
      method = {"disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", "disconnect(Lnet/minecraft/client/gui/screens/Screen;)V", "clearClientLevel"},
      at = {@At("HEAD")}
   )
   private void onDisconnect(CallbackInfo info) {
      if (this.level != null) {
         MeteorClient.EVENT_BUS.post(GameLeftEvent.get());
      }
   }

   @Inject(
      method = {"setScreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSetScreen(Screen screen, CallbackInfo info) {
      if (screen instanceof WidgetScreen) {
         screen.mouseMoved(this.mouseHandler.xpos() * this.window.getGuiScale(), this.mouseHandler.ypos() * this.window.getGuiScale());
      }

      OpenScreenEvent event = OpenScreenEvent.get(screen);
      MeteorClient.EVENT_BUS.post(event);
      if (event.isCancelled()) {
         info.cancel();
      }
   }

   @ModifyExpressionValue(
      method = {"doItemUse"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;",
         ordinal = 1
      )}
   )
   private HitResult doItemUseMinecraftClientCrosshairTargetProxy(HitResult original) {
      return MeteorClient.EVENT_BUS.post(ItemUseCrosshairTargetEvent.get(original)).target;
   }

   @ModifyReturnValue(
      method = {"reloadResources(ZLnet/minecraft/client/MinecraftClient$LoadingContext;)Ljava/util/concurrent/CompletableFuture;"},
      at = {@At("RETURN")}
   )
   private CompletableFuture<Void> onReloadResourcesNewCompletableFuture(CompletableFuture<Void> original) {
      return original.thenRun(() -> MeteorClient.EVENT_BUS.post(ResourcePacksReloadedEvent.get()));
   }

   @ModifyArg(
      method = {"updateWindowTitle"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/util/Window;setTitle(Ljava/lang/String;)V"
      )
   )
   private String setTitle(String original) {
      if (Config.get() != null && Config.get().customWindowTitle.get()) {
         String customTitle = Config.get().customWindowTitleText.get();
         Script script = MeteorStarscript.compile(customTitle);
         if (script != null) {
            String title = MeteorStarscript.run(script);
            if (title != null) {
               customTitle = title;
            }
         }

         return customTitle;
      } else {
         return original;
      }
   }

   @Inject(
      method = {"onResolutionChanged"},
      at = {@At("TAIL")}
   )
   private void onResolutionChanged(CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(ResolutionChangedEvent.get());
   }

   @Inject(
      method = {"getFramerateLimit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetFramerateLimit(CallbackInfoReturnable<Integer> info) {
      if (Modules.get().isActive(UnfocusedCPU.class) && !this.isWindowActive()) {
         info.setReturnValue(Math.min(Modules.get().get(UnfocusedCPU.class).fps.get(), (Integer)this.options.framerateLimit().get()));
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("HEAD")}
   )
   private void onRender(CallbackInfo info) {
      long time = System.currentTimeMillis();
      if (this.firstFrame) {
         this.lastTime = time;
         this.firstFrame = false;
      }

      Utils.frameTime = (double)(time - this.lastTime) / 1000.0;
      this.lastTime = time;
   }

   @ModifyExpressionValue(
      method = {"doItemUse"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;isBreakingBlock()Z"
      )}
   )
   private boolean doItemUseModifyIsBreakingBlock(boolean original) {
      return !Modules.get().isActive(Multitask.class) && original;
   }

   @ModifyExpressionValue(
      method = {"handleBlockBreaking"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"
      )}
   )
   private boolean handleBlockBreakingModifyIsUsingItem(boolean original) {
      return !Modules.get().isActive(Multitask.class) && original;
   }

   @ModifyExpressionValue(
      method = {"handleInputEvents"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
         ordinal = 0
      )}
   )
   private boolean handleInputEventsModifyIsUsingItem(boolean original) {
      return !Modules.get().get(Multitask.class).attackingEntities() && original;
   }

   @Inject(
      method = {"handleInputEvents"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
         ordinal = 0,
         shift = Shift.BEFORE
      )}
   )
   private void handleInputEventsInjectStopUsingItem(CallbackInfo info) {
      if (Modules.get().get(Multitask.class).attackingEntities() && this.player.isUsingItem()) {
         if (!this.options.keyUse.isDown()) {
            this.gameMode.releaseUsingItem(this.player);
         }

         while (this.options.keyUse.consumeClick()) {
         }
      }
   }

   @Override
   public void meteor_client$rightClick() {
      this.rightClick = true;
   }
}
