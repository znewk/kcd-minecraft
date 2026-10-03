package com.znewk.kcd.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import com.znewk.kcd.npc.KcdNpc;

/** Житель рисуется моделью игрока (обычной или с тонкими руками) со своим скином. */
public class KcdNpcRenderer extends HumanoidMobRenderer<KcdNpc, PlayerModel<KcdNpc>> {
    private final PlayerModel<KcdNpc> wide;
    private final PlayerModel<KcdNpc> slim;

    public KcdNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        wide = model;
        slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            ctx.getModelManager()));
    }

    @Override
    public void render(KcdNpc npc, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = npc.slim() ? slim : wide;
        super.render(npc, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(KcdNpc npc) {
        ResourceLocation skin = npc.skin().isEmpty() ? null : ResourceLocation.tryParse(npc.skin());
        return skin != null ? skin : DefaultPlayerSkin.getDefaultTexture();
    }
}
