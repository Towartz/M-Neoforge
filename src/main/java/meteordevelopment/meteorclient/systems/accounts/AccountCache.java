package meteordevelopment.meteorclient.systems.accounts;

import com.mojang.util.UndashedUuid;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.misc.NbtException;
import meteordevelopment.meteorclient.utils.render.PlayerHeadTexture;
import meteordevelopment.meteorclient.utils.render.PlayerHeadUtils;
import net.minecraft.nbt.CompoundTag;

public class AccountCache implements ISerializable<AccountCache> {
   public String username = "";
   public String uuid = "";
   private PlayerHeadTexture headTexture;

   public PlayerHeadTexture getHeadTexture() {
      return this.headTexture != null ? this.headTexture : PlayerHeadUtils.STEVE_HEAD;
   }

   public void loadHead() {
      if (this.uuid != null && !this.uuid.isBlank()) {
         this.headTexture = PlayerHeadUtils.fetchHead(UndashedUuid.fromStringLenient(this.uuid));
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("username", this.username);
      tag.putString("uuid", this.uuid);
      return tag;
   }

   public AccountCache fromTag(CompoundTag tag) {
      if (tag.contains("username") && tag.contains("uuid")) {
         this.username = tag.getString("username");
         this.uuid = tag.getString("uuid");
         this.loadHead();
         return this;
      } else {
         throw new NbtException();
      }
   }
}
