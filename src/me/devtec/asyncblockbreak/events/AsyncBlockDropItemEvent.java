package me.devtec.asyncblockbreak.events;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.devtec.asyncblockbreak.api.LootTable;

public class AsyncBlockDropItemEvent extends Event implements Cancellable {
	private static final HandlerList handlers = new HandlerList();

	private AsyncBlockBreakEvent blockEvent;
	private Location location;
	private LootTable loot;
	private int expToDrop;
	private boolean cancel;

	public AsyncBlockDropItemEvent(AsyncBlockBreakEvent blockEvent, Location location, LootTable loot, int expToDrop) {
		this.blockEvent = blockEvent;
		this.loot = loot;
		this.location = location;
		this.expToDrop = expToDrop;
	}

	public Block getBlock() {
		return blockEvent.getBlock();
	}

	public Location getLocation() {
		return location;
	}

	public void setLocation(Location loc) {
		location = loc;
	}

	public Player getPlayer() {
		return blockEvent.getPlayer();
	}

	public AsyncBlockBreakEvent getBlockBreakEvent() {
		return blockEvent;
	}

	public LootTable getLoot() {
		return loot;
	}

	public int getExpToDrop() {
		return expToDrop;
	}

	public void setExpToDrop(int expToDrop) {
		this.expToDrop = expToDrop;
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
