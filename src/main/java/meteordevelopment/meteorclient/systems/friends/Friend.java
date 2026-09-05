package meteordevelopment.meteorclient.systems.friends;

import com.mojang.util.UndashedUuid;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.render.PlayerHeadTexture;
import meteordevelopment.meteorclient.utils.render.PlayerHeadUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class Friend implements ISerializable<Friend>, Comparable<Friend> {
   public volatile String name;
   @Nullable
   private volatile UUID id;
   @Nullable
   private volatile PlayerHeadTexture headTexture;
   private volatile boolean updating;

   public Friend(String name, @Nullable UUID id) {
      this.name = name;
      this.id = id;
      this.headTexture = null;
   }

   public Friend(Player player) {
      this(player.getName().getString(), player.getUUID());
   }

   public Friend(String name) {
      this(name, null);
   }

   public String getName() {
      return this.name;
   }

   public PlayerHeadTexture getHead() {
      return this.headTexture != null ? this.headTexture : PlayerHeadUtils.STEVE_HEAD;
   }

   public void updateInfo() {
      this.updating = true;
      Friend.APIResponse res = Http.get("https://api.mojang.com/users/profiles/minecraft/" + this.name).sendJson(Friend.APIResponse.class);
      if (res != null && res.name != null && res.id != null) {
         this.name = res.name;
         this.id = UndashedUuid.fromStringLenient(res.id);
         this.headTexture = PlayerHeadUtils.fetchHead(this.id);
         this.updating = false;
      }
   }

   public boolean headTextureNeedsUpdate() {
      return !this.updating && this.headTexture == null;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("name", this.name);
      if (this.id != null) {
         tag.putString("id", UndashedUuid.toString(this.id));
      }

      return tag;
   }

   public Friend fromTag(CompoundTag tag) {
      return this;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Friend friend = (Friend)o;
         return Objects.equals(this.name, friend.name);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.name);
   }

   public int compareTo(@NotNull Friend friend) {
      return this.name.compareTo(friend.name);
   }

   private static class APIResponse {
      String name;
      String id;
   }
}
