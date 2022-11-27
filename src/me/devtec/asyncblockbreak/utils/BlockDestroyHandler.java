package me.devtec.asyncblockbreak.utils;

import java.lang.reflect.Constructor;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.devtec.asyncblockbreak.Loader;
import me.devtec.shared.Ref;
import me.devtec.shared.components.Component;
import me.devtec.theapi.bukkit.BukkitLoader;
import me.devtec.theapi.bukkit.game.Position;

public interface BlockDestroyHandler {

	static Constructor<?> packetDisconnect = Ref.constructor(Ref.nms("network.protocol.game", "PacketPlayOutKickDisconnect"), Ref.nms("network.chat", "IChatBaseComponent"));

	boolean handle(String player, Object packet);

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

	Map<Position, BlockActionContext> calculateChangedBlocks(Position destroyed, Player player);

	default void announce(String text) {
		if (Loader.BROADCAST_CONSOLE)
			Bukkit.getConsoleSender().sendMessage(text);
		if (Loader.BROADCAST_ADMINS)
			for (Player player : Bukkit.getOnlinePlayers())
				if (player.hasPermission("asyncblockbreak.anticheat"))
					player.sendMessage(text);
	}
}
