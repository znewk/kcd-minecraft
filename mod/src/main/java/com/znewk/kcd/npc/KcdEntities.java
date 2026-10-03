package com.znewk.kcd.npc;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.znewk.kcd.KcdMod;

/** Сущности мода. */
public final class KcdEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, KcdMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<KcdNpc>> NPC = ENTITIES.register("npc",
        () -> EntityType.Builder.of(KcdNpc::new, MobCategory.MISC)
            .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10)
            .build(KcdMod.MODID + ":npc"));

    private KcdEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(NPC.get(), KcdNpc.createAttributes().build());
    }
}
