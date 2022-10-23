package me.devtec.asyncblockbreak.providers;

import java.lang.reflect.Field;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftMagicNumbers;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

import me.devtec.asyncblockbreak.Loader;
import me.devtec.asyncblockbreak.api.LootTable;
import me.devtec.asyncblockbreak.events.AsyncBlockBreakEvent;
import me.devtec.asyncblockbreak.events.BlockBreakDropItemsEvent;
import me.devtec.asyncblockbreak.utils.BlockDestroyHandler;
import me.devtec.shared.Ref;
import me.devtec.theapi.bukkit.BukkitLoader;
import me.devtec.theapi.bukkit.game.Position;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.PacketPlayInBlockDig;
import net.minecraft.network.protocol.game.PacketPlayInBlockDig.EnumPlayerDigType;
import net.minecraft.network.protocol.game.PacketPlayOutBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.EnumHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.EntityFallingBlock;
import net.minecraft.world.item.enchantment.EnchantmentDurability;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockBannerWall;
import net.minecraft.world.level.block.BlockChorusFruit;
import net.minecraft.world.level.block.BlockCobbleWall;
import net.minecraft.world.level.block.BlockCoralFanWallAbstract;
import net.minecraft.world.level.block.BlockFacingHorizontal;
import net.minecraft.world.level.block.BlockLadder;
import net.minecraft.world.level.block.BlockLeaves;
import net.minecraft.world.level.block.BlockRedstoneTorchWall;
import net.minecraft.world.level.block.BlockTall;
import net.minecraft.world.level.block.BlockTileEntity;
import net.minecraft.world.level.block.BlockTorchWall;
import net.minecraft.world.level.block.BlockWallSign;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityChest;
import net.minecraft.world.level.block.entity.TileEntityContainer;
import net.minecraft.world.level.block.entity.TileEntityShulkerBox;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockPropertyAttachPosition;
import net.minecraft.world.level.block.state.properties.BlockPropertyBedPart;
import net.minecraft.world.level.block.state.properties.BlockPropertyChestType;
import net.minecraft.world.level.block.state.properties.BlockPropertyDoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockPropertyWallHeight;
import net.minecraft.world.level.block.state.properties.IBlockState;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

public class v1_19_R1 implements BlockDestroyHandler {
	static IBlockState<EnumDirection> direction = BlockProperties.S;
	static IBlockState<BlockPropertyBedPart> bedpart = BlockProperties.bc;
	static IBlockState<BlockPropertyDoubleBlockHalf> doubleHalf = BlockProperties.ae;
	static IBlockState<BlockPropertyChestType> chestType = BlockProperties.bd;
	// vines
	static IBlockState<Boolean> east = BlockProperties.N, north = BlockProperties.M, south = BlockProperties.O, west = BlockProperties.P, up = BlockProperties.K;
	static IBlockState<Boolean> waterlogged = BlockProperties.C;
	static IBlockState<EnumDirection> vertical_direction = BlockProperties.bn;
	static IBlockState<BlockPropertyWallHeight> wall_east = BlockProperties.W, wall_north = BlockProperties.X, wall_south = BlockProperties.Y, wall_west = BlockProperties.Z;
	@SuppressWarnings("unchecked")
	static IBlockState<Boolean>[] BLOCK_ROTATIONS = new IBlockState[] { east, north, south, west };
	@SuppressWarnings("unchecked")
	static IBlockState<BlockPropertyWallHeight>[] WALL_BLOCK_ROTATIONS = new IBlockState[] { wall_east, wall_north, wall_south, wall_west };
	static IBlockState<BlockPropertyAttachPosition> attach = BlockProperties.U;
	static Field async = Ref.field(Event.class, "async");

	private void removeEntity(Position pos, LootTable items, org.bukkit.entity.Entity entity, boolean canBePainting) {
		if (entity instanceof ItemFrame frame) {
			Location loc = entity.getLocation().add(frame.getAttachedFace().getDirection());
			if (loc.getBlockX() == pos.getBlockX() && loc.getBlockY() == pos.getBlockY() && loc.getBlockZ() == pos.getBlockZ()) {
				items.add(frame.getItem());
				items.add(new ItemStack(Material.ITEM_FRAME));
				frame.remove();
			}
		}
		if (canBePainting && entity instanceof Painting paint) {
			Location loc = entity.getLocation().add(paint.getAttachedFace().getDirection());
			if (loc.getBlockX() == pos.getBlockX() && loc.getBlockY() == pos.getBlockY() && loc.getBlockZ() == pos.getBlockZ()) {
				items.add(new ItemStack(Material.PAINTING));
				paint.remove();
			}
		}
	}

