package me.devtec.asyncblockbreak.api;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

public class LootTable {
	private List<ItemStack> itemsToDrop = new ArrayList<>();

	public LootTable add(ItemStack item) {
		if (item == null || item.getType().isAir())
			return this;
		for (ItemStack i : itemsToDrop)
			if (i.isSimilar(item) && i.getAmount() < i.getType().getMaxStackSize()) {
				int newSize = i.getAmount() + item.getAmount();
				if (newSize <= i.getType().getMaxStackSize()) {
					i.setAmount(newSize);
					return this;
				}
				i.setAmount(i.getType().getMaxStackSize());
				newSize -= i.getType().getMaxStackSize();
				item.setAmount(newSize);
			}
		itemsToDrop.add(item);
		return this;
	}

	public List<ItemStack> getItems() {
		return itemsToDrop;
	}

	public void setItems(List<ItemStack> items) {
		itemsToDrop = items;
	}

	public void clear() {
		itemsToDrop.clear();
	}
}
