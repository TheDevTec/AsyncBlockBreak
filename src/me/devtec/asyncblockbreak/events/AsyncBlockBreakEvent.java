package me.devtec.asyncblockbreak.events;

import java.lang.reflect.Field;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Consumer;

import me.devtec.asyncblockbreak.Loader;
import me.devtec.shared.Ref;
import me.devtec.theapi.bukkit.game.BlockDataStorage;

public class AsyncBlockBreakEvent extends BlockBreakEvent {
	private static final Field async = Ref.field(Event.class, "async");

	private BlockDataStorage blockData;
	private boolean isInstant;
	private BlockFace face;
	private Consumer<Item> consumer;

	public AsyncBlockBreakEvent(Block theBlock, Player player, BlockDataStorage blockData, boolean instantlyBroken, BlockFace face) {
		super(theBlock, player);
		this.blockData = blockData;
		isInstant = instantlyBroken;
		this.face = face;
		if (!Loader.SYNC_EVENT)
			Ref.set(this, async, true);
	}

	public BlockFace getBlockFace() {
		return face;
	}

	public boolean isInstant() {
		return isInstant;
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
