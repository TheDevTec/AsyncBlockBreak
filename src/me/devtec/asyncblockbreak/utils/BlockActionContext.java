package me.devtec.asyncblockbreak.utils;

import java.util.List;

import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.devtec.theapi.bukkit.game.BlockDataStorage;

public class BlockActionContext {
	private boolean destroy;
	private Material newType;
	private Object iblockdata;
	private Object destroyedIblockdata;

	private boolean isDripstone;
	private boolean updatePhysics;
	private List<ItemStack> loot;
	private List<ItemStack> tileloot;

	private BlockActionContext(boolean destroy, Material newType) {
		this.destroy = destroy;
		this.newType = newType;
	}

	public static BlockActionContext destroy(Object destroyed, Material newType, List<ItemStack> loot) {
		BlockActionContext action = new BlockActionContext(true, newType);
		action.loot = loot;
		action.destroyedIblockdata = destroyed;
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

	public BlockActionContext withoutUpdatePhysics() {
		updatePhysics = false;
		return this;
	}

	public BlockDataStorage getData() {
		return newType != null ? new BlockDataStorage(newType) : BlockDataStorage.fromData(iblockdata);
	}

	public Object getIBlockData() {
		return iblockdata;
	}

	/**
	 * @apiNote Used only for PaperSpigot AntiXRay
	 * @return IBlockData
	 */
	public Object getDestroyedIBlockData() {
		return destroyedIblockdata;
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

	public List<ItemStack> getLoot() {
		return loot;
	}

	@Nullable
	public List<ItemStack> getTileLoot() {
		return tileloot;
	}

	public void setTileLoot(List<ItemStack> loot) {
		tileloot = loot;
	}

	public void setType(Material type) {
		destroy = false;
		newType = type;
		iblockdata = null;
	}

	public void setIBlockData(Object state) {
		destroy = false;
		newType = null;
		iblockdata = state;
	}

}
