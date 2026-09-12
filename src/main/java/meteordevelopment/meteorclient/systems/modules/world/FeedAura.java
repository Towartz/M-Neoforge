package meteordevelopment.meteorclient.systems.modules.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class FeedAura extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgFilters = this.settings.createGroup("Filters");
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Double> range = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far to reach for feeding animals.")
        .defaultValue(4.5)
        .min(1.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Integer> delay = this.sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between feedings in ticks.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> rotate = this.sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Smoothly rotates to the animal before feeding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterBabies = this.sgFilters.add(new BoolSetting.Builder()
        .name("filter-babies")
        .description("Won't feed baby animals (saves food).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterUntamed = this.sgFilters.add(new BoolSetting.Builder()
        .name("filter-untamed")
        .description("Won't feed untamed tameable animals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterHorses = this.sgFilters.add(new BoolSetting.Builder()
        .name("filter-horses")
        .description("Won't feed horse-like animals.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> render = this.sgRender.add(new BoolSetting.Builder().name("render").description("Renders bounding box around target animal.").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = this.sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").description("How the shape is rendered.").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = this.sgRender.add(new ColorSetting.Builder().name("side-color").description("Side color.").defaultValue(new SettingColor(255, 170, 0, 25)).build());
    private final Setting<SettingColor> lineColor = this.sgRender.add(new ColorSetting.Builder().name("line-color").description("Line color.").defaultValue(new SettingColor(255, 170, 0, 200)).build());

    private int timer;
    private Animal currentTarget;

    public FeedAura() {
        super(Categories.World, "feed-aura", "Automatically feeds and breeds nearby animals with held food.");
    }

    @Override
    public void onActivate() {
        this.timer = 0;
        this.currentTarget = null;
    }

    @Override
    public void onDeactivate() {
        this.currentTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) return;

        if (this.timer > 0) {
            this.timer--;
            return;
        }

        ItemStack heldItem = this.mc.player.getMainHandItem();
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (heldItem.isEmpty()) {
            heldItem = this.mc.player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
        }
        if (heldItem.isEmpty()) {
            this.currentTarget = null;
            return;
        }

        Animal target = this.findClosestTarget(heldItem);
        if (target == null) {
            this.currentTarget = null;
            return;
        }

        this.currentTarget = target;

        final InteractionHand interactionHand = hand;
        if (this.rotate.get()) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 50, () -> this.feed(target, interactionHand));
        } else {
            this.feed(target, interactionHand);
        }

        this.timer = this.delay.get();
    }

    private void feed(Animal animal, InteractionHand hand) {
        if (this.mc.gameMode == null || this.mc.player == null) return;
        this.mc.gameMode.interact(this.mc.player, animal, hand);
        this.mc.player.swing(hand);
    }

    private Animal findClosestTarget(ItemStack food) {
        if (this.mc.player == null || this.mc.level == null) return null;

        double r = this.range.get();
        AABB box = this.mc.player.getBoundingBox().inflate(r);
        Animal closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : this.mc.level.getEntities(this.mc.player, box)) {
            if (!(entity instanceof Animal animal)) continue;
            if (!animal.isAlive()) continue;

            double dist = animal.distanceTo(this.mc.player);
            if (dist > r || dist >= closestDist) continue;

            if (this.filterBabies.get() && animal.isBaby()) continue;
            if (this.filterHorses.get() && animal instanceof AbstractHorse) continue;

            if (this.filterUntamed.get()) {
                if (animal instanceof AbstractHorse horse && !horse.isTamed()) continue;
                if (animal instanceof TamableAnimal tame && !tame.isTame()) continue;
            }

            if (animal.isFood(food) && animal.canFallInLove()) {
                closest = animal;
                closestDist = dist;
            }
        }

        return closest;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get() && this.currentTarget != null && this.currentTarget.isAlive()) {
            event.renderer.box(this.currentTarget.getBoundingBox(), this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
        }
    }
}
