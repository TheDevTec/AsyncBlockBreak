package me.devtec.asyncblockbreak.providers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import me.devtec.asyncblockbreak.Loader;
import me.devtec.asyncblockbreak.api.LootTable;
import me.devtec.asyncblockbreak.providers.math.ThreadAccessRandomSource;
import me.devtec.asyncblockbreak.utils.BlockActionContext;
import me.devtec.shared.Ref;
import me.devtec.theapi.bukkit.game.Position;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.EnumDirection.EnumAxis;
import net.minecraft.core.IRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockBarrel;
import net.minecraft.world.level.block.BlockChest;
import net.minecraft.world.level.block.BlockChorusFruit;
import net.minecraft.world.level.block.BlockCobbleWall;
import net.minecraft.world.level.block.BlockDispenser;
import net.minecraft.world.level.block.BlockFurnace;
import net.minecraft.world.level.block.BlockHopper;
import net.minecraft.world.level.block.BlockLeaves;
import net.minecraft.world.level.block.BlockLectern;
import net.minecraft.world.level.block.BlockStairs;
import net.minecraft.world.level.block.BlockStem;
import net.minecraft.world.level.block.BlockStemAttached;
import net.minecraft.world.level.block.BlockStemmed;
import net.minecraft.world.level.block.BlockTall;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityContainer;
import net.minecraft.world.level.block.entity.TileEntityLectern;
import net.minecraft.world.level.block.entity.TileEntityShulkerBox;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockPropertyAttachPosition;
import net.minecraft.world.level.block.state.properties.BlockPropertyBedPart;
import net.minecraft.world.level.block.state.properties.BlockPropertyChestType;
import net.minecraft.world.level.block.state.properties.BlockPropertyDoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockPropertyRedstoneSide;
import net.minecraft.world.level.block.state.properties.BlockPropertyStairsShape;
import net.minecraft.world.level.block.state.properties.BlockPropertyTrackPosition;
import net.minecraft.world.level.block.state.properties.BlockPropertyWallHeight;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.IBlockState;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.storage.loot.LootTableInfo.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameters;
import net.minecraft.world.phys.Vec3D;

public class BlocksCalculator_v1_19_R1 {
	static IBlockState<EnumDirection> direction = BlockProperties.S;
	static IBlockState<BlockPropertyBedPart> bedpart = BlockProperties.bc;
	static IBlockState<BlockPropertyDoubleBlockHalf> doubleHalf = BlockProperties.ae;
	static IBlockState<BlockPropertyChestType> chestType = BlockProperties.bd;
	static IBlockState<EnumAxis> axis = BlockProperties.I;
	// vines
	static IBlockState<Boolean> east = BlockProperties.N, north = BlockProperties.M, south = BlockProperties.O, west = BlockProperties.P, up = BlockProperties.K, down = BlockProperties.L;
	static IBlockState<Boolean> waterlogged = BlockProperties.C;
	static IBlockState<EnumDirection> vertical_direction = BlockProperties.bn;
	static IBlockState<BlockPropertyWallHeight> wall_east = BlockProperties.W, wall_north = BlockProperties.X, wall_south = BlockProperties.Y, wall_west = BlockProperties.Z;
	@SuppressWarnings("unchecked")
	static IBlockState<Boolean>[] BLOCK_ROTATIONS = new IBlockState[] { east, north, south, west };
	@SuppressWarnings("unchecked")
	static IBlockState<BlockPropertyWallHeight>[] WALL_BLOCK_ROTATIONS = new IBlockState[] { wall_east, wall_north, wall_south, wall_west };
	static IBlockState<BlockPropertyAttachPosition> attach = BlockProperties.U;
	static IBlockState<DripstoneThickness> thickness = BlockProperties.bo;
	static BlockFace[] AXIS_FACES = { BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST };
	static BlockFace[] ALL_FACES = { BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP };
	static DripstoneThickness[] THICKNESS = { DripstoneThickness.b, DripstoneThickness.c, DripstoneThickness.d };
	static IBlockState<BlockPropertyRedstoneSide> north_redstone = BlockProperties.ab;
	static IBlockState<BlockPropertyRedstoneSide> east_redstone = BlockProperties.aa;
	static IBlockState<BlockPropertyRedstoneSide> south_redstone = BlockProperties.ac;
	static IBlockState<BlockPropertyRedstoneSide> west_redstone = BlockProperties.ad;

	static IBlockState<BlockPropertyStairsShape> stairsShape = BlockProperties.bj;

	static IBlockState<Boolean> PISTON_ACTIVATED = BlockProperties.g;
	static IBlockState<EnumDirection> DIRECTION = BlockProperties.Q;
	static IBlockState<BlockPropertyTrackPosition> TRACK_SHAPE = BlockProperties.ag;

	static Field stemmedField = Ref.field(BlockStemAttached.class, "d");

	private RandomSource SINGLE_THREAD_RANDOM_SOURCE;

	public BlocksCalculator_v1_19_R1(ThreadAccessRandomSource RANDOM_SOURCE) {
		SINGLE_THREAD_RANDOM_SOURCE = new ThreadAccessServerRandomSource(RANDOM_SOURCE);
	}

	public Map<Position, BlockActionContext> calculateChangedBlocks(Position destroyed, Player player, ItemStack hand) {
		Map<Position, BlockActionContext> map = new HashMap<>();
		IBlockData iblockdata = (IBlockData) destroyed.getIBlockData();

		map.put(destroyed, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(hand, destroyed, iblockdata, player)));

