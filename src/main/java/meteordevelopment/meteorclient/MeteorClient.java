package meteordevelopment.meteorclient;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.DiscordPresence;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.ReflectInit;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Version;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.misc.input.KeyBinds;
import meteordevelopment.meteorclient.utils.network.OnlinePlayers;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MeteorClient.MOD_ID)
public class MeteorClient {
   public static final String MOD_ID = "meteor_client";
   public static final String NAME = "Meteor Client";
   public static final Version VERSION = new Version("0.5.8");
   public static final String DEV_BUILD = "";
   public static MeteorClient INSTANCE;
   public static MeteorAddon ADDON;
   public static Minecraft mc;
   public static final IEventBus EVENT_BUS = new EventBus();
   public static final File FOLDER = FMLPaths.GAMEDIR.get().resolve("meteor-client").toFile();
   public static final Logger LOG = LoggerFactory.getLogger(NAME);
   private static boolean initialized = false;
   private boolean wasWidgetScreen;
   private boolean wasHudHiddenRoot;

   public MeteorClient(net.neoforged.bus.api.IEventBus modEventBus) {
      INSTANCE = this;
      modEventBus.addListener(this::onClientSetup);
   }

   public void onClientSetup(FMLClientSetupEvent event) {
   }

   public void onInitializeClient() {
      init();
   }

   public synchronized void init() {
      if (initialized) {
         LOG.warn("MeteorClient.init() called more than once! Ignoring duplicate initialization.");
         return;
      }
      initialized = true;

      LOG.info("Initializing {}", NAME);
      mc = Minecraft.getInstance();
         if (!FOLDER.exists()) {
            FOLDER.getParentFile().mkdirs();
            FOLDER.mkdir();
            Systems.addPreLoadTask(() -> Modules.get().get(DiscordPresence.class).toggle());
         }

         AddonManager.init();
         AddonManager.ADDONS
            .forEach(
               addon -> {
                  try {
                     EVENT_BUS.registerLambdaFactory(
                        addon.getPackage(), (lookupInMethod, klass) -> (Lookup)lookupInMethod.invoke(null, klass, MethodHandles.lookup())
                     );
                  } catch (AbstractMethodError var2) {
                     throw new RuntimeException("Addon \"%s\" is too old and cannot be ran.".formatted(addon.name), var2);
                  }
               }
            );
         ReflectInit.registerPackages();
         ReflectInit.init(PreInit.class);
         Categories.init();
         Systems.init();
         EVENT_BUS.subscribe(this);
         AddonManager.ADDONS.forEach(MeteorAddon::onInitialize);
         Modules.get().sortModules();
         Systems.load();
         ReflectInit.init(PostInit.class);
         Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            OnlinePlayers.leave();
            Systems.save();
            GuiThemes.save();
         }));
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (mc.screen == null && mc.getOverlay() == null && KeyBinds.OPEN_COMMANDS.consumeClick()) {
         mc.setScreen(new ChatScreen(Config.get().prefix.get()));
      }
   }

   private long lastToggleGuiTime = 0L;

   @EventHandler
   private void onKey(KeyEvent event) {
      if (event.action == KeyAction.Press && KeyBinds.OPEN_GUI.matches(event.key, 0)) {
         this.toggleGui();
         event.cancel();
      }
   }

   @EventHandler
   private void onMouseButton(MouseButtonEvent event) {
      if (event.action == KeyAction.Press && KeyBinds.OPEN_GUI.matchesMouse(event.button)) {
         this.toggleGui();
         event.cancel();
      }
   }

   private void toggleGui() {
      long now = System.currentTimeMillis();
      if (now - this.lastToggleGuiTime < 250L) {
         return;
      }
      this.lastToggleGuiTime = now;

      if (Utils.canCloseGui()) {
         mc.screen.onClose();
      } else if (Utils.canOpenGui()) {
         Tabs.get().getFirst().openScreen(GuiThemes.get());
      }
   }

   @EventHandler(
      priority = -200
   )
   private void onOpenScreen(OpenScreenEvent event) {
      boolean hideHud = GuiThemes.get().hideHUD();
      if (hideHud) {
         if (!this.wasWidgetScreen) {
            this.wasHudHiddenRoot = mc.options.hideGui;
         }

         if (event.screen instanceof WidgetScreen) {
            mc.options.hideGui = true;
         } else if (!this.wasHudHiddenRoot) {
            mc.options.hideGui = false;
         }
      }

      this.wasWidgetScreen = event.screen instanceof WidgetScreen;
   }

   public static ResourceLocation identifier(String path) {
      return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
   }


}
