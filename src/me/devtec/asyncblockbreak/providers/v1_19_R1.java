package me.devtec.asyncblockbreak.providers;

import java.lang.reflect.Field;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

import me.devtec.asyncblockbreak.Loader;
import me.devtec.asyncblockbreak.api.LootTable;
import me.devtec.asyncblockbreak.events.AsyncBlockBreakEvent;
import me.devtec.asyncblockbreak.events.BlockBreakDropItemsEvent;
import me.devtec.asyncblockbreak.utils.BlockDestroyHandler;
import me.devtec.shared.Ref;
import me.devtec.shared.scheduler.Tasker;
import me.devtec.theapi.bukkit.BukkitLoader;
import me.devtec.theapi.bukkit.game.Position;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.PacketPlayInBlockDig;
import net.minecraft.network.protocol.game.PacketPlayInBlockDig.EnumPlayerDigType;
import net.minecraft.network.protocol.game.PacketPlayOutBlockChange;
import net.minecraft.network.protocol.game.PacketPlayOutKickDisconnect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.EnumHand;
import net.minecraft.world.item.enchantment.EnchantmentDurability;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.ITileEntity;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityBeacon;
import net.minecraft.world.level.block.entity.TileEntityBeehive;
import net.minecraft.world.level.block.entity.TileEntityChest;
import net.minecraft.world.level.block.entity.TileEntityCommand;
import net.minecraft.world.level.block.entity.TileEntityContainer;
import net.minecraft.world.level.block.entity.TileEntityMobSpawner;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockPropertyBedPart;
import net.minecraft.world.level.block.state.properties.BlockPropertyChestType;
import net.minecraft.world.level.block.state.properties.BlockPropertyDoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IBlockState;
import net.minecraft.world.level.chunk.Chunk;

public class v1_19_R1 implements BlockDestroyHandler {
	static IBlockState<EnumDirection> direction;
	static IBlockState<BlockPropertyBedPart> bedpart = BlockProperties.bc;
	static IBlockState<BlockPropertyDoubleBlockHalf> doubleHalf = BlockProperties.ae;
	static IBlockState<BlockPropertyChestType> chestType = BlockProperties.bd;
	// vines
	static IBlockState<Boolean> east, north, south, west, up;

