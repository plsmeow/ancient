package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import tech.onetap.event.list.EventHUD;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.player.other.WorldUtils;
import tech.onetap.util.render.math.ProjectionUtil;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

@ModuleInformation(moduleName = "Predictions", moduleDesc = "Показывает траекторию полета предметов", moduleCategory = ModuleCategory.RENDER)
public class Predictions extends Module {

    private final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Эндерперлы", true),
            new BooleanSetting("Стрелы", true),
            new BooleanSetting("Трезубцы", true),
            new BooleanSetting("Зелья", true),
            new BooleanSetting("Опыт", true),
            new BooleanSetting("Снежки/яйца", true),
            new BooleanSetting("Предметы", true)
    );

    private final List<Point> points = new ArrayList<>();

    @Subscribe
    public void onDraw(EventHUD e) {
        for (Point point : points) {
            Vector2f vec2f = ProjectionUtil.project(point.pos);
            int ticks = point.ticks;

            double time = ticks * 50 / 1000.0;
            String text = String.format("%.1f", time) + " сек";
            float textWidth = Fonts.SFREGULAR.get().getWidth(text, 7);

            float centerX = vec2f.getX();
            float centerY = vec2f.getY();

            float totalWidth = textWidth;
            float totalHeight = 5.75F;

            float rectX = centerX - totalWidth / 2f;
            float rectY = centerY - totalHeight / 2f;

            float textX = rectX;
            float textY = rectY + 5;

            DrawUtil.drawRound(textX - 7, textY - 2, totalWidth + 14.75f, totalHeight + 5,0, ColorProvider.rgba(0,0,0,120));
            e.getDrawContext().getMatrices().push();
            e.getDrawContext().getMatrices().translate(textX - 5, textY - 0.75f, 0);
            e.getDrawContext().getMatrices().scale(0.5f,0.5f,1);
            e.getDrawContext().drawItem(point.stack(), 0, 0);
            e.getDrawContext().getMatrices().scale(1,1,1);
            e.getDrawContext().getMatrices().translate(-(textX - 5), -(textY - 0.75f), 0);
            e.getDrawContext().getMatrices().pop();
            DrawUtil.drawText(
                    Fonts.SFREGULAR.get(),
                    text.replace(",","."),
                    textX + 4.5f,
                    textY - 0.5f,
                    ColorProvider.rgba(255,255,255,255), 6.75f
            );
        }
    }

    @Subscribe
    public void onWorldRender(EventWorldRender e) {
        points.clear();
        getProjectiles().forEach(entity -> {
            Vec3d motion = entity.getVelocity();
            Vec3d pos = entity.getPos();
            Vec3d prevPos;
            int ticks = 0;

            for (int i = 0; i < 300; i++) {
                prevPos = pos;
                pos = pos.add(motion);
                motion = calculateMotion(entity, prevPos, motion);

                HitResult result = RaytraceUtil.raycast(prevPos, pos, RaycastContext.ShapeType.COLLIDER, entity);
                if (!result.getType().equals(HitResult.Type.MISS)) {
                    pos = result.getPos();
                }

                DrawUtil.drawLine(prevPos, pos, ColorProvider.setAlpha(ColorProvider.getThemeColor(), MathHelper.clamp(i / 25.0f, 0, 1) * 255), 2, false);

                Vec3d finalPrevPos = prevPos, finalPos = pos;
                boolean inEntity = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                        .filter(ent -> ent instanceof LivingEntity living && living != mc.player && living.isAlive())
                        .anyMatch(ent -> ent.getBoundingBox().expand(0.25).intersects(finalPrevPos, finalPos));
                if (result.getType().equals(HitResult.Type.BLOCK) || pos.y < -128 || inEntity || result.getType().equals(HitResult.Type.ENTITY)) {
                    BreakingBad(entity, pos, ticks);
                    break;
                }
                ticks++;
            }
        });
    }

    public List<Entity> getProjectiles() {
        return StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(e -> (e instanceof PersistentProjectileEntity || e instanceof ThrownItemEntity || e instanceof ItemEntity) && !visible(e))
                .filter(this::isTargetEnabled)
                .toList();
    }

    public Vec3d calculateMotion(Entity entity, Vec3d prevPos, Vec3d motion) {
        boolean isInWater = Objects.requireNonNull(mc.world).getBlockState(BlockPos.ofFloored(prevPos)).getFluidState().isIn(FluidTags.WATER);

        float multiply = switch (entity) {
            case TridentEntity i -> 0.99F;
            case PersistentProjectileEntity i when isInWater -> 0.6F;
            default -> isInWater ? 0.8F : 0.99F;
        };

        return motion.multiply(multiply).add(0, -entity.getFinalGravity(),0);
    }

    private void BreakingBad(Entity entity, Vec3d pos, int ticks) {
        switch (entity) {
            case ItemEntity item -> points.add(new Point(item.getStack(), pos, ticks));
            case ThrownItemEntity thrown -> points.add(new Point(thrown.getStack(), pos, ticks));
            case PersistentProjectileEntity persistent -> points.add(new Point(persistent.getItemStack(), pos, ticks));
            default -> {}
        }
    }

    private boolean visible(Entity entity) {
        boolean posChange = entity.getX() == entity.prevX && entity.getY() == entity.prevY && entity.getZ() == entity.prevZ;
        boolean itemEntityCheck = entity instanceof ItemEntity && (entity.isOnGround() || WorldUtils.isBoxInBlock(entity.getBoundingBox().expand(2), Blocks.WATER));
        return posChange || itemEntityCheck;
    }

    private boolean isTargetEnabled(Entity entity) {
        return switch (entity) {
            case TridentEntity ignored -> targets.isEnabled("Трезубцы");
            case PersistentProjectileEntity ignored -> targets.isEnabled("Стрелы");
            case ItemEntity ignored -> targets.isEnabled("Предметы");
            case ThrownItemEntity thrown -> {
                ItemStack stack = thrown.getStack();
                if (stack.isOf(Items.ENDER_PEARL)) yield targets.isEnabled("Эндерперлы");
                if (stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION) || stack.isOf(Items.POTION)) yield targets.isEnabled("Зелья");
                if (stack.isOf(Items.EXPERIENCE_BOTTLE)) yield targets.isEnabled("Опыт");
                if (stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG)) yield targets.isEnabled("Снежки/яйца");
                yield false;
            }
            default -> false;
        };
    }

    private record Point(ItemStack stack, Vec3d pos, int ticks) {}
}