		Material material = iblockdata.getBukkitMaterial();
		if (iblockdata.b() instanceof BlockStairs) {
			checkAndModifyStairs(map, destroyed);
			destroyConnectableBlocks(map, player, destroyed, iblockdata);
		} else if (material == Material.RAIL)
			for (BlockFace faces : AXIS_FACES) {
				Position cloned2 = destroyed.clone().add(faces);
				iblockdata = getIBlockDataOrEmpty(map, cloned2);
				material = iblockdata.getBukkitMaterial();
				if (material == Material.RAIL)
					fixRails(map, cloned2, iblockdata);
			}
		else if (material == Material.POINTED_DRIPSTONE) {
			EnumDirection dir = iblockdata.c(vertical_direction);
			boolean pointingDown = dir != EnumDirection.b;
			fixDripstoneThickness(map, player, destroyed.clone().add(0, pointingDown ? 1 : -1, 0), !pointingDown);
			if (iblockdata.c(thickness) == DripstoneThickness.a) // tip_merge
				if (!pointingDown)
					map.put(destroyed.clone().add(0, 1, 0), BlockActionContext.updateState(iblockdata.a(thickness, DripstoneThickness.b).a(vertical_direction, EnumDirection.a))); // tip
				else
					map.put(destroyed.clone().add(0, -1, 0), BlockActionContext.updateState(iblockdata.a(thickness, DripstoneThickness.b).a(vertical_direction, EnumDirection.b))); // tip
			destroyed = destroyed.clone().add(0, pointingDown ? -1 : 1, 0);
			iblockdata = (IBlockData) destroyed.getIBlockData();
			material = iblockdata.getBukkitMaterial();
			while (material == Material.POINTED_DRIPSTONE && iblockdata.c(vertical_direction) == dir) {
				if (!pointingDown)
					map.put(destroyed, BlockActionContext.destroy(iblockdata, Material.AIR, Collections.emptyList()));
				else
					map.put(destroyed, BlockActionContext.destroyDripstone(iblockdata));
				destroyed = destroyed.clone().add(0, pointingDown ? -1 : 1, 0);
				iblockdata = (IBlockData) destroyed.getIBlockData();
				material = iblockdata.getBukkitMaterial();
			}
			if (material == Material.POINTED_DRIPSTONE && iblockdata.c(thickness) == DripstoneThickness.a) // tip_merge
				if (!pointingDown)
					map.put(destroyed, BlockActionContext.updateState(iblockdata.a(thickness, DripstoneThickness.b).a(vertical_direction, EnumDirection.a))); // tip
				else
					map.put(destroyed, BlockActionContext.updateState(iblockdata.a(thickness, DripstoneThickness.b).a(vertical_direction, EnumDirection.b))); // tip
		} else if (material == Material.PISTON || material == Material.STICKY_PISTON || material == Material.PISTON_HEAD) {
			destroyPiston(map, player, destroyed, iblockdata);
			destroyConnectableBlocks(map, player, destroyed, iblockdata);
		} else if (isChest(material)) {
			destroyChest(map, player, destroyed, iblockdata);
			destroyConnectableBlocks(map, player, destroyed, iblockdata);
		} else if (isDoubleBlock(material))
			destroyDoubleBlock(map, player, destroyed, iblockdata);
		else if (isBed(material))
			destroyBed(map, player, destroyed, iblockdata);
		else if (material == Material.NETHER_PORTAL)
			removeAllSurroundingPortals(map, destroyed);
		else if (Loader.LADDER_WORKS_AS_VINE && material == Material.LADDER) {
			destroyed = destroyed.clone();
			destroyed.setY(destroyed.getY() - 1);

			iblockdata = (IBlockData) destroyed.getIBlockData();
			material = iblockdata.getBukkitMaterial();

			if (material == Material.LADDER)
				destroyVineLadder(map, player, destroyed, iblockdata, true);
		} else if (isVine(material)) {
			destroyed = destroyed.clone();
			destroyed.setY(destroyed.getY() - 1);

			iblockdata = (IBlockData) destroyed.getIBlockData();
			material = iblockdata.getBukkitMaterial();

			if (isVine(material))
				destroyVine(map, player, destroyed, iblockdata, true);
		} else if (isWeepingVines(material)) {
			fixPlantIfType(map, destroyed.clone().add(0, 1, 0));

			destroyed = destroyed.clone();
			destroyed.setY(destroyed.getY() - 1);

			iblockdata = (IBlockData) destroyed.getIBlockData();
			material = iblockdata.getBukkitMaterial();

			if (isWeepingVines(material))
				destroyWeepingVines(map, player, destroyed, iblockdata);
		} else if (material == Material.CHORUS_FLOWER || material == Material.POPPED_CHORUS_FRUIT)
			updateChorusPlant(map, destroyed.clone());
		else if (material == Material.CHORUS_PLANT)
			destroyChorusInit(map, player, destroyed, iblockdata);
		else if (isGrowingUp(material)) {
			fixPlantIfType(map, destroyed.clone().add(0, -1, 0));

			destroyed = destroyed.clone();
			destroyed.setY(destroyed.getY() + 1);

			iblockdata = (IBlockData) destroyed.getIBlockData();
			material = iblockdata.getBukkitMaterial();

			if (isGrowingUp(material))
				destroyGrowingUp(map, player, destroyed, iblockdata);
		} else if (material.isSolid() && !isWallBlock(material)) {
			if (isContainerTile(iblockdata.b()))
				destroyContainerTile(map, player, destroyed, iblockdata);
			destroyAround(map, player, destroyed, iblockdata);
		} else if (isConnectableWallBlock(material))
			destroyConnectableBlocks(map, player, destroyed, iblockdata);
		if (material == Material.REDSTONE_WIRE) {
			updateRedstoneWire(map, destroyed, iblockdata);
			BlockActionContext existing = map.get(destroyed);
			if (existing != null)
				existing.doUpdatePhysics();
			else
				map.put(destroyed, BlockActionContext.updatePhysics());
		} else if (isConnectable(material) && !material.isSolid())
			for (BlockFace faces : AXIS_FACES) {
				Position cloned = destroyed.clone().add(faces);
				iblockdata = getIBlockDataOrEmpty(map, cloned);
				material = iblockdata.getBukkitMaterial();
				if (material == Material.REDSTONE_WIRE)
					updateRedstoneWireAt(map, cloned, iblockdata);
			}
		return map;
	}

	private void checkAndModifyStairs(Map<Position, BlockActionContext> map, Position clone) {
		Position east = clone.clone().add(BlockFace.EAST);
		Position north = clone.clone().add(BlockFace.NORTH);
		Position south = clone.clone().add(BlockFace.SOUTH);
		Position west = clone.clone().add(BlockFace.WEST);

		IBlockData ib = (IBlockData) east.getIBlockData();
		if (ib.b() instanceof BlockStairs) {
			BlockPropertyStairsShape shape = getShape(east, ib, clone);
			if (ib.c(stairsShape) != shape)
				map.put(east, BlockActionContext.updateState(ib.a(stairsShape, shape)));
		}
		ib = (IBlockData) south.getIBlockData();
		if (ib.b() instanceof BlockStairs) {
			BlockPropertyStairsShape shape = getShape(south, ib, clone);
			if (ib.c(stairsShape) != shape)
				map.put(south, BlockActionContext.updateState(ib.a(stairsShape, shape)));
		}
		ib = (IBlockData) north.getIBlockData();
		if (ib.b() instanceof BlockStairs) {
			BlockPropertyStairsShape shape = getShape(north, ib, clone);
			if (ib.c(stairsShape) != shape)
				map.put(north, BlockActionContext.updateState(ib.a(stairsShape, shape)));
		}
		ib = (IBlockData) west.getIBlockData();
		if (ib.b() instanceof BlockStairs) {
			BlockPropertyStairsShape shape = getShape(west, ib, clone);
			if (ib.c(stairsShape) != shape)
				map.put(west, BlockActionContext.updateState(ib.a(stairsShape, shape)));
		}
	}

	private BlockPropertyStairsShape getShape(Position position, IBlockData state, Position start) {
		EnumDirection direction = state.c(BlocksCalculator_v1_19_R1.direction);

		Position next = position.clone().add(direction.j(), direction.k(), direction.l());
		IBlockData blockState = next.equals(start) ? Blocks.a.m() : (IBlockData) next.getIBlockData();
		if (isStairs(blockState) && state.c(BlockProperties.af) == blockState.c(BlockProperties.af)) {
			EnumDirection direction2 = blockState.c(BlocksCalculator_v1_19_R1.direction);
			if (direction2.o() != state.c(BlocksCalculator_v1_19_R1.direction).o() && checkDirection(state, position, direction2.g())) {
				if (direction2 == direction.i())
					return BlockPropertyStairsShape.d;

				return BlockPropertyStairsShape.e;
			}
		}

		next = position.clone().add(direction.g().j(), direction.g().k(), direction.g().l());
		IBlockData blockState2 = next.equals(start) ? Blocks.a.m() : (IBlockData) next.getIBlockData();
		if (isStairs(blockState2) && state.c(BlockProperties.af) == blockState2.c(BlockProperties.af)) {
			EnumDirection direction3 = blockState2.c(BlocksCalculator_v1_19_R1.direction);
			if (direction3.o() != state.c(BlocksCalculator_v1_19_R1.direction).o() && checkDirection(state, position, direction3)) {
				if (direction3 == direction.i())
					return BlockPropertyStairsShape.b;

				return BlockPropertyStairsShape.c;
			}
		}

		return BlockPropertyStairsShape.a;
	}

	private boolean checkDirection(IBlockData state, Position pos, EnumDirection direction) {
		IBlockData blockState = (IBlockData) pos.clone().add(direction.j(), direction.k(), direction.l()).getIBlockData();
		return !isStairs(blockState) || blockState.c(BlocksCalculator_v1_19_R1.direction) != state.c(BlocksCalculator_v1_19_R1.direction)
				|| blockState.c(BlockProperties.af) != state.c(BlockProperties.af);
	}

	private boolean isStairs(IBlockData state) {
		return state.b() instanceof BlockStairs;
	}

	private boolean isContainerTile(Block b) {
		return b instanceof BlockBarrel || b instanceof BlockChest || b instanceof BlockFurnace || b instanceof BlockHopper || b instanceof BlockLectern || b instanceof BlockDispenser;
	}

	private boolean isSign(Material material) {
		switch (material) {
		case ACACIA_SIGN:
		case BIRCH_SIGN:
		case CRIMSON_SIGN:
		case DARK_OAK_SIGN:
		case JUNGLE_SIGN:
		case MANGROVE_SIGN:
		case OAK_SIGN:
		case SPRUCE_SIGN:
		case WARPED_SIGN:
			return true;
		default:
			break;
		}
		return false;
	}

	private boolean isBanner(Material material) {
		switch (material) {
		case BLACK_BANNER:
		case BLUE_BANNER:
		case BROWN_BANNER:
		case CYAN_BANNER:
		case GRAY_BANNER:
		case GREEN_BANNER:
		case LIGHT_BLUE_BANNER:
		case LIGHT_GRAY_BANNER:
		case LIME_BANNER:
		case MAGENTA_BANNER:
		case YELLOW_BANNER:
		case WHITE_BANNER:
		case RED_BANNER:
		case PURPLE_BANNER:
		case PINK_BANNER:
		case ORANGE_BANNER:
			return true;
		default:
			break;
		}
		return false;
	}

	private boolean isWallBlock(Material material) {
		if (isConnectableWallBlock(material))
			return true;
		switch (material) {
		case WALL_TORCH:
		case BRAIN_CORAL_WALL_FAN:
		case BUBBLE_CORAL_WALL_FAN:
		case DEAD_BRAIN_CORAL_WALL_FAN:
		case DEAD_BUBBLE_CORAL_WALL_FAN:
		case DEAD_FIRE_CORAL_WALL_FAN:
		case DEAD_HORN_CORAL_WALL_FAN:
		case DEAD_TUBE_CORAL_WALL_FAN:
		case FIRE_CORAL_WALL_FAN:
		case HORN_CORAL_WALL_FAN:
		case REDSTONE_WALL_TORCH:
		case SOUL_WALL_TORCH:
		case TUBE_CORAL_WALL_FAN:
			return true;
		default:
			break;
		}
		return false;
	}

	private boolean isConnectableWallBlock(Material material) {
		switch (material) {
		case ACACIA_WALL_SIGN:
		case BIRCH_WALL_SIGN:
		case BLACK_WALL_BANNER:
		case BLUE_WALL_BANNER:
		case BROWN_WALL_BANNER:
		case CRIMSON_WALL_SIGN:
		case CYAN_WALL_BANNER:
		case DARK_OAK_WALL_SIGN:
		case GRAY_WALL_BANNER:
		case GREEN_WALL_BANNER:
		case JUNGLE_WALL_SIGN:
		case LIGHT_BLUE_WALL_BANNER:
		case LIGHT_GRAY_WALL_BANNER:
		case LIME_WALL_BANNER:
		case MAGENTA_WALL_BANNER:
		case MANGROVE_WALL_SIGN:
		case OAK_WALL_SIGN:
		case ORANGE_WALL_BANNER:
		case PINK_WALL_BANNER:
		case PURPLE_WALL_BANNER:
		case RED_WALL_BANNER:
		case YELLOW_WALL_BANNER:
		case WHITE_WALL_BANNER:
		case WARPED_WALL_SIGN:
		case SPRUCE_WALL_SIGN:
			return true;
		default:
			break;
		}
		return false;
	}

	private void fixPlantIfType(Map<Position, BlockActionContext> map, Position destroyed) {
		IBlockData ib = (IBlockData) destroyed.getIBlockData();
		switch (ib.getBukkitMaterial()) {
		case TWISTING_VINES_PLANT:
			map.put(destroyed, BlockActionContext.updateState(Material.TWISTING_VINES));
			break;
		case KELP_PLANT:
			map.put(destroyed, BlockActionContext.updateState(Material.KELP));
			break;
		case WEEPING_VINES_PLANT:
			map.put(destroyed, BlockActionContext.updateState(Material.WEEPING_VINES));
			break;
		default:
			break;
		}
	}

	private void destroyConnectableBlocks(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata) {
		Position cloned = destroyed.clone().add(0, 1, 0);
		iblockdata = getIBlockDataOrEmpty(map, cloned);
		Material material = iblockdata.getBukkitMaterial();
		if (shouldSkip(material)) {
			// ignored
		} else if (isSign(material) || isBanner(material)) {
			map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
			destroyConnectableBlocks(map, player, cloned, iblockdata);
		}

		// sides
		for (BlockFace face : AXIS_FACES) {
			cloned = destroyed.clone().add(face);
			iblockdata = getIBlockDataOrEmpty(map, cloned);
			material = iblockdata.getBukkitMaterial();
			if (shouldSkip(material)) {
				// ignored
			} else if (isConnectableWallBlock(material) && BlockFace.valueOf(iblockdata.c(direction).name()) == face) {
				map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
				destroyConnectableBlocks(map, player, cloned, iblockdata);
			}
		}
	}

	private void destroyAround(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata) {
		// UP
		Position cloned = destroyed.clone().add(0, 1, 0);
		iblockdata = getIBlockDataOrEmpty(map, cloned);
		Material material = iblockdata.getBukkitMaterial();
		if (shouldSkip(material)) {
			// ignored
		} else if (isAmethyst(material) && iblockdata.c(DIRECTION) == EnumDirection.b)
			map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
		else if (material == Material.SCULK_VEIN) {
			if (!iblockdata.c(east) && !iblockdata.c(north) && !iblockdata.c(south) && !iblockdata.c(west) && !iblockdata.c(up))
				map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
			else
				map.put(cloned, BlockActionContext.updateState(iblockdata.a(down, false)));
		} else if (material == Material.RAIL)
			for (BlockFace faces : AXIS_FACES) {
				Position cloned2 = cloned.clone().add(faces);
				iblockdata = getIBlockDataOrEmpty(map, cloned2);
				material = iblockdata.getBukkitMaterial();
				if (material == Material.RAIL)
					fixRails(map, cloned2, iblockdata);
			}
		else if (Loader.TICK_LEAVES && iblockdata.b() instanceof BlockLeaves leaves) {
			BlockPosition blockPos = (BlockPosition) cloned.getBlockPosition();
			leaves.a(iblockdata, EnumDirection.a((BlockPosition) cloned.getBlockPosition()), Blocks.a.m(), ((CraftWorld) destroyed.getWorld()).getHandle(), blockPos, blockPos);
		} else if (material == Material.POINTED_DRIPSTONE) {
			fixDripstoneThickness(map, player, cloned.clone(), false);
			while (material == Material.POINTED_DRIPSTONE && iblockdata.c(vertical_direction) == EnumDirection.b) { // up
				map.put(cloned, BlockActionContext.destroy(iblockdata, Material.AIR, Collections.emptyList()));
				cloned = cloned.clone().add(0, 1, 0);
				iblockdata = getIBlockDataOrEmpty(map, cloned);
				material = iblockdata.getBukkitMaterial();
			}
			if (material == Material.POINTED_DRIPSTONE && iblockdata.c(thickness) == DripstoneThickness.a) // tip_merge
				map.put(cloned, BlockActionContext.updateState(iblockdata.a(thickness, DripstoneThickness.b).a(vertical_direction, EnumDirection.a))); // tip
		} else if (isDoubleBlock(material)) {
			map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
			destroyDoubleBlock(map, player, cloned, iblockdata);
		} else if (isBed(material)) {
			map.put(cloned, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, cloned, iblockdata, player)));
			destroyBed(map, player, cloned, iblockdata);
		} else if (material == Material.NETHER_PORTAL) {
			map.put(cloned, BlockActionContext.destroy(iblockdata, Material.AIR, Collections.emptyList()));
			removeAllSurroundingPortals(map, cloned);
		} else if (material == Material.CHORUS_FLOWER || material == Material.POPPED_CHORUS_FRUIT)
			map.put(cloned, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, cloned, iblockdata, player)));
		else if (material == Material.CHORUS_PLANT) {
			map.put(cloned, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, cloned, iblockdata, player)));
			destroyChorusInit(map, player, cloned, iblockdata);
		} else if (isGrowingUp(material)) {
			fixPlantIfType(map, cloned.clone().add(0, -1, 0));

			map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
			cloned = cloned.clone();
			cloned.setY(cloned.getY() + 1);

			iblockdata = getIBlockDataOrEmpty(map, cloned);
			material = iblockdata.getBukkitMaterial();

			if (isGrowingUp(material))
				destroyGrowingUp(map, player, cloned, iblockdata);
		} else if (!isHead(material) && material != Material.END_ROD && material != Material.COBWEB && (!material.isSolid() || isPressurePlate(material) || isSign(material) || isBanner(material)))
			if (!iblockdata.b(attach) && !isWallBlock(material) || iblockdata.b(attach) && iblockdata.c(attach) == BlockPropertyAttachPosition.a) {
				map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
				if (isSign(material) || isBanner(material))
					destroyConnectableBlocks(map, player, cloned, iblockdata);
				if (material == Material.REDSTONE_WIRE)
					updateRedstoneWire(map, cloned, iblockdata);
			}
		if (isConnectable(material)) {
			BlockActionContext existing = map.get(cloned);
			if (existing != null)
				existing.doUpdatePhysics();
			else
				map.put(cloned, BlockActionContext.updatePhysics());
			if (material == Material.REDSTONE_WIRE)
				updateRedstoneWireAt(map, cloned, iblockdata);
			else
				for (BlockFace faces : AXIS_FACES) {
					Position cloned2 = cloned.clone().add(faces);
					iblockdata = getIBlockDataOrEmpty(map, cloned2);
					material = iblockdata.getBukkitMaterial();
					if (material == Material.REDSTONE_WIRE)
						updateRedstoneWireAt(map, cloned2, iblockdata);
				}
		}

		// DOWN
		cloned = destroyed.clone().add(0, -1, 0);
		iblockdata = getIBlockDataOrEmpty(map, cloned);
		material = iblockdata.getBukkitMaterial();
		if (shouldSkip(material)) {
			// ignored
		} else if (isAmethyst(material) && iblockdata.c(DIRECTION) == EnumDirection.a)
			map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
		else if (material == Material.SCULK_VEIN) {
			if (!iblockdata.c(east) && !iblockdata.c(north) && !iblockdata.c(south) && !iblockdata.c(west) && !iblockdata.c(down))
				map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
			else
				map.put(cloned, BlockActionContext.updateState(iblockdata.a(up, false)));
		} else if (iblockdata.b() instanceof BlockCobbleWall)
			fixWalls(map, cloned, iblockdata, BlockFace.DOWN);
		else if (Loader.TICK_LEAVES && iblockdata.b() instanceof BlockLeaves leaves) {
			BlockPosition blockPos = (BlockPosition) cloned.getBlockPosition();
			leaves.a(iblockdata, EnumDirection.a((BlockPosition) cloned.getBlockPosition()), Blocks.a.m(), ((CraftWorld) destroyed.getWorld()).getHandle(), blockPos, blockPos);
		} else if (material == Material.POINTED_DRIPSTONE) {
			fixDripstoneThickness(map, player, cloned.clone(), true);
			while (material == Material.POINTED_DRIPSTONE && iblockdata.c(vertical_direction) == EnumDirection.a) { // down
				map.put(cloned, BlockActionContext.destroyDripstone(iblockdata));
				cloned = cloned.clone().add(0, -1, 0);
				iblockdata = getIBlockDataOrEmpty(map, cloned);
				material = iblockdata.getBukkitMaterial();
			}
			if (material == Material.POINTED_DRIPSTONE && iblockdata.c(thickness) == DripstoneThickness.a) // tip_merge
				map.put(cloned, BlockActionContext.updateState(iblockdata.a(thickness, DripstoneThickness.b).a(vertical_direction, EnumDirection.b))); // tip
		} else if (material == Material.NETHER_PORTAL) {
			map.put(cloned, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, cloned, iblockdata, player)));
			removeAllSurroundingPortals(map, cloned);
		} else if (isVine(material))
			destroyVine(map, player, cloned, iblockdata, false);
		else if (isWeepingVines(material)) {
			fixPlantIfType(map, cloned.clone().add(0, 1, 0));

			map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
			cloned = cloned.clone();
			cloned.setY(cloned.getY() - 1);

			iblockdata = getIBlockDataOrEmpty(map, cloned);
			material = iblockdata.getBukkitMaterial();

			if (isWeepingVines(material))
				destroyWeepingVines(map, player, cloned, iblockdata);
		} else if (material == Material.SPORE_BLOSSOM || material == Material.HANGING_ROOTS || iblockdata.b(attach) && iblockdata.c(attach) == BlockPropertyAttachPosition.c)
			map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
		if (isConnectable(material)) {
			BlockActionContext existing = map.get(cloned);
			if (existing != null)
				existing.doUpdatePhysics();
			else
				map.put(cloned, BlockActionContext.updatePhysics());
			if (material == Material.REDSTONE_WIRE)
				updateRedstoneWireAt(map, cloned, iblockdata);
			else
				for (BlockFace faces : AXIS_FACES) {
					Position cloned2 = cloned.clone().add(faces);
					iblockdata = getIBlockDataOrEmpty(map, cloned2);
					material = iblockdata.getBukkitMaterial();
					if (material == Material.REDSTONE_WIRE)
						updateRedstoneWireAt(map, cloned2, iblockdata);
				}
		}

		// sides
		for (BlockFace face : AXIS_FACES) {
			cloned = destroyed.clone().add(face);
			iblockdata = getIBlockDataOrEmpty(map, cloned);
			material = iblockdata.getBukkitMaterial();
			if (shouldSkip(material)) {
				// ignored
			} else if (iblockdata.b() instanceof BlockStemAttached) {
				if (iblockdata.c(direction) == EnumDirection.valueOf(face.name()).g()) {
					BlockStemmed stemmed = (BlockStemmed) Ref.get(iblockdata.b(), stemmedField);
					map.put(cloned, BlockActionContext.updateState(stemmed.b().m().a(BlockStem.b, 7)));
				}
			} else if (isAmethyst(material) && iblockdata.c(DIRECTION) == EnumDirection.valueOf(face.name()))
				map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
			else if (material == Material.SCULK_VEIN)
				switch (face) {
				case EAST:
					if (!iblockdata.c(down) && !iblockdata.c(north) && !iblockdata.c(south) && !iblockdata.c(east) && !iblockdata.c(up))
						map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
					else
						map.put(cloned, BlockActionContext.updateState(iblockdata.a(west, false)));
					break;
				case NORTH:
					if (!iblockdata.c(down) && !iblockdata.c(west) && !iblockdata.c(north) && !iblockdata.c(east) && !iblockdata.c(up))
						map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
					else
						map.put(cloned, BlockActionContext.updateState(iblockdata.a(south, false)));
					break;
				case SOUTH:
					if (!iblockdata.c(down) && !iblockdata.c(west) && !iblockdata.c(south) && !iblockdata.c(east) && !iblockdata.c(up))
						map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
					else
						map.put(cloned, BlockActionContext.updateState(iblockdata.a(north, false)));
					break;
				case WEST:
					if (!iblockdata.c(down) && !iblockdata.c(north) && !iblockdata.c(south) && !iblockdata.c(west) && !iblockdata.c(up))
						map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
					else
						map.put(cloned, BlockActionContext.updateState(iblockdata.a(east, false)));
					break;
				default:
					break;
				}
			else if (material == Material.NETHER_PORTAL && (iblockdata.c(axis) == EnumAxis.a && (face == BlockFace.EAST || face == BlockFace.WEST)
					|| iblockdata.c(axis) == EnumAxis.c && (face == BlockFace.NORTH || face == BlockFace.SOUTH)))
				removeAllSurroundingPortals(map, cloned);
			else if (iblockdata.b() instanceof BlockTall) {
				int stateId = 0;
				for (IBlockState<Boolean> state : BLOCK_ROTATIONS) {
					BlockFace bface = stateId == 0 ? BlockFace.EAST : stateId == 1 ? BlockFace.NORTH : stateId == 2 ? BlockFace.SOUTH : BlockFace.WEST;
					bface = bface.getOppositeFace();
					++stateId;
					if (cloned.getBlockX() - bface.getModX() == destroyed.getBlockX() && cloned.getBlockZ() - bface.getModZ() == destroyed.getBlockZ()) {
						map.put(cloned, BlockActionContext.updateState(iblockdata.a(state, false)));
						break;
					}
				}
			} else if (iblockdata.b() instanceof BlockCobbleWall)
				fixWalls(map, cloned, iblockdata, face);
			else if (Loader.TICK_LEAVES && iblockdata.b() instanceof BlockLeaves leaves) {
				BlockPosition blockPos = (BlockPosition) cloned.getBlockPosition();
				leaves.a(iblockdata, EnumDirection.a((BlockPosition) cloned.getBlockPosition()), Blocks.a.m(), ((CraftWorld) destroyed.getWorld()).getHandle(), blockPos, blockPos);
			} else if (Loader.LADDER_WORKS_AS_VINE && material == Material.LADDER)
				destroyVineLadder(map, player, cloned, iblockdata, false);
			else if (isVine(material))
				destroyVine(map, player, cloned, iblockdata, false);
			else if (!Loader.LADDER_WORKS_AS_VINE && material == Material.LADDER || isWallBlock(material) && BlockFace.valueOf(iblockdata.c(direction).name()) == face
					|| iblockdata.b(attach) && iblockdata.c(attach) == BlockPropertyAttachPosition.b && BlockFace.valueOf(iblockdata.c(direction).name()) == face) {
				map.put(cloned, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, cloned, iblockdata, player)));
				if (isWallBlock(material))
					destroyConnectableBlocks(map, player, cloned, iblockdata);
			}
			if (isConnectable(material)) {
				BlockActionContext existing = map.get(cloned);
				if (existing != null)
					existing.doUpdatePhysics();
				else
					map.put(cloned, BlockActionContext.updatePhysics());
				if (material == Material.REDSTONE_WIRE)
					updateRedstoneWireAt(map, cloned, iblockdata);
				else
					for (BlockFace faces : AXIS_FACES) {
						Position cloned2 = cloned.clone().add(faces);
						iblockdata = getIBlockDataOrEmpty(map, cloned2);
						material = iblockdata.getBukkitMaterial();
						if (material == Material.REDSTONE_WIRE)
							updateRedstoneWireAt(map, cloned2, iblockdata);
					}
			}
		}
	}

	private boolean isAmethyst(Material material) {
		switch (material) {
		case AMETHYST_CLUSTER:
		case LARGE_AMETHYST_BUD:
		case MEDIUM_AMETHYST_BUD:
		case SMALL_AMETHYST_BUD:
			return true;
		default:
			break;
		}
		return false;
	}

	private boolean shouldSkip(Material material) {
		switch (material) {
		case AIR:
		case CAVE_AIR:
		case LAVA:
		case WATER:
			return true;
		default:
			break;
		}
		return false;
	}

	private void fixWallsMakeSmall(Map<Position, BlockActionContext> map, Position cloned, IBlockData iblockdata, BlockFace modFace) {
		if (!(iblockdata.b() instanceof BlockCobbleWall))
			return;

		BlockPropertyWallHeight east = iblockdata.c(WALL_BLOCK_ROTATIONS[0]);
		BlockPropertyWallHeight north = iblockdata.c(WALL_BLOCK_ROTATIONS[1]);
		BlockPropertyWallHeight south = iblockdata.c(WALL_BLOCK_ROTATIONS[2]);
		BlockPropertyWallHeight west = iblockdata.c(WALL_BLOCK_ROTATIONS[3]);

		switch (modFace.getOppositeFace()) {
		case EAST:
			if (west == BlockPropertyWallHeight.c)
				map.put(cloned.clone(), BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[0], BlockPropertyWallHeight.b).a(up, true)));
			else
				map.put(cloned.clone(), BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[0], BlockPropertyWallHeight.b).a(up,
						isDisabled(west, north, south) ? true : isUpper(getIBlockDataOrEmpty(map, cloned.clone().add(BlockFace.UP))))));
			break;
		case NORTH:
			if (south == BlockPropertyWallHeight.c)
				map.put(cloned.clone(), BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[1], BlockPropertyWallHeight.b).a(up, true)));
			else
				map.put(cloned.clone(), BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[1], BlockPropertyWallHeight.b).a(up,
						isDisabled(east, west, south) ? true : isUpper(getIBlockDataOrEmpty(map, cloned.clone().add(BlockFace.UP))))));
			break;
		case SOUTH:
			if (north == BlockPropertyWallHeight.c)
				map.put(cloned.clone(), BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[2], BlockPropertyWallHeight.b).a(up, true)));
			else
				map.put(cloned.clone(), BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[2], BlockPropertyWallHeight.b).a(up,
						isDisabled(east, north, west) ? true : isUpper(getIBlockDataOrEmpty(map, cloned.clone().add(BlockFace.UP))))));
			break;
		case WEST:
			if (east == BlockPropertyWallHeight.c)
				map.put(cloned.clone(), BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[3], BlockPropertyWallHeight.b).a(up, true)));
			else
				map.put(cloned.clone(), BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[3], BlockPropertyWallHeight.b).a(up,
						isDisabled(east, north, south) ? true : isUpper(getIBlockDataOrEmpty(map, cloned.clone().add(BlockFace.UP))))));
			break;
		default:
			break;
		}
	}

	private void fixWalls(Map<Position, BlockActionContext> map, Position cloned, IBlockData iblockdata, BlockFace modFace) {
		if (!(iblockdata.b() instanceof BlockCobbleWall))
			return;

		BlockPropertyWallHeight east = iblockdata.c(WALL_BLOCK_ROTATIONS[0]);
		BlockPropertyWallHeight north = iblockdata.c(WALL_BLOCK_ROTATIONS[1]);
		BlockPropertyWallHeight south = iblockdata.c(WALL_BLOCK_ROTATIONS[2]);
		BlockPropertyWallHeight west = iblockdata.c(WALL_BLOCK_ROTATIONS[3]);

		switch (modFace.getOppositeFace()) {
		case EAST:
			map.put(cloned.clone(),
					BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[0], BlockPropertyWallHeight.a).a(up, isUpper(getIBlockDataOrEmpty(map, cloned.clone().add(BlockFace.UP))) ? true
							: connectedToTwoSides(BlockPropertyWallHeight.a, west, north, south) ? false : isDisabled(east, north, south) ? true : true)));
			break;
		case NORTH:
			map.put(cloned.clone(),
					BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[1], BlockPropertyWallHeight.a).a(up, isUpper(getIBlockDataOrEmpty(map, cloned.clone().add(BlockFace.UP))) ? true
							: connectedToTwoSides(east, west, BlockPropertyWallHeight.a, south) ? false : isDisabled(east, north, south) ? true : true)));
			break;
		case SOUTH:
			map.put(cloned.clone(),
					BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[2], BlockPropertyWallHeight.a).a(up, isUpper(getIBlockDataOrEmpty(map, cloned.clone().add(BlockFace.UP))) ? true
							: connectedToTwoSides(east, west, north, BlockPropertyWallHeight.a) ? false : isDisabled(east, north, south) ? true : true)));
			break;
		case WEST:
			map.put(cloned.clone(),
					BlockActionContext.updateState(iblockdata.a(WALL_BLOCK_ROTATIONS[3], BlockPropertyWallHeight.a).a(up, isUpper(getIBlockDataOrEmpty(map, cloned.clone().add(BlockFace.UP))) ? true
							: connectedToTwoSides(east, BlockPropertyWallHeight.a, north, south) ? false : isDisabled(east, north, south) ? true : true)));
			break;
		case UP:
			boolean above = getIBlockDataOrEmpty(map, cloned.clone().add(BlockFace.UP)).b() instanceof BlockCobbleWall;
			if (!above) {
				if (east == BlockPropertyWallHeight.c)
					iblockdata = iblockdata.a(WALL_BLOCK_ROTATIONS[0], BlockPropertyWallHeight.b);
				if (north == BlockPropertyWallHeight.c)
					iblockdata = iblockdata.a(WALL_BLOCK_ROTATIONS[1], BlockPropertyWallHeight.b);
				if (south == BlockPropertyWallHeight.c)
					iblockdata = iblockdata.a(WALL_BLOCK_ROTATIONS[2], BlockPropertyWallHeight.b);
				if (west == BlockPropertyWallHeight.c)
					iblockdata = iblockdata.a(WALL_BLOCK_ROTATIONS[3], BlockPropertyWallHeight.b);
			}
			map.put(cloned.clone(), BlockActionContext.updateState(
					iblockdata.a(up, connectedToTwoSides(east, west, north, south) ? false : conntectedToAllSides(east, north, south, west) ? false : isDisabled(east, north, south) ? true : !above)));
			if (east == BlockPropertyWallHeight.c) {
				Position pos = cloned.clone().add(BlockFace.EAST);
				fixWallsMakeSmall(map, pos, (IBlockData) pos.getIBlockData(), BlockFace.EAST);
			}
			if (north == BlockPropertyWallHeight.c) {
				Position pos = cloned.clone().add(BlockFace.NORTH);
				fixWallsMakeSmall(map, pos, (IBlockData) pos.getIBlockData(), BlockFace.NORTH);
			}
			if (south == BlockPropertyWallHeight.c) {
				Position pos = cloned.clone().add(BlockFace.SOUTH);
				fixWallsMakeSmall(map, pos, (IBlockData) pos.getIBlockData(), BlockFace.SOUTH);
			}
			if (west == BlockPropertyWallHeight.c) {
				Position pos = cloned.clone().add(BlockFace.WEST);
				fixWallsMakeSmall(map, pos, (IBlockData) pos.getIBlockData(), BlockFace.WEST);
			}
			break;
		default:
			break;
		}
	}

	private boolean connectedToTwoSides(BlockPropertyWallHeight east2, BlockPropertyWallHeight west2, BlockPropertyWallHeight north2, BlockPropertyWallHeight south2) {
		return east2 != BlockPropertyWallHeight.a && west2 != BlockPropertyWallHeight.a && north2 == BlockPropertyWallHeight.a && south2 == BlockPropertyWallHeight.a
				|| north2 != BlockPropertyWallHeight.a && south2 != BlockPropertyWallHeight.a && east2 == BlockPropertyWallHeight.a && west2 == BlockPropertyWallHeight.a;
	}

	private boolean isUpper(IBlockData iblockdata) {
		if (iblockdata.b() instanceof BlockCobbleWall)
			return iblockdata.c(up);
		return false;
	}

	private boolean isDisabled(BlockPropertyWallHeight a, BlockPropertyWallHeight b, BlockPropertyWallHeight c) {
		return a == BlockPropertyWallHeight.a && b == BlockPropertyWallHeight.a && c == BlockPropertyWallHeight.a;
	}

	private boolean conntectedToAllSides(BlockPropertyWallHeight a, BlockPropertyWallHeight b, BlockPropertyWallHeight c, BlockPropertyWallHeight d) {
		return a != BlockPropertyWallHeight.a && b != BlockPropertyWallHeight.a && c != BlockPropertyWallHeight.a && d != BlockPropertyWallHeight.a;
	}

	private void fixRails(Map<Position, BlockActionContext> map, Position cloned, IBlockData iblockdata) {
		switch (iblockdata.c(TRACK_SHAPE)) {
		case g: // south_east
			map.put(cloned, BlockActionContext
					.updateState(iblockdata.a(TRACK_SHAPE, getTypeOrEmpty(map, cloned.clone().add(BlockFace.EAST)) == Material.AIR ? BlockPropertyTrackPosition.a : BlockPropertyTrackPosition.b)));
			break;
		case i: // north_west
			map.put(cloned, BlockActionContext
					.updateState(iblockdata.a(TRACK_SHAPE, getTypeOrEmpty(map, cloned.clone().add(BlockFace.WEST)) == Material.AIR ? BlockPropertyTrackPosition.a : BlockPropertyTrackPosition.b)));
			break;
		case j: // north_east
			map.put(cloned, BlockActionContext
					.updateState(iblockdata.a(TRACK_SHAPE, getTypeOrEmpty(map, cloned.clone().add(BlockFace.EAST)) == Material.AIR ? BlockPropertyTrackPosition.a : BlockPropertyTrackPosition.b)));
			break;
		case h: // south_west
			map.put(cloned, BlockActionContext
					.updateState(iblockdata.a(TRACK_SHAPE, getTypeOrEmpty(map, cloned.clone().add(BlockFace.WEST)) == Material.AIR ? BlockPropertyTrackPosition.a : BlockPropertyTrackPosition.b)));
			break;
		default:
			break;
		}
	}

	// Current "cloned" is removed.
	private void updateRedstoneWire(Map<Position, BlockActionContext> map, Position destroyed, IBlockData iblockdata) {
		// Let's move!
		BlockPropertyRedstoneSide side = iblockdata.c(east_redstone);
		if (side != BlockPropertyRedstoneSide.c) {
			Position cloned = destroyed.clone().add(BlockFace.EAST);
			if (side == BlockPropertyRedstoneSide.a)
				cloned.add(BlockFace.UP);
			if (side != BlockPropertyRedstoneSide.a && getIBlockDataOrEmpty(map, cloned).getBukkitMaterial() != Material.REDSTONE_WIRE)
				cloned.add(BlockFace.DOWN);
			updateRedstoneWireAt(map, cloned, getIBlockDataOrEmpty(map, cloned));
		}
		side = iblockdata.c(north_redstone);
		if (side != BlockPropertyRedstoneSide.c) {
			Position cloned = destroyed.clone().add(BlockFace.NORTH);
			if (side == BlockPropertyRedstoneSide.a)
				cloned.add(BlockFace.UP);
			if (side != BlockPropertyRedstoneSide.a && getIBlockDataOrEmpty(map, cloned).getBukkitMaterial() != Material.REDSTONE_WIRE)
				cloned.add(BlockFace.DOWN);
			updateRedstoneWireAt(map, cloned, getIBlockDataOrEmpty(map, cloned));
		}
		side = iblockdata.c(south_redstone);
		if (side != BlockPropertyRedstoneSide.c) {
			Position cloned = destroyed.clone().add(BlockFace.SOUTH);
			if (side == BlockPropertyRedstoneSide.a)
				cloned.add(BlockFace.UP);
			if (side != BlockPropertyRedstoneSide.a && getIBlockDataOrEmpty(map, cloned).getBukkitMaterial() != Material.REDSTONE_WIRE)
				cloned.add(BlockFace.DOWN);
			updateRedstoneWireAt(map, cloned, getIBlockDataOrEmpty(map, cloned));
		}
		side = iblockdata.c(west_redstone);
		if (side != BlockPropertyRedstoneSide.c) {
			Position cloned = destroyed.clone().add(BlockFace.WEST);
			if (side == BlockPropertyRedstoneSide.a)
				cloned.add(BlockFace.UP);
			if (side != BlockPropertyRedstoneSide.a && getIBlockDataOrEmpty(map, cloned).getBukkitMaterial() != Material.REDSTONE_WIRE)
				cloned.add(BlockFace.DOWN);
			updateRedstoneWireAt(map, cloned, getIBlockDataOrEmpty(map, cloned));
		}
	}

	private void updateRedstoneWireAt(Map<Position, BlockActionContext> map, Position destroyed, IBlockData iblockdata) {
		if (iblockdata.getBukkitMaterial() != Material.REDSTONE_WIRE) {
			destroyed.add(BlockFace.DOWN);
			return;
		}

		int connectedAnywhere = 0;
		BlockPropertyRedstoneSide eastConnected = BlockPropertyRedstoneSide.c;
		boolean east = false;
		BlockPropertyRedstoneSide northConnected = BlockPropertyRedstoneSide.c;
		boolean north = false;
		BlockPropertyRedstoneSide southConnected = BlockPropertyRedstoneSide.c;
		boolean south = false;
		BlockPropertyRedstoneSide westConnected = BlockPropertyRedstoneSide.c;
		boolean west = false;

		BlockPropertyRedstoneSide side = iblockdata.c(east_redstone);
		if (side != BlockPropertyRedstoneSide.c) {
			Position cloned = destroyed.clone().add(BlockFace.EAST);
			if (side == BlockPropertyRedstoneSide.a)
				cloned.add(BlockFace.UP);
			if (side != BlockPropertyRedstoneSide.a && !isConnectable(getIBlockDataOrEmpty(map, cloned).getBukkitMaterial()))
				cloned.add(BlockFace.DOWN);

			east = true;
			IBlockData ib = getIBlockDataOrEmpty(map, cloned);
			if (isConnectable(ib.getBukkitMaterial())) {
				eastConnected = side;
				++connectedAnywhere;
			}
		}
		side = iblockdata.c(north_redstone);
		if (side != BlockPropertyRedstoneSide.c) {
			Position cloned = destroyed.clone().add(BlockFace.NORTH);
			if (side == BlockPropertyRedstoneSide.a)
				cloned.add(BlockFace.UP);
			if (side != BlockPropertyRedstoneSide.a && !isConnectable(getIBlockDataOrEmpty(map, cloned).getBukkitMaterial()))
				cloned.add(BlockFace.DOWN);

			north = true;
			IBlockData ib = getIBlockDataOrEmpty(map, cloned);
			if (isConnectable(ib.getBukkitMaterial())) {
				northConnected = side;
				++connectedAnywhere;
			}
		}
		side = iblockdata.c(south_redstone);
		if (side != BlockPropertyRedstoneSide.c) {
			Position cloned = destroyed.clone().add(BlockFace.SOUTH);
			if (side == BlockPropertyRedstoneSide.a)
				cloned.add(BlockFace.UP);
			if (side != BlockPropertyRedstoneSide.a && !isConnectable(getIBlockDataOrEmpty(map, cloned).getBukkitMaterial()))
				cloned.add(BlockFace.DOWN);

			south = true;
			IBlockData ib = getIBlockDataOrEmpty(map, cloned);
			if (isConnectable(ib.getBukkitMaterial())) {
				southConnected = side;
				++connectedAnywhere;
			}
		}
		side = iblockdata.c(west_redstone);
		if (side != BlockPropertyRedstoneSide.c) {
			Position cloned = destroyed.clone().add(BlockFace.WEST);
			if (side == BlockPropertyRedstoneSide.a)
				cloned.add(BlockFace.UP);
			if (side != BlockPropertyRedstoneSide.a && !isConnectable(getIBlockDataOrEmpty(map, cloned).getBukkitMaterial()))
				cloned.add(BlockFace.DOWN);

			west = true;
			IBlockData ib = getIBlockDataOrEmpty(map, cloned);
			if (isConnectable(ib.getBukkitMaterial())) {
				westConnected = side;
				++connectedAnywhere;
			}
		}

		if (connectedAnywhere == 0)
			map.put(destroyed.clone(), BlockActionContext.updateState(iblockdata.a(east_redstone, BlockPropertyRedstoneSide.b).a(north_redstone, BlockPropertyRedstoneSide.b)
					.a(south_redstone, BlockPropertyRedstoneSide.b).a(west_redstone, BlockPropertyRedstoneSide.b)));
		else {
			if (connectedAnywhere == 1) {
				if (west && westConnected != BlockPropertyRedstoneSide.c || east && eastConnected != BlockPropertyRedstoneSide.c)
					iblockdata = iblockdata.a(west_redstone, westConnected == BlockPropertyRedstoneSide.c ? BlockPropertyRedstoneSide.b : westConnected)
							.a(east_redstone, eastConnected == BlockPropertyRedstoneSide.c ? BlockPropertyRedstoneSide.b : eastConnected).a(north_redstone, BlockPropertyRedstoneSide.c)
							.a(south_redstone, BlockPropertyRedstoneSide.c);
				else if (south && southConnected != BlockPropertyRedstoneSide.c || north && northConnected != BlockPropertyRedstoneSide.c)
					iblockdata = iblockdata.a(south_redstone, southConnected == BlockPropertyRedstoneSide.c ? BlockPropertyRedstoneSide.b : southConnected)
							.a(north_redstone, northConnected == BlockPropertyRedstoneSide.c ? BlockPropertyRedstoneSide.b : northConnected).a(west_redstone, BlockPropertyRedstoneSide.c)
							.a(east_redstone, BlockPropertyRedstoneSide.c);
			} else {
				if (west && westConnected == BlockPropertyRedstoneSide.c)
					iblockdata = iblockdata.a(west_redstone, BlockPropertyRedstoneSide.c);

				if (south && southConnected == BlockPropertyRedstoneSide.c)
					iblockdata = iblockdata.a(south_redstone, BlockPropertyRedstoneSide.c);

				if (north && northConnected == BlockPropertyRedstoneSide.c)
					iblockdata = iblockdata.a(north_redstone, BlockPropertyRedstoneSide.c);

				if (east && eastConnected == BlockPropertyRedstoneSide.c)
					iblockdata = iblockdata.a(east_redstone, BlockPropertyRedstoneSide.c);
			}
			map.put(destroyed.clone(), BlockActionContext.updateState(iblockdata));
		}
	}

	private boolean isConnectable(Material material) {
		switch (material) {
		case REDSTONE_WIRE:
		case REPEATER:
		case COMPARATOR:
		case DAYLIGHT_DETECTOR:
		case LEVER:
		case LIGHTNING_ROD:
		case ACACIA_BUTTON:
		case BIRCH_BUTTON:
		case CRIMSON_BUTTON:
		case DARK_OAK_BUTTON:
		case JUNGLE_BUTTON:
		case MANGROVE_BUTTON:
		case OAK_BUTTON:
		case SPRUCE_BUTTON:
		case POLISHED_BLACKSTONE_BUTTON:
		case STONE_BUTTON:
		case WARPED_BUTTON:
		case TRIPWIRE_HOOK:
		case OBSERVER:
		case DETECTOR_RAIL:
		case SCULK_SENSOR:
		case TARGET:
		case REDSTONE_TORCH:
		case REDSTONE_WALL_TORCH:
		case REDSTONE_BLOCK:
		case REDSTONE_LAMP:
			return true;
		default:
			break;
		}
		return isPressurePlate(material);
	}

	private boolean isPressurePlate(Material material) {
		switch (material) {
		case ACACIA_PRESSURE_PLATE:
		case BIRCH_PRESSURE_PLATE:
		case CRIMSON_PRESSURE_PLATE:
		case DARK_OAK_PRESSURE_PLATE:
		case HEAVY_WEIGHTED_PRESSURE_PLATE:
		case JUNGLE_PRESSURE_PLATE:
		case LIGHT_WEIGHTED_PRESSURE_PLATE:
		case MANGROVE_PRESSURE_PLATE:
		case OAK_PRESSURE_PLATE:
		case POLISHED_BLACKSTONE_PRESSURE_PLATE:
		case SPRUCE_PRESSURE_PLATE:
		case STONE_PRESSURE_PLATE:
		case WARPED_PRESSURE_PLATE:
			return true;
		default:
			break;
		}
		return false;
	}

	private void destroyPiston(Map<Position, BlockActionContext> map, Player player, Position cloned, IBlockData iblockdata) {
		if (iblockdata.getBukkitMaterial() == Material.PISTON_HEAD) {
			BlockFace face = BlockFace.valueOf(iblockdata.c(DIRECTION).name()).getOppositeFace();
			cloned = cloned.clone().add(face);
			map.put(cloned, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, cloned, (IBlockData) cloned.getIBlockData(), player)));
		} else if (iblockdata.c(PISTON_ACTIVATED)) {
			BlockFace face = BlockFace.valueOf(iblockdata.c(DIRECTION).name());
			cloned = cloned.clone().add(face);
			map.put(cloned, BlockActionContext.destroy(iblockdata, Material.AIR, Collections.emptyList()));
		}
	}

	private void fixDripstoneThickness(Map<Position, BlockActionContext> map, Player player, Position destroyed, boolean up) {
		IBlockData iblockdata = (IBlockData) destroyed.getIBlockData();
		Material material = iblockdata.getBukkitMaterial();
		if (material == Material.POINTED_DRIPSTONE && (up ? iblockdata.c(vertical_direction) == EnumDirection.b : iblockdata.c(vertical_direction) == EnumDirection.a)) {
			map.put(destroyed, BlockActionContext.updateState(iblockdata.a(thickness, DripstoneThickness.b)));

			destroyed = destroyed.clone().add(0, !up ? 1 : -1, 0);
			iblockdata = (IBlockData) destroyed.getIBlockData();
			material = iblockdata.getBukkitMaterial();
			int pos = 1;
			while (material == Material.POINTED_DRIPSTONE && (up ? iblockdata.c(vertical_direction) == EnumDirection.b : iblockdata.c(vertical_direction) == EnumDirection.a)) {
				map.put(destroyed, BlockActionContext.updateState(iblockdata.a(thickness, THICKNESS[pos++])));
				if (pos >= THICKNESS.length)
					break;
				destroyed = destroyed.clone().add(0, !up ? 1 : -1, 0);
				iblockdata = (IBlockData) destroyed.getIBlockData();
				material = iblockdata.getBukkitMaterial();
			}
		}
	}

	private boolean isHead(Material material) {
		switch (material) {
		case CREEPER_HEAD:
		case CREEPER_WALL_HEAD:
		case DRAGON_HEAD:
		case DRAGON_WALL_HEAD:
		case PLAYER_HEAD:
		case PLAYER_WALL_HEAD:
		case ZOMBIE_HEAD:
		case ZOMBIE_WALL_HEAD:
		case SKELETON_SKULL:
		case SKELETON_WALL_SKULL:
		case WITHER_SKELETON_SKULL:
		case WITHER_SKELETON_WALL_SKULL:
			return true;
		default:
			break;
		}
		return false;
	}

	public List<org.bukkit.inventory.ItemStack> getDrops(ItemStack nms, Position pos, IBlockData iblockdata, org.bukkit.entity.Entity entity) {
		if (nms == null || isPreferredTool(iblockdata, nms)) {
			WorldServer world = ((CraftWorld) pos.getWorld()).getHandle();
			BlockPosition position = (BlockPosition) pos.getBlockPosition();

			List<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
			Builder loottableinfo_builder = new Builder(world).a(SINGLE_THREAD_RANDOM_SOURCE).a(LootContextParameters.f, Vec3D.a(position)).a(LootContextParameters.i, nms == null ? ItemStack.b : nms)
					.b(LootContextParameters.a, entity == null ? null : ((CraftEntity) entity).getHandle()).b(LootContextParameters.h, ((Chunk) pos.getNMSChunk()).getTileEntityImmediately(position));
			for (ItemStack drop : iblockdata.a(loottableinfo_builder))
				items.add(CraftItemStack.asBukkitCopy(drop));
			return items;
		}
		return Collections.emptyList();
	}

	private boolean isPreferredTool(IBlockData iblockdata, ItemStack nmsItem) {
		return !iblockdata.t() || nmsItem.b(iblockdata);
	}

	private boolean isGrowingUp(Material material) {
		switch (material) {
		case TWISTING_VINES:
		case TWISTING_VINES_PLANT:
		case SUGAR_CANE:
		case BAMBOO:
		case KELP:
		case KELP_PLANT:
		case SCAFFOLDING:
			return true;
		default:
			break;
		}
		return false;
	}

	private void destroyGrowingUp(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata) {
		map.put(destroyed, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata), getDrops(null, destroyed, iblockdata, player)));

		destroyed = destroyed.clone();
		destroyed.setY(destroyed.getY() + 1);

		iblockdata = (IBlockData) destroyed.getIBlockData();

		if (isGrowingUp(iblockdata.getBukkitMaterial()))
			destroyGrowingUp(map, player, destroyed, iblockdata);
	}

	private void destroyWeepingVines(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata) {
		map.put(destroyed, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, destroyed, iblockdata, player)));

		destroyed = destroyed.clone();
		destroyed.setY(destroyed.getY() - 1);

		iblockdata = (IBlockData) destroyed.getIBlockData();

		if (isWeepingVines(iblockdata.getBukkitMaterial()))
			destroyWeepingVines(map, player, destroyed, iblockdata);
	}

	private boolean isWeepingVines(Material material) {
		switch (material) {
		case WEEPING_VINES:
		case WEEPING_VINES_PLANT:
			return true;
		default:
			break;
		}
		return false;
	}

	private boolean isVine(Material material) {
		switch (material) {
		case VINE:
		case CAVE_VINES:
		case GLOW_LICHEN:
			return true;
		default:
			break;
		}
		return false;
	}

	private void destroyVine(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata, boolean first) {
		Object[] result = blockBehindOrAboveVine(map, destroyed, iblockdata);
		if ((boolean) result[0] && result[1] != null) {
			if (!result[1].equals(iblockdata)) {
				map.put(destroyed, BlockActionContext.updateState(result[1]));

				destroyed = destroyed.clone();
				destroyed = destroyed.setY(destroyed.getY() - 1);
				iblockdata = (IBlockData) destroyed.getIBlockData();
				Material type = iblockdata.getBukkitMaterial();
				if (isVine(type))
					destroyVine(map, player, destroyed, iblockdata, false);
			}
			return;
		}

		map.put(destroyed, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, destroyed, iblockdata, player)));

		destroyed = destroyed.clone();
		destroyed = destroyed.setY(destroyed.getY() - 1);
		iblockdata = (IBlockData) destroyed.getIBlockData();
		Material type = iblockdata.getBukkitMaterial();
		if (isVine(type))
			destroyVine(map, player, destroyed, iblockdata, false);
	}

	private Object[] blockBehindOrAboveVine(Map<Position, BlockActionContext> map, Position pos, IBlockData blockData) {
		Position cloned = pos.clone();
		IBlockData above = getIBlockDataOrEmpty(map, pos.clone().add(BlockFace.UP));

		boolean result = false;
		boolean modification = false;

		if (blockData.c(up))
			if (above.getBukkitMaterial().isSolid()) {
				if (!result)
					result = true;
			} else {
				blockData = blockData.a(up, false);
				modification = true;
			}

		if (blockData.c(east)) {
			if (getTypeOrEmpty(map, cloned.add(BlockFace.EAST)).isSolid() || isConnected(east, above)) {
				if (!result)
					result = true;
			} else {
				blockData = blockData.a(east, false);
				modification = true;
			}
			cloned.remove(BlockFace.EAST);
		}
		if (blockData.c(north)) {
			if (getTypeOrEmpty(map, cloned.add(BlockFace.NORTH)).isSolid() || isConnected(north, above)) {
				if (!result)
					result = true;
			} else {
				blockData = blockData.a(north, false);
				modification = true;
			}
			cloned.remove(BlockFace.NORTH);
		}
		if (blockData.c(south)) {
			if (getTypeOrEmpty(map, cloned.add(BlockFace.SOUTH)).isSolid() || isConnected(south, above)) {
				if (!result)
					result = true;
			} else {
				blockData = blockData.a(south, false);
				modification = true;
			}
			cloned.remove(BlockFace.SOUTH);
		}
		if (blockData.c(west)) {
			if (getTypeOrEmpty(map, cloned.add(BlockFace.WEST)).isSolid() || isConnected(west, above)) {
				if (!result)
					result = true;
			} else {
				blockData = blockData.a(west, false);
				modification = true;
			}
			cloned.remove(BlockFace.WEST);
		}
		return new Object[] { !modification ? true : result, isInvalid(blockData) ? null : blockData };
	}

	private boolean isInvalid(IBlockData blockData) {
		return !blockData.c(west) && !blockData.c(south) && !blockData.c(north) && !blockData.c(east) && !blockData.c(up);
	}

	private boolean isConnected(IBlockState<Boolean> side, IBlockData second) {
		return second.b(side) && second.c(side);
	}

	private Material getTypeOrEmpty(Map<Position, BlockActionContext> map, Position add) {
		BlockActionContext context = map.get(add);
		return context != null ? context.isDestroy() ? Material.AIR : context.getIBlockData() != null ? ((IBlockData) context.getIBlockData()).getBukkitMaterial() : context.getType()
				: add.getBukkitType();
	}

	private IBlockData getIBlockDataOrEmpty(Map<Position, BlockActionContext> map, Position add) {
		BlockActionContext context = map.get(add);
		return context != null ? context.isDestroy() ? Blocks.a.m() : context.getIBlockData() != null ? (IBlockData) context.getIBlockData() : (IBlockData) context.getData().getIBlockData()
				: (IBlockData) add.getIBlockData();
	}

	private void destroyVineLadder(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata, boolean first) {
		if (blockBehindOrAboveLadder(map, destroyed, iblockdata, !first))
			return;

		map.put(destroyed, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, destroyed, iblockdata, player)));

		destroyed = destroyed.clone();
		destroyed = destroyed.setY(destroyed.getY() - 1);
		iblockdata = (IBlockData) destroyed.getIBlockData();
		Material type = iblockdata.getBukkitMaterial();
		if (type == Material.LADDER)
			destroyVineLadder(map, player, destroyed, iblockdata, false);
	}

	private boolean blockBehindOrAboveLadder(Map<Position, BlockActionContext> map, Position pos, IBlockData blockData, boolean checkAbove) {
		Position cloned = pos.clone();
		if (checkAbove && getTypeOrEmpty(map, cloned.add(0, 1, 0)) == Material.LADDER)
			return true;
		if (checkAbove)
			cloned.add(0, -1, 0);

		BlockFace face = BlockFace.valueOf(blockData.c(direction).name()).getOppositeFace();
		return getTypeOrEmpty(map, cloned.add(face)).isSolid();
	}

	private void removeAllSurroundingPortals(Map<Position, BlockActionContext> map, Position destroyed) {
		for (BlockFace face : ALL_FACES) {
			Position clone = destroyed.clone().add(face);
			if (map.containsKey(clone))
				continue;
			IBlockData iblockdata = (IBlockData) clone.getIBlockData();
			Material type = iblockdata.getBukkitMaterial();
			if (type == Material.NETHER_PORTAL) {
				map.put(clone, BlockActionContext.destroy(iblockdata, Material.AIR, Collections.emptyList()));
				removeAllSurroundingPortals(map, clone);
			}
		}
	}

	private boolean isBed(Material material) {
		switch (material) {
		case BLACK_BED:
		case BLUE_BED:
		case BROWN_BED:
		case CYAN_BED:
		case GRAY_BED:
		case GREEN_BED:
		case LIGHT_BLUE_BED:
		case LIGHT_GRAY_BED:
		case LIME_BED:
		case MAGENTA_BED:
		case ORANGE_BED:
		case PINK_BED:
		case PURPLE_BED:
		case RED_BED:
		case WHITE_BED:
		case YELLOW_BED:
			return true;
		default:
			break;
		}
		return false;
	}

	private void destroyBed(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata) {
		Position cloned = destroyed.clone();
		BlockFace face = BlockFace.valueOf(iblockdata.c(direction).name());
		if (iblockdata.c(bedpart) == BlockPropertyBedPart.a)
			map.put(cloned.add((face = face.getOppositeFace()).getModX(), 0, face.getModZ()), BlockActionContext.destroy(iblockdata, Material.AIR, Collections.emptyList()));
		else
			map.put(cloned.add(face.getModX(), 0, face.getModZ()), BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, cloned, (IBlockData) cloned.getIBlockData(), player)));
	}

	private boolean isDoubleBlock(Material material) {
		switch (material) {
		case TALL_GRASS:
		case TALL_SEAGRASS:
		case LARGE_FERN:
		case PEONY:
		case ROSE_BUSH:
		case LILAC:
		case SUNFLOWER:
		case ACACIA_DOOR:
		case BIRCH_DOOR:
		case CRIMSON_DOOR:
		case DARK_OAK_DOOR:
		case IRON_DOOR:
		case JUNGLE_DOOR:
		case MANGROVE_DOOR:
		case OAK_DOOR:
		case SPRUCE_DOOR:
		case WARPED_DOOR:
			return true;
		default:
			break;
		}
		return false;
	}

	private void destroyDoubleBlock(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata) {
		if (iblockdata.c(doubleHalf) == BlockPropertyDoubleBlockHalf.b)
			destroyed = destroyed.clone().add(0, 1, 0);
		else
			destroyed = destroyed.clone().add(0, -1, 0);
		map.put(destroyed, BlockActionContext.destroy(iblockdata, isWaterlogged(iblockdata),
				iblockdata.c(doubleHalf) != BlockPropertyDoubleBlockHalf.b ? getDrops(null, destroyed, (IBlockData) destroyed.getIBlockData(), player) : Collections.emptyList()));
	}

	private boolean isChest(Material material) {
		switch (material) {
		case CHEST:
		case TRAPPED_CHEST:
			return true;
		default:
			break;
		}
		return false;
	}

	private void destroyContainerTile(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata) {
		// Items
		TileEntity container = ((Chunk) destroyed.getNMSChunk()).getTileEntityImmediately((BlockPosition) destroyed.getBlockPosition());
		if (container == null)
			return;
		if (container instanceof TileEntityLectern lectern) {
			if (lectern.c() != null) {
				map.get(destroyed).setTileLoot(new ArrayList<>());
				map.get(destroyed).getTileLoot().add(CraftItemStack.asBukkitCopy(lectern.c())); // add lectern book
			}
		} else if (container instanceof TileEntityShulkerBox shulker) {
			map.get(destroyed).getLoot().clear(); // remove empty shulker box
			ItemStack item = new ItemStack(IRegistry.Y.a(new MinecraftKey("minecraft:" + (shulker.j() == null ? "shulker_box" : shulker.j().name().toLowerCase() + "_shulker_box"))));
			shulker.e(item);
			map.get(destroyed).getLoot().add(CraftItemStack.asBukkitCopy(item)); // add filled shulker box
		} else if (container instanceof TileEntityContainer tileContainer) {
			map.get(destroyed).setTileLoot(new ArrayList<>());
			for (ItemStack item : tileContainer.getContents())
				map.get(destroyed).getTileLoot().add(CraftItemStack.asBukkitCopy(item));
		}
	}

	private void destroyChest(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata) {
		destroyContainerTile(map, player, destroyed, iblockdata);

		BlockPropertyChestType chesttype = iblockdata.c(chestType);
		if (chesttype == BlockPropertyChestType.c) {
			destroyed = destroyed.clone();
			if (iblockdata.b() instanceof BlockChest) {
				switch (BlockFace.valueOf(iblockdata.c(direction).name())) {
				case EAST:
					destroyed.add(0, 0, -1);
					break;
				case NORTH:
					destroyed.add(-1, 0, 0);
					break;
				case SOUTH:
					destroyed.add(1, 0, 0);
					break;
				case WEST:
					destroyed.add(0, 0, 1);
					break;
				default:
					break;
				}
				IBlockData modified = (IBlockData) destroyed.getIBlockData();
				if (modified.b() instanceof BlockChest)
					map.put(destroyed, BlockActionContext.updateState(modified.a(chestType, BlockPropertyChestType.a)));
			}
		} else if (chesttype == BlockPropertyChestType.b) {
			destroyed = destroyed.clone();
			if (iblockdata.b() instanceof BlockChest) {
				switch (BlockFace.valueOf(iblockdata.c(direction).name())) {
				case EAST:
					destroyed.add(0, 0, 1);
					break;
				case NORTH:
					destroyed.add(1, 0, 0);
					break;
				case SOUTH:
					destroyed.add(-1, 0, 0);
					break;
				case WEST:
					destroyed.add(0, 0, -1);
					break;
				default:
					break;
				}
				IBlockData modified = (IBlockData) destroyed.getIBlockData();
				if (modified.b() instanceof BlockChest)
					map.put(destroyed, BlockActionContext.updateState(modified.a(chestType, BlockPropertyChestType.a)));
			}
		}
	}

	private Material isWaterlogged(IBlockData data) {
		if (data.getBukkitMaterial() == Material.SEAGRASS || data.getBukkitMaterial() == Material.TALL_SEAGRASS || data.getBukkitMaterial() == Material.KELP
				|| data.getBukkitMaterial() == Material.KELP_PLANT)
			return Material.WATER;
		return !data.b(waterlogged) ? Material.AIR : data.c(waterlogged) ? Material.WATER : Material.AIR;
	}

	/**
	 * @apiNote Start destroying of chorus plant with flowers, see
	 *          {@link #destroyChorus(Position, LootTable, boolean, int)}
	 */
	private void destroyChorusInit(Map<Position, BlockActionContext> map, Player player, Position destroyed, IBlockData iblockdata) {
		boolean onEast = iblockdata.c(east);
		boolean onNorth = iblockdata.c(north);
		boolean onSouth = iblockdata.c(south);
		boolean onWest = iblockdata.c(west);
		boolean onTop = iblockdata.c(up);
		if (onTop) {
			destroyChorus(map, player, destroyed.clone().add(0, 1, 0), 1);
			return;
		}
		if (onEast)
			destroyChorus(map, player, destroyed.clone().add(BlockFace.EAST), 0);
		if (onNorth)
			destroyChorus(map, player, destroyed.clone().add(BlockFace.NORTH), 0);
		if (onSouth)
			destroyChorus(map, player, destroyed.clone().add(BlockFace.SOUTH), 0);
		if (onWest)
			destroyChorus(map, player, destroyed.clone().add(BlockFace.WEST), 0);
	}

	/**
	 * @apiNote Destroy chorus plant with flowers and continue to connected
	 */
	private void destroyChorus(Map<Position, BlockActionContext> map, Player player, Position destroyed, int direction) {
		IBlockData iblockdata = (IBlockData) destroyed.getIBlockData();
		if (iblockdata.getBukkitMaterial() == Material.CHORUS_FLOWER || iblockdata.getBukkitMaterial() == Material.POPPED_CHORUS_FRUIT) {
			map.put(destroyed, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, destroyed, iblockdata, player)));
			return;
		}
		if (!(iblockdata.b() instanceof BlockChorusFruit))
			return;

		map.put(destroyed, BlockActionContext.destroy(iblockdata, Material.AIR, getDrops(null, destroyed, iblockdata, player)));

		boolean onTop = iblockdata.c(up);
		if (onTop) {
			destroyChorus(map, player, destroyed.clone().add(0, 1, 0), 1);
			return;
		}

		if (direction == 0)
			return; // 2x to the side?

		boolean onEast = iblockdata.c(east);
		boolean onNorth = iblockdata.c(north);
		boolean onSouth = iblockdata.c(south);
		boolean onWest = iblockdata.c(west);
		if (onEast)
			destroyChorus(map, player, destroyed.clone().add(BlockFace.EAST), 0);
		if (onNorth)
			destroyChorus(map, player, destroyed.clone().add(BlockFace.NORTH), 0);
		if (onSouth)
			destroyChorus(map, player, destroyed.clone().add(BlockFace.SOUTH), 0);
		if (onWest)
			destroyChorus(map, player, destroyed.clone().add(BlockFace.WEST), 0);
	}

	/**
	 * @apiNote Fix nearby chorus fruit plant
	 */
	private void updateChorusPlant(Map<Position, BlockActionContext> map, Position clone) {
		// top?
		clone.add(0, -1, 0);
		IBlockData iblockdata = (IBlockData) clone.getIBlockData();
		if (iblockdata.b() instanceof BlockChorusFruit) {
			boolean on = iblockdata.c(up);
			if (on) {
				map.put(clone.clone(), BlockActionContext.updateState(iblockdata.a(up, false)));
				return;
			}
		}
		// east?
		clone.add(BlockFace.NORTH.getModX(), 0, BlockFace.NORTH.getModZ());
		iblockdata = (IBlockData) clone.getIBlockData();
		if (iblockdata.b() instanceof BlockChorusFruit) {
			boolean on = iblockdata.c(east);
			if (on) {
				map.put(clone.clone(), BlockActionContext.updateState(iblockdata.a(east, false)));
				return;
			}
		}
		// north?
		clone.add(BlockFace.EAST.getModX(), 0, BlockFace.EAST.getModZ());
		iblockdata = (IBlockData) clone.getIBlockData();
		if (iblockdata.b() instanceof BlockChorusFruit) {
			boolean on = iblockdata.c(north);
			if (on) {
				map.put(clone.clone(), BlockActionContext.updateState(iblockdata.a(north, false)));
				return;
			}
		}
		// west?
		clone.add(BlockFace.SOUTH.getModX(), 0, BlockFace.SOUTH.getModZ());
		iblockdata = (IBlockData) clone.getIBlockData();
		if (iblockdata.b() instanceof BlockChorusFruit) {
			boolean on = iblockdata.c(west);
			if (on) {
				map.put(clone.clone(), BlockActionContext.updateState(iblockdata.a(west, false)));
				return;
			}
		}
		// south?
		clone.add(BlockFace.WEST.getModX(), 0, BlockFace.WEST.getModZ());
		iblockdata = (IBlockData) clone.getIBlockData();
		if (iblockdata.b() instanceof BlockChorusFruit) {
			boolean on = iblockdata.c(south);
			if (on)
				map.put(clone.clone(), BlockActionContext.updateState(iblockdata.a(south, false)));
		}
	}

	public static class ThreadAccessServerRandomSource implements BitRandomSource {
		ThreadAccessRandomSource random;

		public ThreadAccessServerRandomSource(ThreadAccessRandomSource random) {
			this.random = random;
		}

		@Override
		public RandomSource d() {
			return new ThreadAccessServerRandomSource(random);
		}

		@Override
		public PositionalRandomFactory e() {
			return new PositionalRandomFactory() {
				protected long a = g();

				@Override
				public RandomSource a(final int x, final int y, final int z) {
					final long l = MathHelper.c(x, y, z);
					final long m = l ^ a;
					return new LegacyRandomSource(m);
				}

				@Override
				public RandomSource a(final String seed) {
					final int i = seed.hashCode();
					return new LegacyRandomSource(i ^ a);
				}

				@Override
				public void a(final StringBuilder info) {
					info.append("ThreadAccessServerPositionalRandomFactory{").append(a).append("}");
				}
			};
		}

		@Override
		public void b(long seed) {
			random.setSeed(seed);
		}

		@Override
		public int c(int bits) {
			return random.percentChance(bits);
		}

		@Override
		public double k() {
			return random.nextDouble();
		}

		@Override
		public float i() {
			return random.floatChance();
		}
	}
}
