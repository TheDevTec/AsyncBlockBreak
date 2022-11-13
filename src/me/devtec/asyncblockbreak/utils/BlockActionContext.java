package me.devtec.asyncblockbreak.utils;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.devtec.theapi.bukkit.game.BlockDataStorage;
import net.minecraft.world.level.block.state.IBlockData;

public class BlockActionContext {
	private boolean destroy;
	private Material newType;
	private Object iblockdata;

	private boolean isDripstone;
	private boolean updatePhysics;
	public List<ItemStack> loot;

	private BlockActionContext(boolean destroy, Material newType) {
		this.destroy = destroy;
		this.newType = newType;
	}

	public static BlockActionContext destroy(Material newType, List<ItemStack> loot) {
		BlockActionContext action = new BlockActionContext(true, newType);
		action.loot = loot;
		return action;
	}

	public static BlockActionContext destroyDripstone(Object iblockdata) {
		BlockActionContext action = new BlockActionContext(true, Material.AIR);
		action.isDripstone = true;
		action.iblockdata = iblockdata;
		return action;
	}

	public static BlockActionContext updateState(Material newType) {
		return new BlockActionContext(false, newType);
	}

	public static BlockActionContext updateState(Object iblockdata) {
		BlockActionContext action = new BlockActionContext(false, null);
		action.iblockdata = iblockdata;
		return action;
	}

	public static BlockActionContext updatePhysics() {
		BlockActionContext action = new BlockActionContext(false, null);
		action.updatePhysics = true;
		return action;
	}

	public BlockActionContext doUpdatePhysics() {
		updatePhysics = true;
		return this;
	}

	public BlockDataStorage getData() {
		return newType != null ? new BlockDataStorage(newType) : BlockDataStorage.fromData(iblockdata);
	}

	public Object getIBlockData() {
		return iblockdata;
	}

	public boolean isDestroy() {
		return destroy;
	}

	public boolean isDripstone() {
		return isDripstone;
	}

	public boolean shouldUpdatePhysics() {
		return updatePhysics;
	}

	public Material getType() {
		return newType;
	}

	public void setType(Material type) {
		destroy = false;
		newType = type;
		iblockdata = null;
	}

	public void setIBlockData(IBlockData state) {
		destroy = false;
		newType = null;
		iblockdata = state;
	}

}
