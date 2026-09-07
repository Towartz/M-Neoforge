package meteordevelopment.meteorclient.systems.modules.combat.plus;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class Teams extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> scoreBoardTeam = this.sgGeneral.add(new BoolSetting.Builder().name("Scoreboard-Team").defaultValue(true).build());
    private final Setting<Boolean> colorTeam = this.sgGeneral.add(new BoolSetting.Builder().name("color").defaultValue(true).build());
    private final Setting<Boolean> gommeSkyWars = this.sgGeneral.add(new BoolSetting.Builder().name("GommeHD-SkyWars").defaultValue(false).build());

    public Teams() {
        super(Categories.PlusCombat, "teams", "Checks if target entity is in your team using scoreboard, color, or prefix.");
    }

    public boolean isInYourTeam(Entity entity) {
        return entity instanceof LivingEntity living ? this.isInYourTeam(living) : false;
    }

    public boolean isInYourTeam(LivingEntity entity) {
        LocalPlayer player = this.mc.player;
        if (player == null || !this.isActive()) return false;

        if (this.scoreBoardTeam.get() && player.getTeam() != null && entity.getTeam() != null && player.isAlliedTo(entity.getTeam())) {
            return true;
        }

        Component displayName = player.getName();
        if (displayName != null && entity.getName() != null) {
            String targetName = entity.getName().getString().replaceAll("§r", "");
            String clientName = displayName.getString().replaceAll("§r", "");

            if (this.gommeSkyWars.get() && targetName.length() >= 2 && clientName.length() >= 2) {
                if (targetName.startsWith("T") && clientName.startsWith("T") && Character.isDigit(targetName.charAt(1)) && Character.isDigit(clientName.charAt(1))) {
                    return targetName.charAt(1) == clientName.charAt(1);
                }
            }

            if (this.colorTeam.get() && clientName.length() >= 2) {
                return targetName.startsWith("§" + clientName.charAt(1));
            }
        }

        return false;
    }
}
