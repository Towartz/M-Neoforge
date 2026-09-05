package meteordevelopment.meteorclient.systems.config;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.FontFaceSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public class Config extends System<Config> {
   public final Settings settings = new Settings();
   private final SettingGroup sgVisual = this.settings.createGroup("Visual");
   private final SettingGroup sgChat = this.settings.createGroup("Chat");
   private final SettingGroup sgMisc = this.settings.createGroup("Misc");
   public final Setting<Boolean> customFont = this.sgVisual
      .add(new BoolSetting.Builder().name("custom-font").description("Use a custom font.").defaultValue(Boolean.valueOf(true)).build());
   public final Setting<FontFace> font = this.sgVisual
      .add(new FontFaceSetting.Builder().name("font").description("Custom font to use.").visible(this.customFont::get).onChanged(Fonts::load).build());
   public final Setting<Double> rainbowSpeed = this.sgVisual
      .add(new DoubleSetting.Builder().name("rainbow-speed").description("The global rainbow speed.").defaultValue(0.5).range(0.0, 10.0).sliderMax(5.0).build());
   public final Setting<Boolean> titleScreenCredits = this.sgVisual
      .add(
         new BoolSetting.Builder().name("title-screen-credits").description("Show Meteor credits on title screen").defaultValue(Boolean.valueOf(true)).build()
      );
   public final Setting<Boolean> titleScreenSplashes = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("title-screen-splashes")
            .description("Show Meteor splash texts on title screen")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public final Setting<Boolean> customWindowTitle = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("custom-window-title")
            .description("Show custom text in the window title.")
            .defaultValue(Boolean.valueOf(false))
            .onModuleActivated(setting -> MeteorClient.mc.updateTitle())
            .onChanged(value -> MeteorClient.mc.updateTitle())
            .build()
      );
   public final Setting<String> customWindowTitleText = this.sgVisual
      .add(
         new StringSetting.Builder()
            .name("window-title-text")
            .description("The text it displays in the window title.")
            .visible(this.customWindowTitle::get)
            .defaultValue("Minecraft {mc_version} - {meteor.name} {meteor.version}")
            .onChanged(value -> MeteorClient.mc.updateTitle())
            .build()
      );
   public final Setting<SettingColor> friendColor = this.sgVisual
      .add(new ColorSetting.Builder().name("friend-color").description("The color used to show friends.").defaultValue(new SettingColor(0, 255, 180)).build());
   public final Setting<String> prefix = this.sgChat.add(new StringSetting.Builder().name("prefix").description("Prefix.").defaultValue(".").build());
   public final Setting<Boolean> chatFeedback = this.sgChat
      .add(
         new BoolSetting.Builder()
            .name("chat-feedback")
            .description("Sends chat feedback when meteor performs certain actions.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public final Setting<Boolean> deleteChatFeedback = this.sgChat
      .add(
         new BoolSetting.Builder()
            .name("delete-chat-feedback")
            .description("Delete previous matching chat feedback to keep chat clear.")
            .visible(this.chatFeedback::get)
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public final Setting<Integer> rotationHoldTicks = this.sgMisc
      .add(
         new IntSetting.Builder()
            .name("rotation-hold")
            .description("Hold long to hold server side rotation when not sending any packets.")
            .defaultValue(Integer.valueOf(4))
            .build()
      );
   public final Setting<Boolean> useTeamColor = this.sgMisc
      .add(
         new BoolSetting.Builder()
            .name("use-team-color")
            .description("Uses player's team color for rendering things like esp and tracers.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public final Setting<Integer> moduleSearchCount = this.sgMisc
      .add(
         new IntSetting.Builder()
            .name("module-search-count")
            .description("Amount of modules and settings to be shown in the module search bar.")
            .defaultValue(Integer.valueOf(8))
            .min(1)
            .sliderMax(12)
            .build()
      );
   public final Setting<Boolean> moduleAliases = this.sgMisc
      .add(
         new BoolSetting.Builder()
            .name("search-module-aliases")
            .description("Whether or not module aliases will be used in the module search bar.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public List<String> dontShowAgainPrompts = new ArrayList<>();

   public Config() {
      super("config");
   }

   public static Config get() {
      return Systems.get(Config.class);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("version", MeteorClient.VERSION.toString());
      tag.put("settings", this.settings.toTag());
      tag.put("dontShowAgainPrompts", this.listToTag(this.dontShowAgainPrompts));
      return tag;
   }

   public Config fromTag(CompoundTag tag) {
      if (tag.contains("settings")) {
         this.settings.fromTag(tag.getCompound("settings"));
      }

      if (tag.contains("dontShowAgainPrompts")) {
         this.dontShowAgainPrompts = this.listFromTag(tag, "dontShowAgainPrompts");
      }

      return this;
   }

   private ListTag listToTag(List<String> list) {
      ListTag nbt = new ListTag();

      for (String item : list) {
         nbt.add(StringTag.valueOf(item));
      }

      return nbt;
   }

   private List<String> listFromTag(CompoundTag tag, String key) {
      List<String> list = new ArrayList<>();

      for (Tag item : tag.getList(key, 8)) {
         list.add(item.getAsString());
      }

      return list;
   }
}
