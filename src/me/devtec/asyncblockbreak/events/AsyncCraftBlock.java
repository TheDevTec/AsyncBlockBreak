package me.devtec.asyncblockbreak.events;

import java.util.Collection;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

public interface AsyncCraftBlock extends Block {

	public void setEvent(AsyncBlockBreakEvent asyncBlockBreakEvent);

	public AsyncBlockBreakEvent getEvent();

	public Object getIBlockData();

	@Override
	public byte getData();

	@Override
	public Material getType();

	@Override
	public void setType(Material type, boolean applyPhysics);

	@Override
	public void setBlockData(BlockData data, boolean applyPhysics);

	public void setData(byte data, boolean applyPhysics);

	@Override
	public Collection<ItemStack> getDrops(ItemStack item, Entity entity);
}