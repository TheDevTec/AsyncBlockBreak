package me.devtec.asyncblockbreak.events;

import java.util.Collection;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_19_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Consumer;

import me.devtec.asyncblockbreak.api.LootTable;
import me.devtec.asyncblockbreak.utils.BlockActionContext;
import me.devtec.theapi.bukkit.game.BlockDataStorage;
import me.devtec.theapi.bukkit.game.Position;
import net.minecraft.core.BlockPosition;
import net.minecraft.world.level.block.state.IBlockData;

public class AsyncBlockBreakEvent extends BlockBreakEvent {
	private BlockDataStorage blockData;
	private boolean isInstant;
	private BlockFace face;
	private boolean tileDrops;
	private Consumer<Item> consumer;
	private LootTable loot;
	private Position pos;
	private Map<Position, BlockActionContext> modifiedBlocks;

	public AsyncBlockBreakEvent(Integer[] result, Position initBlock, Map<Position, BlockActionContext> modifiedBlocks, Player player, BlockDataStorage blockData, boolean instantlyBroken,
			BlockFace face) {
		super(new CraftBlock(((CraftWorld) initBlock.getWorld()).getHandle(), (BlockPosition) initBlock.getBlockPosition()) {

			BlockActionContext main = modifiedBlocks.get(initBlock);
			IBlockData data = (IBlockData) blockData.getIBlockData() == null ? CraftMagicNumbers.getBlock(blockData.getType()).m() : (IBlockData) blockData.getIBlockData();
			Material type = blockData.getType();

			@Override
			public IBlockData getNMS() {
				return result[0] == 1 ? getCraftWorld().getHandle().a_(getPosition()) : data;
			}

			@Override
			public byte getData() {
				return CraftMagicNumbers.toLegacyData(getNMS());
			}

			@Override
			public Material getType() {
				return result[0] == 1 ? getCraftWorld().getHandle().a_(getPosition()).getBukkitMaterial() : type;
			}

			@Override
			public void setType(Material type, boolean applyPhysics) {
				if (result[0] == 1) {
					this.setBlockData(type.createBlockData(), applyPhysics);
					return;
				}
				this.type = type;
				main.setType(type);
				data = (IBlockData) main.getIBlockData() == null ? CraftMagicNumbers.getBlock(type).m() : (IBlockData) main.getIBlockData();
				if (applyPhysics)
					main.doUpdatePhysics();
			}

			@Override
			public void setBlockData(BlockData data, boolean applyPhysics) {
				if (result[0] == 1) {
					CraftBlock.setTypeAndData(getCraftWorld().getHandle(), getPosition(), getNMS(), ((CraftBlockData) data).getState(), applyPhysics);
					return;
				}
				type = data.getMaterial();
				IBlockData nms = ((CraftBlockData) data).getState();
				main.setIBlockData(nms);
				this.data = nms;
				if (applyPhysics)
					main.doUpdatePhysics();
			}

			@Override
			public void setData(byte data, boolean applyPhysics) {
				if (result[0] == 1) {
					getCraftWorld().getHandle().a(getPosition(), CraftMagicNumbers.getBlock(getType(), data), applyPhysics ? 3 : 2);
					return;
				}
				IBlockData nms = CraftMagicNumbers.getBlock(getType(), data);
				main.setIBlockData(nms);
				this.data = nms;
			}

			@Override
			public Collection<ItemStack> getDrops(ItemStack item, Entity entity) {
				return result[0] == 1 ? super.getDrops(item, entity) : main.getLoot();
			}
		}, player);
		this.blockData = blockData;
		isInstant = instantlyBroken;
		pos = initBlock;
		this.modifiedBlocks = modifiedBlocks;
		this.face = face;
		tileDrops = true;
		loot = new LootTable();
	}

	public LootTable getLoot() {
		return loot;
	}

	public Position getPosition() {
		return pos;
	}

	public Map<Position, BlockActionContext> getModifiedBlocks() {
		return modifiedBlocks;
	}

	public BlockFace getBlockFace() {
		return face;
	}

	public boolean isInstant() {
		return isInstant;
	}

	public boolean doTileDrops() {
		return tileDrops;
	}

	public void setTileDrops(boolean status) {
		tileDrops = status;
	}

	public BlockDataStorage getBlockData() {
		return blockData;
	}

	// Applicable only if event is cancelled
	public void setBlockData(BlockDataStorage data) {
		blockData = data;
	}

	public Consumer<Item> getItemConsumer() {
		return consumer;
	}

	public void setItemConsumer(Consumer<Item> consumer) {
		this.consumer = consumer;
	}
}