	private void destroyChorusInit(Position start, IBlockData blockData, LootTable items, boolean dropItems, boolean destroy) {
		if (destroy) {
			if (dropItems)
				for (ItemStack item : start.getBlock().getDrops())
					items.add(item);
			removeBlock(start, false);
		}
		boolean onEast = blockData.c(east);
		boolean onNorth = blockData.c(north);
		boolean onSouth = blockData.c(south);
		boolean onWest = blockData.c(west);
		boolean onTop = blockData.c(up);
		if (onTop) {
			destroyChorus(start.clone().add(0, 1, 0), items, dropItems, 1);
			return;
		}
		if (onEast)
			destroyChorus(start.clone().add(BlockFace.EAST.getModX(), 0, BlockFace.EAST.getModZ()), items, dropItems, 0);
		if (onNorth)
			destroyChorus(start.clone().add(BlockFace.NORTH.getModX(), 0, BlockFace.NORTH.getModZ()), items, dropItems, 0);
		if (onSouth)
			destroyChorus(start.clone().add(BlockFace.SOUTH.getModX(), 0, BlockFace.SOUTH.getModZ()), items, dropItems, 0);
		if (onWest)
			destroyChorus(start.clone().add(BlockFace.WEST.getModX(), 0, BlockFace.WEST.getModZ()), items, dropItems, 0);
	}

	private void destroyChorus(Position start, LootTable items, boolean dropItems, int direction) {
		IBlockData blockData = (IBlockData) start.getIBlockData();
		if (blockData.getBukkitMaterial() == Material.CHORUS_FLOWER || blockData.getBukkitMaterial() == Material.POPPED_CHORUS_FRUIT) {
			if (dropItems)
				for (ItemStack item : start.getBlock().getDrops())
					items.add(item);
			removeBlock(start, false);
			return;
		}
		if (!(blockData.b() instanceof BlockChorusFruit))
			return;
		if (dropItems)
			for (ItemStack item : start.getBlock().getDrops())
				items.add(item);
		removeBlock(start, false);
		boolean onEast = blockData.c(east);
		boolean onNorth = blockData.c(north);
		boolean onSouth = blockData.c(south);
		boolean onWest = blockData.c(west);
		boolean onTop = blockData.c(up);
		if (onTop) {
			destroyChorus(start.clone().add(0, 1, 0), items, dropItems, 1);
			return;
		}
		if (direction == 0)
			return; // 2x to the side?
		if (onEast)
			destroyChorus(start.clone().add(BlockFace.EAST.getModX(), 0, BlockFace.EAST.getModZ()), items, dropItems, 0);
		if (onNorth)
			destroyChorus(start.clone().add(BlockFace.NORTH.getModX(), 0, BlockFace.NORTH.getModZ()), items, dropItems, 0);
		if (onSouth)
			destroyChorus(start.clone().add(BlockFace.SOUTH.getModX(), 0, BlockFace.SOUTH.getModZ()), items, dropItems, 0);
		if (onWest)
			destroyChorus(start.clone().add(BlockFace.WEST.getModX(), 0, BlockFace.WEST.getModZ()), items, dropItems, 0);
	}

	private void removeEntitiesFrom(Position pos, Position clone, LootTable items, boolean painting) {
		Chunk chunk = (Chunk) clone.getNMSChunk();
		if (Ref.getClass("io.papermc.paper.chunk.system.scheduling.NewChunkHolder") != null)
			for (org.bukkit.entity.Entity entity : chunk.getChunkHolder().getEntityChunk().getChunkEntities())
				removeEntity(pos, items, entity, painting);
		else {
			@SuppressWarnings("unchecked")
			PersistentEntitySectionManager<Entity> entityManager = (PersistentEntitySectionManager<Entity>) Ref.get(((CraftWorld) pos.getWorld()).getHandle(), "P");
			for (org.bukkit.entity.Entity entity : entityManager.getEntities(new ChunkCoordIntPair(clone.getBlockX() >> 4, clone.getBlockZ() >> 4)).stream().map(Entity::getBukkitEntity)
					.filter(Objects::nonNull).toArray(paramInt -> new org.bukkit.entity.Entity[paramInt]))
				removeEntity(pos, items, entity, painting);
		}
	}

