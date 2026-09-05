package meteordevelopment.meteorclient.systems.proxies;

import java.net.InetSocketAddress;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class Proxy implements ISerializable<Proxy> {
   public final Settings settings = new Settings();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgOptional = this.settings.createGroup("Optional");
   public Setting<String> name = this.sgGeneral.add(new StringSetting.Builder().name("name").description("The name of the proxy.").build());
   public Setting<ProxyType> type = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("type")).description("The type of proxy."))
               .defaultValue(ProxyType.Socks5))
            .build()
      );
   public Setting<String> address = this.sgGeneral
      .add(new StringSetting.Builder().name("address").description("The ip address of the proxy.").filter(Utils::ipFilter).build());
   public Setting<Integer> port = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("port")
            .description("The port of the proxy.")
            .defaultValue(Integer.valueOf(0))
            .range(0, 65535)
            .sliderMax(65535)
            .noSlider()
            .build()
      );
   public Setting<Boolean> enabled = this.sgGeneral
      .add(new BoolSetting.Builder().name("enabled").description("Whether the proxy is enabled.").defaultValue(Boolean.valueOf(true)).build());
   public Setting<String> username = this.sgOptional.add(new StringSetting.Builder().name("username").description("The username of the proxy.").build());
   public Setting<String> password = this.sgOptional
      .add(
         new StringSetting.Builder().name("password").description("The password of the proxy.").visible(() -> this.type.get().equals(ProxyType.Socks5)).build()
      );

   private Proxy() {
   }

   public Proxy(Tag tag) {
      this.fromTag((CompoundTag)tag);
   }

   public boolean resolveAddress() {
      int port = this.port.get();
      String address = this.address.get();
      if (port > 0 && port <= 65535 && address != null && !address.isBlank()) {
         InetSocketAddress socketAddress = new InetSocketAddress(address, port);
         return !socketAddress.isUnresolved();
      } else {
         return false;
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.put("settings", this.settings.toTag());
      return tag;
   }

   public Proxy fromTag(CompoundTag tag) {
      if (tag.contains("settings")) {
         this.settings.fromTag(tag.getCompound("settings"));
      }

      return this;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Proxy proxy = (Proxy)o;
         return Objects.equals(proxy.address.get(), this.address.get()) && Objects.equals(proxy.port.get(), this.port.get());
      } else {
         return false;
      }
   }

   public static class Builder {
      protected ProxyType type = ProxyType.Socks5;
      protected String address = "";
      protected int port = 0;
      protected String name = "";
      protected String username = "";
      protected boolean enabled = false;

      public Proxy.Builder type(ProxyType type) {
         this.type = type;
         return this;
      }

      public Proxy.Builder address(String address) {
         this.address = address;
         return this;
      }

      public Proxy.Builder port(int port) {
         this.port = port;
         return this;
      }

      public Proxy.Builder name(String name) {
         this.name = name;
         return this;
      }

      public Proxy.Builder username(String username) {
         this.username = username;
         return this;
      }

      public Proxy.Builder enabled(boolean enabled) {
         this.enabled = enabled;
         return this;
      }

      public Proxy build() {
         Proxy proxy = new Proxy();
         if (!this.type.equals(proxy.type.getDefaultValue())) {
            proxy.type.set(this.type);
         }

         if (!this.address.equals(proxy.address.getDefaultValue())) {
            proxy.address.set(this.address);
         }

         if (this.port != proxy.port.getDefaultValue()) {
            proxy.port.set(this.port);
         }

         if (!this.name.equals(proxy.name.getDefaultValue())) {
            proxy.name.set(this.name);
         }

         if (!this.username.equals(proxy.username.getDefaultValue())) {
            proxy.username.set(this.username);
         }

         if (this.enabled != proxy.enabled.getDefaultValue()) {
            proxy.enabled.set(this.enabled);
         }

         return proxy;
      }
   }
}
