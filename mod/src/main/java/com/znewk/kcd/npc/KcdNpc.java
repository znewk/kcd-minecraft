package com.znewk.kcd.npc;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.znewk.kcd.dialogue.DialogueService;

/**
 * Житель KCD: тело с моделью игрока и своим скином. Кто он (имя, скин, диалог) — в файле
 * {@code data/<ns>/kcd_npc/<id>.json}, сущность хранит только ссылку на него.
 * Пока без распорядка дня: стоит на месте, смотрит на прохожих, во время разговора — на собеседника.
 */
public class KcdNpc extends PathfinderMob {
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(KcdNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM = SynchedEntityData.defineId(KcdNpc.class, EntityDataSerializers.BOOLEAN);

    private String npcId = "";
    @Nullable
    private UUID talkingTo;

    public KcdNpc(EntityType<? extends KcdNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, "");
        builder.define(SLIM, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    public String npcId() {
        return npcId;
    }

    public boolean slim() {
        return entityData.get(SLIM);
    }

    public String skin() {
        return entityData.get(SKIN);
    }

    /** Назначить NPC его описание (имя и скин берутся из файла). */
    public void setNpcId(String id) {
        npcId = id;
        applyDefinition();
    }

    private void applyDefinition() {
        NpcDefinition def = NpcRegistry.get(npcId);
        if (def == null) return;
        if (!def.skin().equals(skin())) entityData.set(SKIN, def.skin());
        if (def.slim() != slim()) entityData.set(SLIM, def.slim());
        if (getCustomName() == null || !getCustomName().getString().equals(def.name())) setCustomName(Component.literal(def.name()));
    }

    public void setTalkingTo(@Nullable UUID player) {
        talkingTo = player;
        if (player != null) getNavigation().stop();
    }

    @Override
    public void tick() {
        super.tick();
        // описания могли перезагрузиться (/reload) — подхватываем изредка, это дёшево
        if (!level().isClientSide && tickCount % 100 == 1) applyDefinition();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (talkingTo != null && level().getPlayerByUUID(talkingTo) instanceof Player p) {
            getLookControl().setLookAt(p, 30.0F, 30.0F);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) DialogueService.start(sp, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // жители не умирают от случайного урона; /kill работает
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("npc", npcId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        npcId = tag.getString("npc");
        applyDefinition();
    }
}