	private void destroyAround(Material blockType, EnumDirection dir, Position pos, Player player, LootTable items, boolean dropItems) {
		// top
		Position clone = pos.clone().add(0, 1, 0);
		IBlockData blockData = (IBlockData) clone.getIBlockData();
		Material type = blockData.getBukkitMaterial();
		if (!shouldSkip(type)) {
			removeEntitiesFrom(pos, clone, items, false);
			if (type == Material.NETHER_PORTAL) {
				clone.setAirAndUpdate(false);
				removeAllSurroundingPortals(clone);
			} else if (type == Material.POINTED_DRIPSTONE) { // Dripstones pointed up remove without drops
				EnumDirection vert = blockData.c(vertical_direction);
				while (type == Material.POINTED_DRIPSTONE && vert == EnumDirection.b) {
					removeBlock(clone, isWaterlogged(blockData));
					vert = blockData.c(vertical_direction);
					clone.setY(clone.getY() + 1);
					type = ((IBlockData) clone.getIBlockData()).getBukkitMaterial();
				}
			} else if (isAmethyst(type)) {
				if (dropItems)
					for (ItemStack item : clone.getBlock().getDrops())
						items.add(item);
				clone.setAirAndUpdate(false);
			} else if (type.name().endsWith("_LEAVES") && Loader.TICK_LEAVES) {
				BlockLeaves c = (BlockLeaves) blockData.b();
				c.a(blockData, EnumDirection.a((BlockPosition) pos.getBlockPosition()), Blocks.a.m(), ((CraftWorld) clone.getWorld()).getHandle(), (BlockPosition) clone.getBlockPosition(),
						(BlockPosition) clone.getBlockPosition());
			} else if (type == Material.CHORUS_PLANT)
				destroyChorusInit(clone, blockData, items, dropItems, true);
			else if (isBed(type))
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
			} else if (isDoubleBlock(type)) { // plant or door
				if (dropItems)
					for (ItemStack item : clone.getBlock().getDrops())
						items.add(item);
				destroyDoubleBlock(isWaterlogged(blockData), player, clone, blockData, items, dropItems);
			} else if (blockData.b() instanceof BlockFacingHorizontal || blockData.b() instanceof BlockCoralFanWallAbstract) {
				if (blockData.c(attach) == BlockPropertyAttachPosition.a) {
					if (dropItems)
						for (ItemStack item : clone.getBlock().getDrops())
							items.add(item);
					removeBlock(clone, isWaterlogged(blockData));
				}
			} else if (type.name().endsWith("_SIGN")
					|| !type.isSolid() && !type.name().contains("WALL_") && !(type == Material.WEEPING_VINES || type == Material.WEEPING_VINES_PLANT) && !type.name().endsWith("_HEAD")) {
				if (dropItems)
					for (ItemStack item : clone.getBlock().getDrops())
						items.add(item);
				removeBlock(clone, isWaterlogged(blockData));
			}
		}
		// sides
		for (BlockFace face : faces) {
			clone = pos.clone().add(face.getModX(), face.getModY(), face.getModZ());
			blockData = (IBlockData) clone.getIBlockData();
			type = blockData.getBukkitMaterial();
			if (shouldSkip(type)) // fast skip
				continue;
			removeEntitiesFrom(pos, clone, items, true);
			if (blockData.b() instanceof BlockTall tall) {
				int stateId = 0;
				for (IBlockState<Boolean> state : BLOCK_ROTATIONS) {
					BlockFace bface = stateId == 0 ? BlockFace.EAST : stateId == 1 ? BlockFace.NORTH : stateId == 2 ? BlockFace.SOUTH : BlockFace.WEST;
					bface = bface.getOppositeFace();
					++stateId;
					if (clone.getBlockX() - bface.getModX() == pos.getBlockX() && clone.getBlockZ() - bface.getModZ() == pos.getBlockZ()) {
						blockData = blockData.a(state, false);
						BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), blockData);
						BukkitLoader.getPacketHandler().send(clone.getWorld().getPlayers(), BukkitLoader.getNmsProvider().packetBlockChange(clone, blockData));
						break;
					}
				}
			} else if (blockData.b() instanceof BlockCobbleWall wall) {
				blockData = Block.b(blockData, (GeneratorAccess) ((CraftWorld) clone.getWorld()).getHandle(), (BlockPosition) clone.getBlockPosition());
				BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), blockData);
				BukkitLoader.getPacketHandler().send(clone.getWorld().getPlayers(), BukkitLoader.getNmsProvider().packetBlockChange(clone, blockData));
			} else if (type == Material.NETHER_PORTAL) {
				clone.setAirAndUpdate(false);
				removeAllSurroundingPortals(clone);
			} else if (isAmethyst(type)) {
				if (dropItems)
					for (ItemStack item : clone.getBlock().getDrops())
						items.add(item);
				clone.setAirAndUpdate(false);
			} else if (type.name().endsWith("_LEAVES") && Loader.TICK_LEAVES) {
				BlockLeaves c = (BlockLeaves) blockData.b();
				c.a(blockData, EnumDirection.a((BlockPosition) pos.getBlockPosition()), Blocks.a.m(), ((CraftWorld) clone.getWorld()).getHandle(), (BlockPosition) clone.getBlockPosition(),
						(BlockPosition) clone.getBlockPosition());
			} else if (blockData.b() instanceof BlockRedstoneTorchWall || blockData.b() instanceof BlockWallSign || blockData.b() instanceof BlockTorchWall || blockData.b() instanceof BlockBannerWall
					|| !Loader.LADDER_WORKS_AS_VINE && blockData.b() instanceof BlockLadder) {
				BlockFace bface = BlockFace.valueOf(blockData.c(direction).name());
				if (clone.getBlockX() - bface.getModX() == pos.getBlockX() && clone.getBlockZ() - bface.getModZ() == pos.getBlockZ()) {
					if (dropItems)
						for (ItemStack item : clone.getBlock().getDrops())
							items.add(item);
					removeBlock(clone, isWaterlogged(blockData));
				}
			} else if (blockData.b() instanceof BlockFacingHorizontal || blockData.b() instanceof BlockCoralFanWallAbstract) {
				if (type == Material.COCOA || blockData.c(attach) == BlockPropertyAttachPosition.b) {
					BlockFace bface = BlockFace.valueOf(blockData.c(direction).name());
					if (type == Material.COCOA)
						bface = bface.getOppositeFace();
					if (clone.getBlockX() - bface.getModX() == pos.getBlockX() && clone.getBlockZ() - bface.getModZ() == pos.getBlockZ()) {
						if (dropItems)
							for (ItemStack item : clone.getBlock().getDrops())
								items.add(item);
						removeBlock(clone, isWaterlogged(blockData));
					}
				}
			} else if ((type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN) && !blockBehindOrAbove(clone, blockData)) {
				removeBlock(clone, isWaterlogged(blockData));
				clone.setY(clone.getY() - 1);
				blockData = (IBlockData) clone.getIBlockData();
				type = blockData.getBukkitMaterial();
				if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN)
					destroyVine(clone, blockData);
			} else if (Loader.LADDER_WORKS_AS_VINE && blockData.b() instanceof BlockLadder && !blockBehindOrAboveLadder(clone, blockData)) {
				if (dropItems)
					items.add(new ItemStack(Material.LADDER));
				removeBlock(clone, isWaterlogged(blockData));
				clone.setY(clone.getY() - 1);
				blockData = (IBlockData) clone.getIBlockData();
				type = blockData.getBukkitMaterial();
				if (type == Material.LADDER)
					destroyLadder(clone, items, dropItems, blockData);
			}
		}

		// down
		clone = pos.clone().add(0, -1, 0);
		blockData = (IBlockData) clone.getIBlockData();
		type = blockData.getBukkitMaterial();
		if (!shouldSkip(type)) {
			removeEntitiesFrom(pos, clone, items, false);
			if (type == Material.NETHER_PORTAL) {
				clone.setAirAndUpdate(false);
				removeAllSurroundingPortals(clone);
			} else if (type == Material.POINTED_DRIPSTONE) { // Dripstones pointed down "drop" down
				EnumDirection vert = blockData.c(vertical_direction);
				while (type == Material.POINTED_DRIPSTONE && vert == EnumDirection.a) {
					BlockPosition bPos = (BlockPosition) clone.getBlockPosition();
					IBlockData data = blockData;
					WorldServer world = ((CraftWorld) clone.getWorld()).getHandle();
					if (Bukkit.isPrimaryThread()) {
						EntityFallingBlock dripstone = EntityFallingBlock.a(world, bPos, data);
						int i = Math.max(1 + bPos.v() - bPos.i().v(), 6);
						float f = 1.0F * i;
						dripstone.b(f, 40);
					} else
						BukkitLoader.getNmsProvider().postToMainThread(() -> {
							EntityFallingBlock dripstone = EntityFallingBlock.a(world, bPos, data);
							int i = Math.max(1 + bPos.v() - bPos.i().v(), 6);
							float f = 1.0F * i;
							dripstone.b(f, 40);
						});
					removeBlock(clone, isWaterlogged(blockData));
					vert = blockData.c(vertical_direction);
					clone.setY(clone.getY() - 1);
					blockData = (IBlockData) clone.getIBlockData();
					type = blockData.getBukkitMaterial();
				}
			} else if (isAmethyst(type)) {
				if (dropItems)
					for (ItemStack item : clone.getBlock().getDrops())
						items.add(item);
				clone.setAirAndUpdate(false);
			} else if (type.name().endsWith("_LEAVES") && Loader.TICK_LEAVES) {
				BlockLeaves c = (BlockLeaves) blockData.b();
				c.a(blockData, EnumDirection.a((BlockPosition) pos.getBlockPosition()), Blocks.a.m(), ((CraftWorld) clone.getWorld()).getHandle(), (BlockPosition) clone.getBlockPosition(),
						(BlockPosition) clone.getBlockPosition());
			} else if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN) {
				if (!blockBehindOrAbove(clone, blockData)) {
					removeBlock(clone, isWaterlogged(blockData));
					clone.setY(clone.getY() - 1);
					blockData = (IBlockData) clone.getIBlockData();
					type = blockData.getBukkitMaterial();
					if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN)
						destroyVine(clone, blockData);
				}
			} else if (blockData.b() instanceof BlockFacingHorizontal || blockData.b() instanceof BlockCoralFanWallAbstract) {
				if (blockData.c(attach) == BlockPropertyAttachPosition.c) {
					if (dropItems)
						for (ItemStack item : clone.getBlock().getDrops())
							items.add(item);
					removeBlock(clone, isWaterlogged(blockData));
				}
			} else if (type == Material.WEEPING_VINES || type == Material.WEEPING_VINES_PLANT)
				while (type == Material.WEEPING_VINES || type == Material.WEEPING_VINES_PLANT) {
					if (dropItems)
						for (ItemStack item : clone.getBlock().getDrops())
							items.add(item);
					clone.setAirAndUpdate(false);
					clone.setY(clone.getY() - 1);
					type = ((IBlockData) clone.getIBlockData()).getBukkitMaterial();
				}
		}
	}

	private boolean isAmethyst(Material type) {
		return type == Material.AMETHYST_CLUSTER || type == Material.LARGE_AMETHYST_BUD || type == Material.MEDIUM_AMETHYST_BUD || type == Material.SMALL_AMETHYST_BUD
				|| type == Material.BUDDING_AMETHYST;
	}

	public boolean shouldSkip(Material type) {
		return type == Material.WATER || type == Material.LAVA || type.isAir();
	}

	private void removeAllSurroundingPortals(Position pos) {
		for (BlockFace face : all_faces) {
			Position clone = pos.clone().add(face.getModX(), face.getModY(), face.getModZ());
			IBlockData blockData = (IBlockData) clone.getIBlockData();
			Material type = blockData.getBukkitMaterial();
			if (type == Material.NETHER_PORTAL) {
				clone.setAirAndUpdate(false);
				removeAllSurroundingPortals(clone);
			}
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

	private boolean blockBehindOrAboveLadder(Position pos, IBlockData blockData) {
		if (pos.clone().add(0, 1, 0).getBukkitType() == Material.LADDER || isSolid(
				pos.clone().add(BlockFace.valueOf(blockData.c(direction).name()).getOppositeFace().getModX(), 0, BlockFace.valueOf(blockData.c(direction).name()).getOppositeFace().getModZ())))
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

	private void destroyLadder(Position pos, LootTable loot, boolean dropItems, IBlockData blockData) {
		if (blockBehindOrAboveLadder(pos, blockData))
			return;

		removeBlock(pos, isWaterlogged(blockData));
		pos = pos.clone().setY(pos.getY() - 1);
		blockData = (IBlockData) pos.getIBlockData();
		Material type = blockData.getBukkitMaterial();
		if (dropItems)
			loot.add(new ItemStack(Material.LADDER));
		if (type == Material.LADDER)
			destroyLadder(pos, loot, dropItems, blockData);
	}

	private static void destroyBed(Player player, Position clone, IBlockData blockData, LootTable items, boolean dropItems) {
		clone.setAirAndUpdate(false);
		BlockFace face = BlockFace.valueOf(blockData.c(direction).name());
		if (blockData.c(bedpart) == BlockPropertyBedPart.a)
			clone.add(face.getOppositeFace().getModX(), 0, face.getOppositeFace().getModZ());
		else {
			clone.add(face.getModX(), 0, face.getModZ());
			if (dropItems)
				for (ItemStack item : clone.getBlock().getDrops())
					items.add(item);
		}
		clone.setAirAndUpdate(false);
	}

	private void destroyChest(Player player, Position pos, IBlockData iblockdata, LootTable items, boolean dropItems) {
		BlockPropertyChestType chesttype = iblockdata.c(chestType);
		BlockFace face = BlockFace.valueOf(iblockdata.c(direction).name());
		if (dropItems) {
			TileEntityChest tile = (TileEntityChest) ((Chunk) pos.getNMSChunk()).c_((BlockPosition) pos.getBlockPosition());
			if (tile != null)
				for (net.minecraft.world.item.ItemStack nmsItem : tile.getContents())
					items.add(CraftItemStack.asBukkitCopy(nmsItem));
		}

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
			TileEntityChest tile = (TileEntityChest) ((Chunk) pos.getNMSChunk()).c_((BlockPosition) clone.getBlockPosition());
			NBTTagCompound tag = tile.o();
			removeBlock(pos, isWaterlogged(iblockdata));
			IBlockData data = (IBlockData) clone.getIBlockData();
			if (data.getBukkitMaterial() == Material.CHEST || data.getBukkitMaterial() == Material.TRAPPED_CHEST) {
				data = data.a(chestType, BlockPropertyChestType.a);
				BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), data);
				tile = (TileEntityChest) ((Chunk) pos.getNMSChunk()).c_((BlockPosition) clone.getBlockPosition());
				tile.a(tag);
				BukkitLoader.getPacketHandler().send(clone.getWorld().getPlayers(), BukkitLoader.getNmsProvider().packetBlockChange(clone, data));
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
			TileEntityChest tile = (TileEntityChest) ((Chunk) pos.getNMSChunk()).c_((BlockPosition) clone.getBlockPosition());
			NBTTagCompound tag = tile.o();
			removeBlock(pos, isWaterlogged(iblockdata));
			IBlockData data = (IBlockData) clone.getIBlockData();
			if (data.getBukkitMaterial() == Material.CHEST || data.getBukkitMaterial() == Material.TRAPPED_CHEST) {
				data = data.a(chestType, BlockPropertyChestType.a);
				BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), data);
				tile = (TileEntityChest) ((Chunk) pos.getNMSChunk()).c_((BlockPosition) clone.getBlockPosition());
				tile.a(tag);
				BukkitLoader.getPacketHandler().send(clone.getWorld().getPlayers(), BukkitLoader.getNmsProvider().packetBlockChange(clone, data));
			}
		} else
			removeBlock(pos, isWaterlogged(iblockdata));
	}

	private void destroyDoubleBlock(boolean water, Player player, Position pos, IBlockData iblockdata, LootTable items, boolean dropItems) {
		removeBlock(pos, water);
		if (iblockdata.c(doubleHalf) == BlockPropertyDoubleBlockHalf.b) {
			Position clone = pos.clone().add(0, 1, 0);
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
		LootTable loot = breakEvent.getLoot();
		// Add loot from block to the LootTable
		if (breakEvent.isDropItems())
			for (ItemStack item : pos.getBlock().getDrops(player.getItemInHand(), player))
				loot.add(item);

		// Destroy block/s
		Material material = iblockdata.getBukkitMaterial();
		if (material == Material.CHEST || material == Material.TRAPPED_CHEST) {
			destroyChest(player, pos, iblockdata, loot, breakEvent.doTileDrops());
			pos.updatePhysics(Blocks.a.m());
		}
		if (breakEvent.doTileDrops() && iblockdata.b() instanceof BlockTileEntity) {
			Object prev = pos.getIBlockData();
			TileEntity blockEntity = ((Chunk) pos.getNMSChunk()).c_((BlockPosition) pos.getBlockPosition());
			TileEntityContainer inv = blockEntity instanceof TileEntityContainer ? (TileEntityContainer) blockEntity : null;
			if (inv != null && !(inv instanceof TileEntityShulkerBox))
				for (net.minecraft.world.item.ItemStack item : inv.getContents())
					loot.add(CraftItemStack.asBukkitCopy(item));
			pos.setAirAndUpdate(false);
			destroyAround(material, packet.c(), pos, player, loot, breakEvent.isDropItems());
			pos.updatePhysics(prev);
		} else if (isDoubleBlock(material)) { // plant or door
			destroyDoubleBlock(isWaterlogged(iblockdata), player, pos, iblockdata, loot, breakEvent.isDropItems());
			pos.updatePhysics(Blocks.a.m());
		} else if (isBed(material)) {
			destroyBed(player, pos, iblockdata, loot, breakEvent.isDropItems());
			pos.updatePhysics(Blocks.a.m());
		} else {
			Object prev = pos.getIBlockData();
			// Set block to air/water & update nearby blocks
			removeBlock(pos, isWaterlogged(iblockdata));
			if (material == Material.NETHER_PORTAL)
				removeAllSurroundingPortals(pos);
			else if (Loader.LADDER_WORKS_AS_VINE && material == Material.LADDER) {
				Position clone = pos.clone();
				clone.setY(clone.getY() - 1);
				IBlockData blockData = (IBlockData) clone.getIBlockData();
				Material type = blockData.getBukkitMaterial();
				if (type == Material.LADDER)
					destroyLadder(clone, loot, breakEvent.isDropItems(), blockData);
			} else if (material == Material.VINE || material == Material.CAVE_VINES || material == Material.GLOW_LICHEN) {
				Position clone = pos.clone();
				clone.setY(clone.getY() - 1);
				IBlockData blockData = (IBlockData) clone.getIBlockData();
				Material type = blockData.getBukkitMaterial();
				if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.GLOW_LICHEN)
					destroyVine(clone, blockData);
			} else if (material == Material.WEEPING_VINES || material == Material.WEEPING_VINES_PLANT) {
				fixPlantIfType(pos, material);
				Position clone = pos.clone();
				clone.setY(clone.getY() - 1);
				Material type = ((IBlockData) clone.getIBlockData()).getBukkitMaterial();
				while (type == Material.WEEPING_VINES || type == Material.WEEPING_VINES_PLANT) {
					if (breakEvent.isDropItems())
						for (ItemStack item : clone.getBlock().getDrops())
							loot.add(item);
					clone.setAirAndUpdate(false);
					clone.setY(clone.getY() - 1);
					type = ((IBlockData) clone.getIBlockData()).getBukkitMaterial();
				}
			} else if (material == Material.CHORUS_FLOWER || material == Material.POPPED_CHORUS_FRUIT) {
				Position clone = pos.clone();
				// top?
				clone.add(0, -1, 0);
				iblockdata = (IBlockData) clone.getIBlockData();
				if (iblockdata.b() instanceof BlockChorusFruit) {
					boolean on = iblockdata.c(up);
					if (on) {
						iblockdata = iblockdata.a(up, false);
						BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), iblockdata);
						BukkitLoader.getPacketHandler().send(clone.getWorld().getPlayers(), BukkitLoader.getNmsProvider().packetBlockChange(clone, iblockdata));
						return;
					}
				}
				// east?
				clone.add(BlockFace.NORTH.getModX(), 0, BlockFace.NORTH.getModZ());
				iblockdata = (IBlockData) clone.getIBlockData();
				if (iblockdata.b() instanceof BlockChorusFruit) {
					boolean on = iblockdata.c(east);
					if (on) {
						iblockdata = iblockdata.a(east, false);
						BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), iblockdata);
						BukkitLoader.getPacketHandler().send(clone.getWorld().getPlayers(), BukkitLoader.getNmsProvider().packetBlockChange(clone, iblockdata));
						return;
					}
				}
				// north?
				clone.add(BlockFace.EAST.getModX(), 0, BlockFace.EAST.getModZ());
				iblockdata = (IBlockData) clone.getIBlockData();
				if (iblockdata.b() instanceof BlockChorusFruit) {
					boolean on = iblockdata.c(north);
					if (on) {
						iblockdata = iblockdata.a(north, false);
						BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), iblockdata);
						BukkitLoader.getPacketHandler().send(clone.getWorld().getPlayers(), BukkitLoader.getNmsProvider().packetBlockChange(clone, iblockdata));
						return;
					}
				}
				// west?
				clone.add(BlockFace.SOUTH.getModX(), 0, BlockFace.SOUTH.getModZ());
				iblockdata = (IBlockData) clone.getIBlockData();
				if (iblockdata.b() instanceof BlockChorusFruit) {
					boolean on = iblockdata.c(west);
					if (on) {
						iblockdata = iblockdata.a(west, false);
						BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), iblockdata);
						BukkitLoader.getPacketHandler().send(clone.getWorld().getPlayers(), BukkitLoader.getNmsProvider().packetBlockChange(clone, iblockdata));
						return;
					}
				}
				// south?
				clone.add(BlockFace.WEST.getModX(), 0, BlockFace.WEST.getModZ());
				iblockdata = (IBlockData) clone.getIBlockData();
				if (iblockdata.b() instanceof BlockChorusFruit) {
					boolean on = iblockdata.c(south);
					if (on) {
						iblockdata = iblockdata.a(south, false);
						BukkitLoader.getNmsProvider().setBlock(clone.getNMSChunk(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ(), iblockdata);
						BukkitLoader.getPacketHandler().send(clone.getWorld().getPlayers(), BukkitLoader.getNmsProvider().packetBlockChange(clone, iblockdata));
						return;
					}
				}
			} else if (material == Material.CHORUS_PLANT)
				destroyChorusInit(pos, iblockdata, loot, breakEvent.isDropItems(), false);
			else if (material == Material.TWISTING_VINES || material == Material.TWISTING_VINES_PLANT || material == Material.SUGAR_CANE || material == Material.BAMBOO || material == Material.KELP
					|| material == Material.KELP_PLANT) {
				Position clone = pos.clone();
				clone.setY(clone.getY() + 1);
				Material type = ((IBlockData) clone.getIBlockData()).getBukkitMaterial();
				while (type == Material.TWISTING_VINES || type == Material.TWISTING_VINES_PLANT || type == Material.SUGAR_CANE || type == Material.BAMBOO || type == Material.KELP
						|| type == Material.KELP_PLANT) {
					if (breakEvent.isDropItems())
						for (ItemStack item : clone.getBlock().getDrops())
							loot.add(item);
					removeBlock(clone, type == Material.KELP || type == Material.KELP_PLANT);
					clone.setY(clone.getY() + 1);
					type = ((IBlockData) clone.getIBlockData()).getBukkitMaterial();
				}
				fixPlantIfType(pos, material);
			} else if (material.isSolid() && !material.isAir() && !material.name().contains("WALL_"))
				destroyAround(material, packet.c(), pos, player, loot, breakEvent.isDropItems());
			pos.updatePhysics(prev);
		}
		// Damage tool
		net.minecraft.world.item.ItemStack itemInHand = nmsPlayer.b(EnumHand.a);
		short maxDamage = CraftMagicNumbers.getMaterial(itemInHand.c()).getMaxDurability();
		if (maxDamage > 0) { // Is tool/armor
			int damage = damageTool(nmsPlayer, itemInHand, itemInHand.u != null && itemInHand.u.q("Unbreakable") ? 0 : 1);
			if (damage > 0)
				if (itemInHand.j() + damage >= CraftMagicNumbers.getMaterial(itemInHand.c()).getMaxDurability())
					nmsPlayer.a(EnumHand.a, net.minecraft.world.item.ItemStack.b);
				else
					itemInHand.b(itemInHand.j() + damage);
		}
		// Packet response
		BukkitLoader.getPacketHandler().send(player, new ClientboundBlockChangedAckPacket(packet.e()));

		// Drop items & exp
		Location dropLoc = pos.add(0.5, 0, 0.5).toLocation();
		if (!loot.getItems().isEmpty() && breakEvent.isDropItems() || breakEvent.getExpToDrop() > 0)
			if (Bukkit.isPrimaryThread()) {

				// Do not call event if isn't registered any listener
				if (BlockBreakDropItemsEvent.getHandlerList().getRegisteredListeners().length == 0) {
					if (!loot.getItems().isEmpty() && breakEvent.isDropItems())
						for (ItemStack drop : loot.getItems())
							player.getWorld().dropItem(dropLoc, drop, breakEvent.getItemConsumer());
					if (breakEvent.getExpToDrop() > 0)
						player.getWorld().spawn(dropLoc, EntityType.EXPERIENCE_ORB.getEntityClass(), c -> {
							ExperienceOrb orb = (ExperienceOrb) c;
							orb.setExperience(breakEvent.getExpToDrop());
						});
					return;
				}
				if (!loot.getItems().isEmpty() && breakEvent.isDropItems()) {
					BlockBreakDropItemsEvent event = new BlockBreakDropItemsEvent(breakEvent, loot);
					Bukkit.getPluginManager().callEvent(event);
					if (!event.isCancelled())
						for (ItemStack drop : event.getLoot().getItems())
							player.getWorld().dropItem(dropLoc, drop, breakEvent.getItemConsumer());
				}
				if (breakEvent.getExpToDrop() > 0)
					player.getWorld().spawn(dropLoc, EntityType.EXPERIENCE_ORB.getEntityClass(), c -> {
						ExperienceOrb orb = (ExperienceOrb) c;
						orb.setExperience(breakEvent.getExpToDrop());
					});
			} else
				MinecraftServer.getServer().execute(() -> {

					// Do not call event if isn't registered any listener
					if (BlockBreakDropItemsEvent.getHandlerList().getRegisteredListeners().length == 0) {
						if (!loot.getItems().isEmpty() && breakEvent.isDropItems())
							for (ItemStack drop : loot.getItems())
								player.getWorld().dropItem(dropLoc, drop, breakEvent.getItemConsumer());
						if (breakEvent.getExpToDrop() > 0)
							player.getWorld().spawn(dropLoc, EntityType.EXPERIENCE_ORB.getEntityClass(), c -> {
								ExperienceOrb orb = (ExperienceOrb) c;
								orb.setExperience(breakEvent.getExpToDrop());
							});
						return;
					}
					if (!loot.getItems().isEmpty() && breakEvent.isDropItems()) {
						BlockBreakDropItemsEvent event = new BlockBreakDropItemsEvent(breakEvent, loot);
						Bukkit.getPluginManager().callEvent(event);
						if (!event.isCancelled())
							for (ItemStack drop : event.getLoot().getItems())
								player.getWorld().dropItem(dropLoc, drop, breakEvent.getItemConsumer());
					}
					if (breakEvent.getExpToDrop() > 0)
						player.getWorld().spawn(dropLoc, EntityType.EXPERIENCE_ORB.getEntityClass(), c -> {
							ExperienceOrb orb = (ExperienceOrb) c;
							orb.setExperience(breakEvent.getExpToDrop());
						});
				});
	}

	private int damageTool(EntityPlayer player, net.minecraft.world.item.ItemStack item, int damage) {
		int enchant = EnchantmentManager.a(Enchantments.w, item);

		if (enchant > 0 && EnchantmentDurability.a(item, enchant, player.dQ()))
			--damage;

		PlayerItemDamageEvent event = new PlayerItemDamageEvent(player.getBukkitEntity(), CraftItemStack.asCraftMirror(item), damage);
		if (!Bukkit.isPrimaryThread())
			Ref.set(event, async, true);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled())
			return 0;

		return event.getDamage();
	}

	private void processBlockBreak(PacketPlayInBlockDig packet, Player player, EntityPlayer nmsPlayer, IBlockData iblockdata, BlockPosition blockPos, Position pos, boolean dropItems,
			boolean instantlyBroken) {
		AsyncBlockBreakEvent event = new AsyncBlockBreakEvent(pos, player, BukkitLoader.getNmsProvider().toMaterial(iblockdata), instantlyBroken, BlockFace.valueOf(packet.c().name()));
		event.setTileDrops(!Loader.DISABLE_TILE_DROPS);
		event.setDropItems(dropItems);
		if (instantlyBroken) {
			PlayerInteractEvent interactEvent = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, player.getItemInHand(), pos.getBlock(), event.getBlockFace());
			if (PlayerInteractEvent.getHandlerList().getRegisteredListeners().length > 0) {
				if (Loader.SYNC_EVENT) {
					BukkitLoader.getNmsProvider().postToMainThread(() -> {
						Bukkit.getPluginManager().callEvent(interactEvent);
						if (interactEvent.isCancelled() || interactEvent.useInteractedBlock() == Result.DENY) {
							sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
							return;
						}
						// Drop exp only in survival / adventure gamemode
						if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL)
							if (nmsPlayer.d(iblockdata.b().m()))
								event.setExpToDrop(iblockdata.b().getExpDrop(iblockdata, nmsPlayer.x(), blockPos, CraftItemStack.asNMSCopy(player.getItemInHand()), true));

						// Do not call event if isn't registered any listener - instantly process async
						if (BlockExpEvent.getHandlerList().getRegisteredListeners().length == 0) {
							Ref.set(event, async, true);
							breakBlock(player, pos, iblockdata, nmsPlayer, packet, event);
							return;
						}
						Bukkit.getPluginManager().callEvent(event);
						if (event.isCancelled()) {
							sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
							return;
						}
						breakBlock(player, pos, iblockdata, nmsPlayer, packet, event);
					});
					return;
				}
				Ref.set(interactEvent, async, true);
				Bukkit.getPluginManager().callEvent(interactEvent);
				if (event.isCancelled()) {
					sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
					return;
				}
			}
		}

		// Drop exp only in survival / adventure gamemode
		if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL)
			if (nmsPlayer.d(iblockdata.b().m()))
				event.setExpToDrop(iblockdata.b().getExpDrop(iblockdata, nmsPlayer.x(), blockPos, CraftItemStack.asNMSCopy(player.getItemInHand()), true));

		// Do not call event if isn't registered any listener - instantly process async
		if (BlockExpEvent.getHandlerList().getRegisteredListeners().length == 0) {
			Ref.set(event, async, true);
			breakBlock(player, pos, iblockdata, nmsPlayer, packet, event);
			return;
		}

		if (Loader.SYNC_EVENT)
			BukkitLoader.getNmsProvider().postToMainThread(() -> {
				Bukkit.getPluginManager().callEvent(event);
				if (event.isCancelled()) {
					sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
					return;
				}
				breakBlock(player, pos, iblockdata, nmsPlayer, packet, event);
			});
		else {
			Ref.set(event, async, true);
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
				return;
			}
			breakBlock(player, pos, iblockdata, nmsPlayer, packet, event);
		}
	}

	@Override
	public boolean handle(String playerName, Object packetObject) {
		PacketPlayInBlockDig packet = (PacketPlayInBlockDig) packetObject;
		if (packet.d() == EnumPlayerDigType.c) { // stop
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
			// iblockdata.a(nmsPlayer.s, blockPos, nmsPlayer); // hit block
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
		return !data.b(waterlogged) ? false : data.c(waterlogged);
	}
}
