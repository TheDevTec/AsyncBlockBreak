package me.devtec.asyncblockbreak.events;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Consumer;

import me.devtec.asyncblockbreak.api.LootTable;
import me.devtec.theapi.bukkit.game.BlockDataStorage;
import me.devtec.theapi.bukkit.game.Position;

public class AsyncBlockBreakEvent extends BlockBreakEvent {
	private BlockDataStorage blockData;
	private boolean isInstant;
	private BlockFace face;
	private boolean tileDrops;
	private Consumer<Item> consumer;
	private LootTable loot;
	private Position pos;

	public AsyncBlockBreakEvent(Position pos, Player player, BlockDataStorage blockData, boolean instantlyBroken, BlockFace face) {
		super(pos.getBlock(), player);
		this.blockData = blockData;
		isInstant = instantlyBroken;
		this.pos = pos;
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
