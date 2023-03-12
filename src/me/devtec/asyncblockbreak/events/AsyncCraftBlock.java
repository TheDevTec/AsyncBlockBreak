package me.devtec.asyncblockbreak.events;

import java.util.Collection;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_19_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import me.devtec.asyncblockbreak.utils.BlockActionContext;
import me.devtec.theapi.bukkit.game.BlockDataStorage;
import me.devtec.theapi.bukkit.game.Position;
import net.minecraft.core.BlockPosition;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.block.state.IBlockData;

public class AsyncCraftBlock extends CraftBlock {

	BlockActionContext main;
	BlockDataStorage blockData;
	AsyncBlockBreakEvent event;

	IBlockData data;
	Material type;

	private AsyncCraftBlock(GeneratorAccess world, BlockPosition position) {
		super(world, position);
	}

	public AsyncCraftBlock(Position initBlock, BlockActionContext main, BlockDataStorage blockData) {
		this(((CraftWorld) initBlock.getWorld()).getHandle(), (BlockPosition) initBlock.getBlockPosition());
		this.main = main;
		this.blockData = blockData;

		data = (IBlockData) blockData.getIBlockData() == null ? CraftMagicNumbers.getBlock(blockData.getType()).m() : (IBlockData) blockData.getIBlockData();
		type = blockData.getType();
	}

	protected void setEvent(AsyncBlockBreakEvent event) {
		this.event = event;
	}

	public AsyncBlockBreakEvent getEvent() {
		return event;
	}

	@Override
	public IBlockData getNMS() {
		return event.isCompleted() ? getCraftWorld().getHandle().a_(getPosition()) : data;
	}

	@Override
	public byte getData() {
		return CraftMagicNumbers.toLegacyData(getNMS());
	}

	@Override
	public Material getType() {
		return event.isCompleted() ? getCraftWorld().getHandle().a_(getPosition()).getBukkitMaterial() : type;
	}

	@Override
	public void setType(Material type, boolean applyPhysics) {
		if (event.isCompleted()) {
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
		if (event.isCompleted()) {
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
		if (event.isCompleted()) {
			getCraftWorld().getHandle().a(getPosition(), CraftMagicNumbers.getBlock(getType(), data), applyPhysics ? 3 : 2);
			return;
		}
		IBlockData nms = CraftMagicNumbers.getBlock(getType(), data);
		main.setIBlockData(nms);
		this.data = nms;
	}

	@Override
	public Collection<ItemStack> getDrops(ItemStack item, Entity entity) {
		return event.isCompleted() ? super.getDrops(item, entity) : main.getLoot();
	}
}