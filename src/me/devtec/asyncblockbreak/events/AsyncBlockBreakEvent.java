package me.devtec.asyncblockbreak.events;

import java.util.Map;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Consumer;

import me.devtec.asyncblockbreak.api.LootTable;
import me.devtec.asyncblockbreak.utils.BlockActionContext;
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
	private Map<Position, BlockActionContext> modifiedBlocks;
	protected final Integer[] result;

	public AsyncBlockBreakEvent(Integer[] result, Position initBlock, Map<Position, BlockActionContext> modifiedBlocks, Player player, BlockDataStorage blockData, boolean instantlyBroken,
			BlockFace face) {
		super(new AsyncCraftBlock(initBlock, modifiedBlocks.get(initBlock), blockData), player);
		((AsyncCraftBlock) getBlock()).setEvent(this);
		this.result = result;
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

	@Override
	public void setDropItems(boolean status) {
		super.setDropItems(status);
		tileDrops = status;
		loot.clear();
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

	public void setFinished() {
		result[0] = 1;
	}

	public void replaceEventStatement(Position initBlock, Map<Position, BlockActionContext> modifiedBlocks, Player player, BlockDataStorage blockData, boolean instantlyBroken, BlockFace face) {
		setDropItems(true);
		setCancelled(false);
		setExpToDrop(0);
		consumer = null;
		result[0] = 0;
		this.blockData = blockData;
		isInstant = instantlyBroken;
		pos = initBlock;
		this.modifiedBlocks = modifiedBlocks;
		this.face = face;
		((AsyncCraftBlock) getBlock()).updateEvent(initBlock, modifiedBlocks.get(initBlock), blockData);
	}
}
