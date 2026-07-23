package com.blackhole.physics;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

/**
 * Material -> BlockPhysicsProfile registry. Extending block coverage later
 * means adding an entry here, not touching PredictionEngine.
 */
public final class BlockPhysicsRegistry {

    private final Map<Material, BlockPhysicsProfile> profiles = new EnumMap<>(Material.class);

    public BlockPhysicsRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        register(Material.AIR, BlockPhysicsProfile.AIR);

        register(Material.ICE, BlockPhysicsProfile.builder().slipperiness(0.98).build());
        register(Material.PACKED_ICE, BlockPhysicsProfile.builder().slipperiness(0.98).build());

        register(Material.WATER, BlockPhysicsProfile.builder().solid(false).liquid(LiquidType.WATER).build());
        register(Material.STATIONARY_WATER, BlockPhysicsProfile.builder().solid(false).liquid(LiquidType.WATER).build());

        register(Material.LAVA, BlockPhysicsProfile.builder().solid(false).liquid(LiquidType.LAVA).build());
        register(Material.STATIONARY_LAVA, BlockPhysicsProfile.builder().solid(false).liquid(LiquidType.LAVA).build());

        register(Material.WEB, BlockPhysicsProfile.builder().solid(false).velocityMultiplier(0.05).build());

        register(Material.SOUL_SAND, BlockPhysicsProfile.builder()
                .slipperiness(0.6)
                .extraHorizontalFriction(0.4)
                .hitboxTopOffset(-0.125)
                .build());

        register(Material.LADDER, BlockPhysicsProfile.builder().solid(false).climbable(true).build());
        register(Material.VINE, BlockPhysicsProfile.builder().solid(false).climbable(true).build());

        register(Material.SLIME_BLOCK, BlockPhysicsProfile.builder().bouncy(true).build());
    }

    public void register(Material material, BlockPhysicsProfile profile) {
        profiles.put(material, profile);
    }

    public BlockPhysicsProfile getProfile(Material material) {
        if (material == null || material == Material.AIR) {
            return BlockPhysicsProfile.AIR;
        }
        BlockPhysicsProfile profile = profiles.get(material);
        if (profile != null) {
            return profile;
        }
        return material.isSolid() ? BlockPhysicsProfile.DEFAULT_SOLID : BlockPhysicsProfile.AIR;
    }
}
