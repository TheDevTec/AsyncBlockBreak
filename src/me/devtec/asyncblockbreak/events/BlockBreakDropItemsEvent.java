package me.devtec.asyncblockbreak.events;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.devtec.asyncblockbreak.api.LootTable;

public class BlockBreakDropItemsEvent extends Event implements Cancellable {
	private static final HandlerList handlers = new HandlerList();

	private AsyncBlockBreakEvent blockEvent;
	private LootTable loots;
	private boolean cancel;

	public BlockBreakDropItemsEvent(AsyncBlockBreakEvent blockEvent, LootTable loots) {
		this.blockEvent = blockEvent;
		this.loots = loots;
	}

	public Block getBlock() {
		return blockEvent.getBlock();
	}

	public Player getPlayer() {
		return blockEvent.getPlayer();
	}

	public AsyncBlockBreakEvent getBlockBreakEvent() {
		return blockEvent;
	}

	public LootTable getLoot() {
		return loots;
	}

	@Override
	public boolean isCancelled() {
		return cancel;
	}

	@Override
	public void setCancelled(boolean cancelStatus) {
		cancel = cancelStatus;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
