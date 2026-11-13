package meow.ancient.client.util.render;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhase;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Matrix4f;

public final class ChamsSubmitNodeCollector extends ChamsOrderedSubmitNodeCollector implements SubmitNodeCollector {
    private final SubmitNodeCollector source;
    private final SubmitNodeStorage maskStorage;
    private final Int2ObjectOpenHashMap<ChamsOrderedSubmitNodeCollector> orders = new Int2ObjectOpenHashMap<>();

    public ChamsSubmitNodeCollector(SubmitNodeCollector delegate, SubmitNodeStorage mask) {
        super(delegate, mask);
        source = delegate;
        maskStorage = mask;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        ChamsOrderedSubmitNodeCollector collector = orders.get(order);
        OrderedSubmitNodeCollector delegate = source.order(order);
        OrderedSubmitNodeCollector mask = maskStorage.order(order);
        if (collector == null) {
            collector = new ChamsOrderedSubmitNodeCollector(delegate, mask);
            orders.put(order, collector);
        } else {
            collector.bind(delegate, mask);
        }
        return collector;
    }
}

class ChamsOrderedSubmitNodeCollector implements OrderedSubmitNodeCollector {
    private OrderedSubmitNodeCollector delegate;
    private OrderedSubmitNodeCollector mask;

    ChamsOrderedSubmitNodeCollector(OrderedSubmitNodeCollector delegate, OrderedSubmitNodeCollector mask) {
        this.delegate = delegate;
        this.mask = mask;
    }

    void bind(OrderedSubmitNodeCollector delegate, OrderedSubmitNodeCollector mask) {
        this.delegate = delegate;
        this.mask = mask;
    }

    private SimpleFeatureRenderPhase outlinePhase() {
        return mask instanceof SubmitNodeStorage storage ? storage.order(0).outline : ((SubmitNodeCollection) mask).outline;
    }

    private static RenderType outline(RenderType renderType) {
        return renderType.isOutline() ? renderType : renderType.outline().orElse(null);
    }

    private <S> void captureModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, UvMapping uvMapping) {
        RenderType outlineType = outline(renderType);
        if (outlineType != null) {
            mask.submitModel(model, state, poseStack, outlineType, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, uvMapping, -1);
        }
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        delegate.submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(PoseStack poseStack, Vec3 attachment, int yOffset, Component text, boolean discrete, int lightCoords, CameraRenderState cameraState) {
        delegate.submitNameTag(poseStack, attachment, yOffset, text, discrete, lightCoords, cameraState);
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode displayMode, int color, int backgroundColor, int lightCoords, int outlineColor) {
        delegate.submitText(poseStack, x, y, text, shadow, displayMode, color, backgroundColor, lightCoords, outlineColor);
    }

    @Override
    public void submitTextBackground(PoseStack poseStack, float minX, float minY, float maxX, float maxY, int color, Font.DisplayMode displayMode, int lightCoords) {
        delegate.submitTextBackground(poseStack, minX, minY, maxX, maxY, color, displayMode, lightCoords);
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf rotation) {
        delegate.submitFlame(poseStack, state, rotation);
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState state) {
        delegate.submitLeash(poseStack, state);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int color, UvMapping uvMapping, int outlineColor) {
        delegate.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, color, uvMapping, outlineColor);
        captureModel(model, state, poseStack, renderType, uvMapping);
    }

    @Override
    public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int color, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        delegate.submitCrumblingOverlay(model, state, poseStack, renderType, lightCoords, overlayCoords, color, crumblingOverlay);
        captureModel(model, state, poseStack, renderType, null);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int outlineColor) {
        delegate.submitMovingBlock(poseStack, state, outlineColor);
        outlinePhase().submit(new MovingBlockFeatureRenderer.Submit(new Matrix4f(poseStack.last().pose()), state, -1, false));
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
        delegate.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
        RenderType outlineType = outline(renderType);
        if (outlineType != null) {
            mask.submitBlockModel(poseStack, outlineType, parts, BlockModelRenderState.EMPTY_TINTS, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        }
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean translucent) {
        delegate.submitBreakingBlockModel(poseStack, parts, progress, translucent);
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float lineWidth, boolean sortVertices) {
        delegate.submitShapeOutline(poseStack, shape, renderType, color, lineWidth, sortVertices);
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers, ItemQuads quads, ItemStackRenderState.FoilType foilType) {
        delegate.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
        outlinePhase().submit(new ItemFeatureRenderer.Submit(poseStack.last().copy(), displayContext, LightCoordsUtil.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, -1, ItemStackRenderState.LayerRenderState.EMPTY_TINTS, quads.all(), ItemStackRenderState.FoilType.NONE));
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        delegate.submitCustomGeometry(poseStack, renderType, renderer);
        RenderType outlineType = outline(renderType);
        if (outlineType != null) {
            mask.submitCustomGeometry(poseStack, outlineType, renderer);
        }
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState state) {
        delegate.submitQuadParticleGroup(state);
    }

    @Override
    public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState cameraState, boolean translucent) {
        delegate.submitGizmoPrimitives(group, cameraState, translucent);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, Function<ChunkSectionLayer, RenderType> renderTypeFunction, boolean translucent, List<BlockStateModelPart> parts, Mesh mesh, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
        delegate.submitBlockModel(poseStack, renderTypeFunction, translucent, parts, mesh, tintLayers, lightCoords, overlayCoords, outlineColor);
        mask.submitBlockModel(poseStack, renderTypeFunction, translucent, parts, mesh, BlockModelRenderState.EMPTY_TINTS, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, Mesh mesh, int progress, boolean translucent) {
        delegate.submitBreakingBlockModel(poseStack, parts, mesh, progress, translucent);
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers, ItemQuads quads, MeshView mesh, ItemStackRenderState.FoilType foilType) {
        delegate.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, mesh, foilType);
        mask.submitItem(poseStack, displayContext, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, ItemStackRenderState.LayerRenderState.EMPTY_TINTS, quads, mesh, ItemStackRenderState.FoilType.NONE);
    }

    @Override
    public <T extends SubmitNode> void submitCustom(SubmitRenderPhase<T> phase, T node) {
        delegate.submitCustom(phase, node);
    }
}