	@SuppressWarnings("unchecked")
	private void destroyAround(Material blockType, Position pos, Player player, LootTable items, boolean dropItems) {
		// FIX PLANT
		if (blockType == Material.KELP || blockType == Material.KELP_PLANT)
			fixPlant(pos, Material.KELP_PLANT, Material.KELP, Material.KELP);
		else if (blockType == Material.TWISTING_VINES || blockType == Material.TWISTING_VINES_PLANT)
			fixPlant(pos, Material.TWISTING_VINES, Material.TWISTING_VINES_PLANT, Material.TWISTING_VINES);
		else if (blockType == Material.WEEPING_VINES || blockType == Material.WEEPING_VINES_PLANT)
			fixPlant(pos, Material.WEEPING_VINES, Material.WEEPING_VINES_PLANT, Material.WEEPING_VINES);

		// top
		Position clone = pos.clone().add(0, 1, 0);
		IBlockData blockData = (IBlockData) clone.getIBlockData();
		Material type = blockData.getBukkitMaterial();
		if (!(type == Material.WATER || type == Material.LAVA))
			if (type == Material.CHORUS_PLANT || type == Material.CHORUS_FLOWER) {
				if (dropItems)
					for (ItemStack item : clone.getBlock().getDrops())
						items.add(item);
				clone.setAirAndUpdate(false);
				if (type == Material.CHORUS_PLANT)
					destroyAround(type, clone, player, items, dropItems);
			} else if (isBed(type))
				destroyBed(player, clone, blockData, items, dropItems);
			else if (type == Material.TWISTING_VINES || type == Material.TWISTING_VINES_PLANT || type == Material.SUGAR_CANE || type == Material.BAMBOO || type == Material.KELP
					|| type == Material.KELP_PLANT) {
				type = ((IBlockData) clone.getIBlockData()).getBukkitMaterial();
				while (type == Material.TWISTING_VINES || type == Material.TWISTING_VINES_PLANT || type == Material.SUGAR_CANE || type == Material.BAMBOO || type == Material.KELP
						|| type == Material.KELP_PLANT) {
					if (dropItems)
						for (ItemStack item : clone.getBlock().getDrops())
							items.add(item);
					removeBlock(clone, type == Material.KELP || type == Material.KELP_PLANT);
					clone.setY(clone.getY() + 1);
					type = ((IBlockData) clone.getIBlockData()).getBukkitMaterial();
				}
			} else if (!type.isSolid() && !type.isAir() && !type.name().contains("WALL_") && !(type == Material.WEEPING_VINES || type == Material.WEEPING_VINES_PLANT))
				if (isDoubleBlock(type)) // plant or door
					destroyDoubleBlock(isWaterlogged(blockData), player, clone, blockData, items, dropItems);
				else
					removeBlock(clone, isWaterlogged(blockData));

		// sides
		for (BlockFace face : faces) {
			clone = pos.clone().add(face.getModX(), face.getModY(), face.getModZ());
			blockData = (IBlockData) clone.getIBlockData();
			type = blockData.getBukkitMaterial();
			if (type == Material.WATER || type == Material.LAVA)
				continue;

			if (type == Material.CHORUS_PLANT || type == Material.CHORUS_FLOWER) {
				if (dropItems)
					for (ItemStack item : clone.getBlock().getDrops())
						items.add(item);
				clone.setAirAndUpdate(false);
				if (type == Material.CHORUS_PLANT)
					destroyAround(type, clone, player, items, dropItems);
			} else if (!type.isSolid() && !type.isAir() && type != Material.WATER && type != Material.LAVA)
				if (type.name().contains("WALL_")) {
					if (direction == null)
						for (IBlockState<?> s : blockData.v())
							if (s.f().equals("facing"))
								direction = (IBlockState<EnumDirection>) s;
					BlockFace bface = BlockFace.valueOf(blockData.c(direction).name());
					if (clone.getBlockX() - bface.getModX() == pos.getBlockX() && clone.getBlockZ() - bface.getModZ() == pos.getBlockZ()) {
						if (dropItems)
							for (ItemStack item : clone.getBlock().getDrops())
								items.add(item);
						removeBlock(clone, isWaterlogged(blockData));
					}
				} else if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN) {
					if (direction == null)
						for (IBlockState<?> s : blockData.v())
							if (s.f().equals("facing"))
								direction = (IBlockState<EnumDirection>) s;
					if (up == null || east == null || north == null || south == null || west == null)
						for (IBlockState<?> s : blockData.v()) {
							if (s.f().equals("east"))
								east = (IBlockState<Boolean>) s;
							if (s.f().equals("north"))
								north = (IBlockState<Boolean>) s;
							if (s.f().equals("south"))
								south = (IBlockState<Boolean>) s;
							if (s.f().equals("west"))
								west = (IBlockState<Boolean>) s;
							if (s.f().equals("up"))
								up = (IBlockState<Boolean>) s;
						}
					if (!blockBehindOrAbove(clone, blockData)) {
						removeBlock(clone, isWaterlogged(blockData));
						clone.setY(clone.getY() - 1);
						blockData = (IBlockData) clone.getIBlockData();
						type = blockData.getBukkitMaterial();
						if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN)
							destroyVine(clone, blockData);
					}
				}
		}

		// down
		clone = pos.clone().add(0, -1, 0);
		blockData = (IBlockData) clone.getIBlockData();
		type = blockData.getBukkitMaterial();
		if (!(type == Material.WATER || type == Material.LAVA))
			if (type == Material.CHORUS_PLANT || type == Material.CHORUS_FLOWER) {
				if (dropItems)
					for (ItemStack item : clone.getBlock().getDrops())
						items.add(item);
				clone.setAirAndUpdate(false);
				if (type == Material.CHORUS_PLANT)
					destroyAround(type, clone, player, items, dropItems);
			} else if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN) {
				if (up == null || east == null || north == null || south == null || west == null)
					for (IBlockState<?> s : blockData.v()) {
						if (s.f().equals("east"))
							east = (IBlockState<Boolean>) s;
						if (s.f().equals("north"))
							north = (IBlockState<Boolean>) s;
						if (s.f().equals("south"))
							south = (IBlockState<Boolean>) s;
						if (s.f().equals("west"))
							west = (IBlockState<Boolean>) s;
						if (s.f().equals("up"))
							up = (IBlockState<Boolean>) s;
					}
				if (!blockBehindOrAbove(clone, blockData)) {
					removeBlock(clone, isWaterlogged(blockData));
					clone.setY(clone.getY() - 1);
					blockData = (IBlockData) clone.getIBlockData();
					type = blockData.getBukkitMaterial();
					if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN)
						destroyVine(clone, blockData);
				}
			} else
				while (type == Material.WEEPING_VINES || type == Material.WEEPING_VINES_PLANT) {
					if (dropItems)
						for (ItemStack item : clone.getBlock().getDrops())
							items.add(item);
					clone.setAirAndUpdate(false);
					clone.setY(clone.getY() - 1);
					type = ((IBlockData) clone.getIBlockData()).getBukkitMaterial();
				}
	}

	private boolean blockBehindOrAbove(Position pos, IBlockData blockData) {
		boolean onEast = blockData.c(east);
		boolean onNorth = blockData.c(north);
		boolean onSouth = blockData.c(south);
		boolean onWest = blockData.c(west);
		boolean onTop = blockData.c(up);
		if (isVine(pos.clone().add(0, 1, 0), blockData.getBukkitMaterial()) || onTop && isSolid(pos.clone().add(0, 1, 0))
				|| onEast && isSolid(pos.clone().add(BlockFace.EAST.getModX(), 0, BlockFace.EAST.getModZ()))
				|| onNorth && isSolid(pos.clone().add(BlockFace.NORTH.getModX(), 0, BlockFace.NORTH.getModZ()))
				|| onSouth && isSolid(pos.clone().add(BlockFace.SOUTH.getModX(), 0, BlockFace.SOUTH.getModZ()))
				|| onWest && isSolid(pos.clone().add(BlockFace.WEST.getModX(), 0, BlockFace.WEST.getModZ())))
			return true;
		return false;
	}

	private void destroyVine(Position pos, IBlockData blockData) {
		if (blockBehindOrAbove(pos, blockData))
			return;

		removeBlock(pos, isWaterlogged(blockData));
		pos = pos.clone().setY(pos.getY() - 1);
		blockData = (IBlockData) pos.getIBlockData();
		Material type = blockData.getBukkitMaterial();
		if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN)
			destroyVine(pos, blockData);
	}

	@SuppressWarnings("unchecked")
	private static void destroyBed(Player player, Position clone, IBlockData blockData, LootTable items, boolean dropItems) {
		clone.setAirAndUpdate(false);
		if (direction == null)
			for (IBlockState<?> s : blockData.v())
				if (s.f().equals("facing"))
					direction = (IBlockState<EnumDirection>) s;

		BlockFace face = BlockFace.valueOf(blockData.c(direction).name());
		if (blockData.c(bedpart).name().equals("HEAD"))
			clone.add(face.getOppositeFace().getModX(), 0, face.getOppositeFace().getModZ());
		else {
			clone.add(face.getModX(), 0, face.getModZ());
			if (dropItems)
				for (ItemStack item : clone.getBlock().getDrops())
					items.add(item);
		}
		clone.setAirAndUpdate(false);
	}

	@SuppressWarnings("unchecked")
	private void destroyChest(Player player, Position pos, IBlockData iblockdata, LootTable items, boolean dropItems) {
		if (direction == null)
			for (IBlockState<?> s : iblockdata.v())
				if (s.f().equals("facing"))
					direction = (IBlockState<EnumDirection>) s;

		BlockPropertyChestType chesttype = iblockdata.c(chestType);
		BlockFace face = BlockFace.valueOf(iblockdata.c(direction).name());
		BlockPosition blockPos = (BlockPosition) pos.getBlockPosition();
		Chunk chunk = (Chunk) pos.getNMSChunk();
		TileEntityChest tile = (TileEntityChest) ((Chunk) pos.getNMSChunk()).getTileEntityImmediately(blockPos);
		if (dropItems || !Loader.DISABLE_TILE_DROPS)
			for (net.minecraft.world.item.ItemStack nmsItem : tile.getContents())
				items.add(CraftItemStack.asBukkitCopy(nmsItem));
		chunk.d(blockPos);
		removeBlock(pos, isWaterlogged(iblockdata));

		if (chesttype == BlockPropertyChestType.c) {
			Position clone = pos.clone();
			switch (face) {
			case EAST:
				clone.add(0, 0, -1);
				break;
			case NORTH:
				clone.add(-1, 0, 0);
				break;
			case SOUTH:
				clone.add(1, 0, 0);
				break;
			case WEST:
				clone.add(0, 0, 1);
				break;
			default:
				break;
			}
			IBlockData data = (IBlockData) clone.getIBlockData();
			if (data.getBukkitMaterial() == Material.CHEST | data.getBukkitMaterial() == Material.TRAPPED_CHEST) {
				data = data.a(chestType, BlockPropertyChestType.a);
				BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), data);
				BukkitLoader.getPacketHandler().send(BukkitLoader.getOnlinePlayers(), new PacketPlayOutBlockChange((BlockPosition) clone.getBlockPosition(), data));
				Position.updateLightAt(clone);
			}
		} else if (chesttype == BlockPropertyChestType.b) {
			Position clone = pos.clone();
			switch (face) {
			case EAST:
				clone.add(0, 0, 1);
				break;
			case NORTH:
				clone.add(1, 0, 0);
				break;
			case SOUTH:
				clone.add(-1, 0, 0);
				break;
			case WEST:
				clone.add(0, 0, -1);
				break;
			default:
				break;
			}
			IBlockData data = (IBlockData) clone.getIBlockData();
			if (data.getBukkitMaterial() == Material.CHEST | data.getBukkitMaterial() == Material.TRAPPED_CHEST) {
				data = data.a(chestType, BlockPropertyChestType.a);
				BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), data);
				BukkitLoader.getPacketHandler().send(BukkitLoader.getOnlinePlayers(), new PacketPlayOutBlockChange((BlockPosition) clone.getBlockPosition(), data));
				Position.updateLightAt(clone);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void destroyDoubleBlock(boolean water, Player player, Position pos, IBlockData iblockdata, LootTable items, boolean dropItems) {
		if (dropItems)
			for (ItemStack item : pos.getBlock().getDrops())
				items.add(item);
		removeBlock(pos, water);

		if (doubleHalf == null)
			for (IBlockState<?> s : iblockdata.v())
				if (s.f().equals("half"))
					doubleHalf = (IBlockState<BlockPropertyDoubleBlockHalf>) s;
		if (iblockdata.c(doubleHalf).name().equals("LOWER")) {
			Position clone = pos.clone().add(0, 1, 0);
			if (dropItems)
				for (ItemStack item : clone.getBlock().getDrops())
					items.add(item);
			removeBlock(clone, water);
		} else {
			Position clone = pos.clone().add(0, -1, 0);
			if (dropItems)
				for (ItemStack item : clone.getBlock().getDrops())
					items.add(item);
			removeBlock(clone, water);
		}
	}

	public void breakBlock(Player player, Position pos, IBlockData iblockdata, EntityPlayer nmsPlayer, PacketPlayInBlockDig packet, AsyncBlockBreakEvent breakEvent) {
		BlockPosition blockPos = packet.b();

		LootTable loot = new LootTable();
		// Add loot from block to the LootTable
		if (breakEvent.isDropItems())
			for (ItemStack item : pos.getBlock().getDrops(player.getItemInHand(), player))
				loot.add(item);

		// Destroy block/s
		Material material = iblockdata.getBukkitMaterial();
		if (material == Material.CHEST || material == Material.TRAPPED_CHEST)
			destroyChest(player, pos, iblockdata, loot, breakEvent.isDropItems());
		else if (isDoubleBlock(material)) // plant or door
			destroyDoubleBlock(isWaterlogged(iblockdata), player, pos, iblockdata, loot, breakEvent.isDropItems());
		else if (isBed(material))
			destroyBed(player, pos, iblockdata, loot, breakEvent.isDropItems());
		else {
			boolean pass = true;
			if (iblockdata.b() instanceof ITileEntity) {
				pass = false; // don't do useless check
				Chunk chunk = (Chunk) pos.getNMSChunk();
				TileEntity tilet = chunk.getTileEntityImmediately(blockPos);
				if (tilet instanceof TileEntityContainer) {
					TileEntityContainer tile = (TileEntityContainer) tilet;
					if (breakEvent.isDropItems() || !Loader.DISABLE_TILE_DROPS)
						for (net.minecraft.world.item.ItemStack nmsItem : tile.getContents())
							loot.add(CraftItemStack.asBukkitCopy(nmsItem));
					chunk.d(blockPos);
					chunk.q.capturedTileEntities.remove(blockPos);
					pass = true;
				} else
					pass = tilet instanceof TileEntityMobSpawner || tilet instanceof TileEntityBeacon || tilet instanceof TileEntityCommand || tilet instanceof TileEntityBeehive;
			}
			if (isWaterlogged(iblockdata))
				pos.setTypeAndUpdate(Material.WATER, true);
			else
				pos.setAirAndUpdate(true);

			if (pass)
				destroyAround(material, pos, player, loot, breakEvent.isDropItems());
		}
		BukkitLoader.getPacketHandler().send(player, new ClientboundBlockChangedAckPacket(packet.e())); // Finish BlockBreak packet

		// Drop items & exp
		Location dropLoc = pos.add(0.5, 0, 0.5).toLocation();
		if (!loot.getItems().isEmpty() || breakEvent.getExpToDrop() > 0)
			MinecraftServer.getServer().execute(() -> {

				// Do not call event if isn't registered any listener
				if (BlockBreakDropItemsEvent.getHandlerList().getRegisteredListeners().length == 0) {
					for (ItemStack drop : loot.getItems())
						player.getWorld().dropItem(dropLoc, drop, breakEvent.getItemConsumer());
					if (breakEvent.getExpToDrop() > 0)
						player.getWorld().spawn(dropLoc, EntityType.EXPERIENCE_ORB.getEntityClass(), c -> {
							ExperienceOrb orb = (ExperienceOrb) c;
							orb.setExperience(breakEvent.getExpToDrop());
						});
					return;
				}
				BlockBreakDropItemsEvent event = new BlockBreakDropItemsEvent(breakEvent, loot);
				Bukkit.getPluginManager().callEvent(event);
				if (!event.isCancelled())
					for (ItemStack drop : event.getLoot().getItems())
						player.getWorld().dropItem(dropLoc, drop, breakEvent.getItemConsumer());
				if (breakEvent.getExpToDrop() > 0)
					player.getWorld().spawn(dropLoc, EntityType.EXPERIENCE_ORB.getEntityClass(), c -> {
						ExperienceOrb orb = (ExperienceOrb) c;
						orb.setExperience(breakEvent.getExpToDrop());
					});
			});
		// Damage tool
		int damage = damageTool(nmsPlayer, nmsPlayer.b(EnumHand.a), nmsPlayer.b(EnumHand.a).u.q("Unbreakable") ? 0 : 1);
		if (damage > 0)
			if (nmsPlayer.b(EnumHand.a).j() + damage == nmsPlayer.b(EnumHand.a).k())
				player.setItemInHand(null);
			else
				nmsPlayer.b(EnumHand.a).b(nmsPlayer.b(EnumHand.a).j() + damage);
	}

	private static final Field async = Ref.field(Event.class, "async");

	private int damageTool(EntityPlayer player, net.minecraft.world.item.ItemStack item, int damage) {
		int enchant = EnchantmentManager.a(Enchantments.w, item);

		if (enchant > 0 && EnchantmentDurability.a(item, enchant, player.dQ()))
			--damage;

		PlayerItemDamageEvent event = new PlayerItemDamageEvent(player.getBukkitEntity(), CraftItemStack.asCraftMirror(item), damage);
		Ref.set(event, async, true);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled())
			return 0;

		return event.getDamage();
	}

	private void processBlockBreak(PacketPlayInBlockDig packet, Player player, EntityPlayer nmsPlayer, IBlockData iblockdata, BlockPosition blockPos, Position pos, boolean cancelDrop,
			boolean instantlyBroken) {
		AsyncBlockBreakEvent event = new AsyncBlockBreakEvent(pos.getBlock(), player, BukkitLoader.getNmsProvider().toMaterial(iblockdata), instantlyBroken, BlockFace.valueOf(packet.c().name()));
		event.setDropItems(cancelDrop);
		if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL)
			if (nmsPlayer.d(iblockdata.b().m()))
				event.setExpToDrop(iblockdata.b().getExpDrop(iblockdata, nmsPlayer.x(), blockPos, CraftItemStack.asNMSCopy(player.getItemInHand()), true));

		// Do not call event if isn't registered any listener - instantly process async
		if (BlockExpEvent.getHandlerList().getRegisteredListeners().length == 0) {
			breakBlock(player, pos, iblockdata, nmsPlayer, packet, event);
			return;
		}

		if (Loader.SYNC_EVENT)
			BukkitLoader.getNmsProvider().postToMainThread(() -> {
				Bukkit.getPluginManager().callEvent(event);
				new Tasker() {
					@Override
					public void run() {
						if (event.isCancelled()) {
							sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
							return;
						}
						breakBlock(player, pos, iblockdata, nmsPlayer, packet, event);
					}
				}.runTask();
			});
		else {
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
				return;
			}
			breakBlock(player, pos, iblockdata, nmsPlayer, packet, event);
		}
	}

	private boolean isInvalid(Player player, Position pos) {
		return !player.isOnline() || !player.isValid() || player.isDead() || player.getHealth() <= 0 || Loader.kick.contains(player.getUniqueId()) || invalidRange(player.getLocation(), player, pos);
	}

	private boolean invalidRange(Location location, Player player, Position pos) {
		double radius = pos.distance(location);
		if (radius > Loader.MAXIMUM_RADIUS_WITHIN_BLOCK_AND_PLAYER) {
			if (Loader.KICK_PLAYER) {
				Loader.kick.add(player.getUniqueId());
				BukkitLoader.getPacketHandler().send(player, new PacketPlayOutKickDisconnect(IChatBaseComponent.a("Destroyed block at too far distance (Hacking?)")));
			}
			announce("Player " + player.getName() + "[" + player.getUniqueId() + "] destroyed a block at too far a distance (" + radius + ") (Hacking?)");
			return true;
		}
		int destroyedBlocks = Loader.destroyedCountInTick.getOrDefault(player.getUniqueId(), 0) + 1;
		if (destroyedBlocks > Loader.DESTROYED_BLOCKS_LIMIT) {
			if (Loader.KICK_PLAYER) {
				Loader.kick.add(player.getUniqueId());
				BukkitLoader.getPacketHandler().send(player, new PacketPlayOutKickDisconnect(IChatBaseComponent.a("Too many blocks destroyed in one server tick (Hacking?)")));
			}
			announce("Player " + player.getName() + "[" + player.getUniqueId() + "] destroyed too many blocks (" + destroyedBlocks + ") in one server tick (Hacking?)");
			return true;
		}
		Loader.destroyedCountInTick.put(player.getUniqueId(), destroyedBlocks);
		return false;
	}

	private void announce(String text) {
		if (Loader.BROADCAST_CONSOLE)
			Bukkit.getConsoleSender().sendMessage(text);
		if (Loader.BROADCAST_ADMINS)
			for (Player player : Bukkit.getOnlinePlayers())
				if (player.hasPermission("asyncblockbreak.anticheat"))
					player.sendMessage(text);
	}

	@Override
	public boolean handle(String playerName, Object packetObject) {
		PacketPlayInBlockDig packet = (PacketPlayInBlockDig) packetObject;
		if (packet.d() == EnumPlayerDigType.c) { // destoy
			BlockPosition blockPos = packet.b();
			Player player = Bukkit.getPlayer(playerName);
			Position pos = new Position(player.getWorld(), blockPos.u(), blockPos.v(), blockPos.w());
			IBlockData iblockdata = (IBlockData) pos.getIBlockData();
			if (iblockdata.getBukkitMaterial() == Material.AIR || isInvalid(player, pos)) {
				sendCancelPackets(packet, player, blockPos, iblockdata);
				return true;
			}

			EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
			processBlockBreak(packet, player, nmsPlayer, iblockdata, blockPos, pos, true, false);
			return true;
		}
		if (packet.d() == EnumPlayerDigType.a) { // start
			BlockPosition blockPos = packet.b();
			Player player = Bukkit.getPlayer(playerName);
			Position pos = new Position(player.getWorld(), blockPos.u(), blockPos.v(), blockPos.w());
			IBlockData iblockdata = (IBlockData) pos.getIBlockData();
			EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();

			if (player.getGameMode() == GameMode.CREATIVE) {
				if (iblockdata.getBukkitMaterial() == Material.AIR || isInvalid(player, pos)) {
					sendCancelPackets(packet, player, blockPos, iblockdata);
					return true;
				}
				processBlockBreak(packet, player, nmsPlayer, iblockdata, blockPos, pos, false, true);
				return true;
			}

			iblockdata.a(nmsPlayer.s, blockPos, nmsPlayer); // hit block
			float f = iblockdata.a(nmsPlayer, nmsPlayer.s, blockPos); // get damage
			if (f >= 1.0F) {
				if (iblockdata.getBukkitMaterial() == Material.AIR || isInvalid(player, pos)) {
					sendCancelPackets(packet, player, blockPos, iblockdata);
					return true;
				}
				processBlockBreak(packet, player, nmsPlayer, iblockdata, blockPos, pos, true, true);
				return true;
			}
		}
		return false;
	}

	public void sendCancelPackets(PacketPlayInBlockDig packet, Player player, BlockPosition blockPos, IBlockData iblockdata) {
		BukkitLoader.getPacketHandler().send(player, new PacketPlayOutBlockChange(blockPos, iblockdata));
		BukkitLoader.getPacketHandler().send(player, new ClientboundBlockChangedAckPacket(packet.e()));
	}

	private boolean isWaterlogged(IBlockData data) {
		if (data.getBukkitMaterial() == Material.SEAGRASS || data.getBukkitMaterial() == Material.TALL_SEAGRASS || data.getBukkitMaterial() == Material.KELP
				|| data.getBukkitMaterial() == Material.KELP_PLANT)
			return true;
		return !data.b(BlockProperties.C) ? false : data.c(BlockProperties.C);
	}
}
