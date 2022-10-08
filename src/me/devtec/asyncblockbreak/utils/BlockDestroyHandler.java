package me.devtec.asyncblockbreak.utils;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import me.devtec.theapi.bukkit.game.Position;

public interface BlockDestroyHandler {

	static BlockFace[] faces = { BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH };

	boolean handle(String player, Object packet);

	default boolean isBed(Material material) {
		return material.name().endsWith("_BED");
	}

	default boolean isDoubleBlock(Material material) {
		return material == Material.TALL_GRASS || material == Material.TALL_SEAGRASS || material == Material.LARGE_FERN || material == Material.PEONY || material == Material.ROSE_BUSH
				|| material == Material.LILAC || material == Material.SUNFLOWER || material.name().endsWith("_DOOR");
	}

	default boolean isSolid(Position add) {
		return add.getBukkitType().isSolid();
	}

	default boolean isVine(Position add, Material bukkitMaterial) {
		return add.getBukkitType() == bukkitMaterial;
	}

	default void removeBlock(Position clone, boolean water) {
		if (water)
			clone.setTypeAndUpdate(Material.WATER, false);
		else
			clone.setAirAndUpdate(false);
	}

	default void fixPlant(Position pos, Material thisOne, Material orThisOne, Material replacement) {
		Position clone2 = pos.clone().add(0, -1, 0);
		Material type = clone2.getBukkitType();
		if (type == thisOne || type == orThisOne)
			clone2.setTypeAndUpdate(replacement, false);
	}
}
