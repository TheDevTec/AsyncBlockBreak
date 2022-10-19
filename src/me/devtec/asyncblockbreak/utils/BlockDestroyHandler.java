package me.devtec.asyncblockbreak.utils;

import java.lang.reflect.Constructor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import me.devtec.asyncblockbreak.Loader;
import me.devtec.shared.Ref;
import me.devtec.shared.components.Component;
import me.devtec.theapi.bukkit.BukkitLoader;
import me.devtec.theapi.bukkit.game.Position;

public interface BlockDestroyHandler {

	static Constructor<?> packetDisconnect = Ref.constructor(Ref.nms("network.protocol.game", "PacketPlayOutKickDisconnect"), Ref.nms("network.chat", "IChatBaseComponent"));
	static BlockFace[] faces = { BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH };
	static BlockFace[] all_faces = { BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.DOWN, BlockFace.UP };

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

	default void fixPlantIfType(Position pos, Material blockType) {
		if (blockType == Material.KELP || blockType == Material.KELP_PLANT)
			fixPlant(pos, Material.KELP_PLANT, Material.KELP, Material.KELP);
		else if (blockType == Material.TWISTING_VINES || blockType == Material.TWISTING_VINES_PLANT)
			fixPlant(pos, Material.TWISTING_VINES, Material.TWISTING_VINES_PLANT, Material.TWISTING_VINES);
		else if (blockType == Material.WEEPING_VINES || blockType == Material.WEEPING_VINES_PLANT)
			fixPlant(pos, Material.WEEPING_VINES, Material.WEEPING_VINES_PLANT, Material.WEEPING_VINES);
	}

	default void fixPlant(Position pos, Material thisOne, Material orThisOne, Material replacement) {
		Position clone2 = pos.clone().add(0, -1, 0);
		Material type = clone2.getBukkitType();
		if (type == thisOne || type == orThisOne)
			clone2.setTypeAndUpdate(replacement, false);
	}

	default boolean isInvalid(Player player, Position pos) {
		return !player.isOnline() || !player.isValid() || player.isDead() || player.getHealth() <= 0 || player.getGameMode() == GameMode.SPECTATOR || Loader.KICK_PLAYER
				? Loader.kick.contains(player.getUniqueId())
				: false || invalidRange(player.getLocation(), player, pos);
	}

	default boolean invalidRange(Location location, Player player, Position pos) {
		double radius = pos.distance(location);
		if (Loader.MAXIMUM_RADIUS_WITHIN_BLOCK_AND_PLAYER != 0 && radius > Loader.MAXIMUM_RADIUS_WITHIN_BLOCK_AND_PLAYER) {
			if (Loader.KICK_PLAYER) {
				Loader.kick.add(player.getUniqueId());
				BukkitLoader.getPacketHandler().send(player,
						Ref.newInstance(packetDisconnect, BukkitLoader.getNmsProvider().toIChatBaseComponent(new Component("Destroyed block at too far distance (Hacking?)"))));
			}
			announce("Player " + player.getName() + "[" + player.getUniqueId() + "] destroyed a block at too far a distance (" + radius + ") (Hacking?)");
			return true;
		}
		if (Loader.DESTROYED_BLOCKS_LIMIT == 0)
			return false;
		int destroyedBlocks = Loader.destroyedCountInTick.getOrDefault(player.getUniqueId(), 0) + 1;
		if (destroyedBlocks > Loader.DESTROYED_BLOCKS_LIMIT) {
			if (Loader.KICK_PLAYER) {
				Loader.kick.add(player.getUniqueId());
				BukkitLoader.getPacketHandler().send(player,
						Ref.newInstance(packetDisconnect, BukkitLoader.getNmsProvider().toIChatBaseComponent(new Component("Too many blocks destroyed in one server tick (Hacking?)"))));
			}
			announce("Player " + player.getName() + "[" + player.getUniqueId() + "] destroyed too many blocks (" + destroyedBlocks + ") in one server tick (Hacking?)");
			return true;
		}
		Loader.destroyedCountInTick.put(player.getUniqueId(), destroyedBlocks);
		return false;
	}

	default void announce(String text) {
		if (Loader.BROADCAST_CONSOLE)
			Bukkit.getConsoleSender().sendMessage(text);
		if (Loader.BROADCAST_ADMINS)
			for (Player player : Bukkit.getOnlinePlayers())
				if (player.hasPermission("asyncblockbreak.anticheat"))
					player.sendMessage(text);
	}
}
