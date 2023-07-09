package me.devtec.asyncblockbreak.utils;

import java.util.List;

import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.devtec.theapi.bukkit.game.BlockDataStorage;
import me.devtec.theapi.bukkit.game.Position;

public class BlockActionContext {
	private boolean destroy;
	private Material original;
	private Material newType;
	private Object iblockdata;
	private Object destroyedIblockdata;

	private boolean isDripstone;
	private boolean updatePhysics;
	private List<ItemStack> loot;
	private List<ItemStack> tileloot;

	private BlockActionContext(boolean destroy, Material original, Material newType, boolean tick) {
		this.destroy = destroy;
		this.newType = newType;
		this.original = original;
		updatePhysics = tick;
	}

	public static BlockActionContext destroy(Object destroyed, Material original, Material newType, List<ItemStack> loot, boolean tick) {
		BlockActionContext action = new BlockActionContext(true, original, newType, tick);
		action.loot = loot;
		action.destroyedIblockdata = destroyed;
		return action;
	}

	public static BlockActionContext destroyDripstone(Object iblockdata) {
		BlockActionContext action = new BlockActionContext(true, Material.POINTED_DRIPSTONE, Material.AIR, true);
		action.isDripstone = true;
		action.iblockdata = iblockdata;
		return action;
	}

	public static BlockActionContext updateState(Material newType) {
		return new BlockActionContext(false, null, newType, false);
	}

	public static BlockActionContext updateState(Object iblockdata) {
		BlockActionContext action = new BlockActionContext(false, null, null, false);
		action.iblockdata = iblockdata;
		return action;
	}

	public static BlockActionContext updatePhysics() {
		return new BlockActionContext(false, null, null, true);
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

	/**
	 * @apiNote Non-null only if block is destroyed
	 * @return Material
	 */
	@Nullable
	public Material getOriginalType() {
		return original;
	}

	public void processBreakingLootCheck(Position pos) {
		if (loot != null && !loot.isEmpty() && pos.getBukkitType() != getOriginalType())
			loot.clear();
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
