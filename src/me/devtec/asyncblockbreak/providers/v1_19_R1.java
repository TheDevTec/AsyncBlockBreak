package me.devtec.asyncblockbreak.providers;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftMagicNumbers;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

import com.destroystokyo.paper.antixray.ChunkPacketBlockControllerAntiXray;

import me.devtec.asyncblockbreak.Loader;
import me.devtec.asyncblockbreak.api.LootTable;
import me.devtec.asyncblockbreak.events.AsyncBlockBreakEvent;
import me.devtec.asyncblockbreak.events.AsyncBlockDropItemEvent;
import me.devtec.asyncblockbreak.providers.math.ThreadAccessRandomSource;
import me.devtec.asyncblockbreak.utils.BlockActionContext;
import me.devtec.asyncblockbreak.utils.BlockDestroyHandler;
import me.devtec.shared.Ref;
import me.devtec.theapi.bukkit.BukkitLoader;
import me.devtec.theapi.bukkit.game.Position;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.PacketPlayInBlockDig;
import net.minecraft.network.protocol.game.PacketPlayInBlockDig.EnumPlayerDigType;
import net.minecraft.network.protocol.game.PacketPlayOutBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.EnumHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.EntityFallingBlock;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.ITileEntity;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

public class v1_19_R1 implements BlockDestroyHandler {
	static Field async = Ref.field(Event.class, "async");
	static IBlockData AIR = Blocks.a.m();

	private boolean IS_PAPER = Ref.getClass("io.papermc.paper.chunk.system.scheduling.NewChunkHolder") != null;
	private Field persistentEntitySectionManager = IS_PAPER ? null : Ref.field(Ref.nms("server.level", "WorldServer"), "P");

	private ThreadAccessRandomSource RANDOM_SOURCE = new ThreadAccessRandomSource();
	private BlocksCalculator_v1_19_R1 calculator = new BlocksCalculator_v1_19_R1(RANDOM_SOURCE);

	@Override
	public Map<Position, BlockActionContext> calculateChangedBlocks(Position destroyed, Player player) {
		return calculateChangedBlocks(destroyed, player, ((CraftPlayer) player).getHandle().fA().f());
	}

	public Map<Position, BlockActionContext> calculateChangedBlocks(Position destroyed, Player player, net.minecraft.world.item.ItemStack itemInHand) {
		return calculator.calculateChangedBlocks(destroyed, player, itemInHand);
	}

	@Override
	public boolean handle(String playerName, Object packetObject) {
		PacketPlayInBlockDig packet = (PacketPlayInBlockDig) packetObject;
		if (packet.d() == EnumPlayerDigType.c) { // stop
			BlockPosition blockPos = packet.b();
			Player player = Bukkit.getPlayer(playerName);
			Position pos = new Position(player.getWorld(), blockPos.u(), blockPos.v(), blockPos.w());
			IBlockData iblockdata = (IBlockData) pos.getIBlockData();
			if (iblockdata.getBukkitMaterial().isAir() || isInvalid(player, pos)) {
				sendCancelPackets(packet, player, blockPos, iblockdata);
				return true;
			}

			EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
			processBlockBreak(packet, player, nmsPlayer, iblockdata, blockPos, pos, true, false);
			return true;
		}
		if (packet.d() == EnumPlayerDigType.a) { // start
			BlockPosition blockPos = packet.b();
			Player player = Bukkit.getPlayer(playerName);
			Position pos = new Position(player.getWorld(), blockPos.u(), blockPos.v(), blockPos.w());
			IBlockData iblockdata = (IBlockData) pos.getIBlockData();
			EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();

			if (player.getGameMode() == GameMode.CREATIVE) {
				if (iblockdata.getBukkitMaterial().isAir() || isInvalid(player, pos)) {
					sendCancelPackets(packet, player, blockPos, iblockdata);
					return true;
				}
				processBlockBreak(packet, player, nmsPlayer, iblockdata, blockPos, pos, false, true);
				return true;
			}
			// iblockdata.a(nmsPlayer.s, blockPos, nmsPlayer); // hit block
			float f = getDamageOfBlock(iblockdata, nmsPlayer, nmsPlayer.s, blockPos); // get damage
			if (f >= 1.0F) {
				if (iblockdata.getBukkitMaterial().isAir() || isInvalid(player, pos)) {
					sendCancelPackets(packet, player, blockPos, iblockdata);
					return true;
				}
				processBlockBreak(packet, player, nmsPlayer, iblockdata, blockPos, pos, true, true);
				return true;
			}
		}
		return false;
	}

	private float getDamageOfBlock(IBlockData iblockdata, EntityPlayer nmsPlayer, World world, BlockPosition blockPos) {
		final float hardness = iblockdata.h(world, blockPos);
		if (hardness == -1.0f)
			return 0.0f;
		final int i = nmsPlayer.d(iblockdata) ? 30 : 100;
		return nmsPlayer.c(iblockdata) / hardness / i;
	}

	/**
	 * @apiNote Call AsyncBlockBreakEvent and convert whole block break to the sync
	 *          if needed
	 */
	private void processBlockBreak(PacketPlayInBlockDig packet, Player player, EntityPlayer nmsPlayer, IBlockData iblockdata, BlockPosition blockPos, Position pos, boolean dropItems,
			boolean instantlyBroken) {
		Integer[] integer = { 0 };
		AsyncBlockBreakEvent event = new AsyncBlockBreakEvent(integer, pos, calculateChangedBlocks(pos, player), player,
				BukkitLoader.getNmsProvider().toMaterial(iblockdata)
						.setNBT(iblockdata.b() instanceof ITileEntity ? BukkitLoader.getNmsProvider().getNBTOfTile(pos.getNMSChunk(), pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()) : null),
				instantlyBroken, BlockFace.valueOf(packet.c().name()));
		if (player.getGameMode() == GameMode.CREATIVE)
			event.setTileDrops(!Loader.DISABLE_TILE_DROPS);
		event.setDropItems(dropItems);

		if (instantlyBroken) {
			PlayerInteractEvent interactEvent = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, player.getItemInHand(), pos.getBlock(), event.getBlockFace());
			if (PlayerInteractEvent.getHandlerList().getRegisteredListeners().length > 0) {
				if (Loader.SYNC_EVENT) {
					BukkitLoader.getNmsProvider().postToMainThread(() -> {
						Bukkit.getPluginManager().callEvent(interactEvent);
						if (interactEvent.isCancelled() || interactEvent.useInteractedBlock() == Result.DENY) {
							sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
							return;
						}

						net.minecraft.world.item.ItemStack hand = nmsPlayer.fA().f();
						// Drop exp only in survival / adventure gamemode
						if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL)
							genExpsFromBlock(event, nmsPlayer, hand, iblockdata);

						// Do not call event if isn't registered any listener - instantly process async
						if (BlockExpEvent.getHandlerList().getRegisteredListeners().length == 0) {
							breakBlock(player, pos, iblockdata, nmsPlayer, packet, event, hand);
							return;
						}
						Bukkit.getPluginManager().callEvent(event);
						integer[0] = 1;
						if (event.isCancelled()) {
							sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
							return;
						}
						breakBlock(player, pos, iblockdata, nmsPlayer, packet, event, hand);
					});
					return;
				}
				Ref.set(interactEvent, async, true);
				Bukkit.getPluginManager().callEvent(interactEvent);
				if (event.isCancelled()) {
					sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
					return;
				}
			}
		}

		net.minecraft.world.item.ItemStack hand = nmsPlayer.fA().f();

		// Drop exp only in survival / adventure gamemode
		if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL)
			genExpsFromBlock(event, nmsPlayer, hand, iblockdata);

		// Do not call event if isn't registered any listener - instantly process async
		if (BlockExpEvent.getHandlerList().getRegisteredListeners().length == 0) {
			breakBlock(player, pos, iblockdata, nmsPlayer, packet, event, hand);
			return;
		}

		if (Loader.SYNC_EVENT)
			BukkitLoader.getNmsProvider().postToMainThread(() -> {
				Bukkit.getPluginManager().callEvent(event);
				integer[0] = 1;
				if (event.isCancelled()) {
					sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
					return;
				}
				breakBlock(player, pos, iblockdata, nmsPlayer, packet, event, hand);
			});
		else {
			Ref.set(event, async, true);
			Bukkit.getPluginManager().callEvent(event);
			integer[0] = 1;
			if (event.isCancelled()) {
				sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
				return;
			}
			breakBlock(player, pos, iblockdata, nmsPlayer, packet, event, hand);
		}
	}

	private void genExpsFromBlock(AsyncBlockBreakEvent event, EntityPlayer nmsPlayer, net.minecraft.world.item.ItemStack hand, IBlockData iblockdata) {
		if (nmsPlayer.d(iblockdata) && EnchantmentManager.a(Enchantments.v, hand) == 0) { // no silktouch
			if (iblockdata.b() instanceof DropExperienceBlock expBlock)
				event.setExpToDrop(1 + RANDOM_SOURCE.percentChance(5));
			switch (iblockdata.getBukkitMaterial()) {
			case COAL_ORE:
			case DEEPSLATE_COAL_ORE:
				event.setExpToDrop(1 + RANDOM_SOURCE.percentChance(2));
				break;
			case NETHER_GOLD_ORE:
				event.setExpToDrop(1 + RANDOM_SOURCE.percentChance(1));
				break;
			case DIAMOND_ORE:
			case DEEPSLATE_DIAMOND_ORE:
			case EMERALD_ORE:
			case DEEPSLATE_EMERALD_ORE:
				event.setExpToDrop(3 + RANDOM_SOURCE.percentChance(7));
				break;
			case REDSTONE_ORE:
			case DEEPSLATE_REDSTONE_ORE:
				event.setExpToDrop(1 + RANDOM_SOURCE.percentChance(5));
				break;
			case LAPIS_ORE:
			case DEEPSLATE_LAPIS_ORE:
			case NETHER_QUARTZ_ORE:
				event.setExpToDrop(2 + RANDOM_SOURCE.percentChance(5));
				break;
			case SPAWNER:
				event.setExpToDrop(15 + RANDOM_SOURCE.percentChance(43));
				break;
			case SCULK:
				event.setExpToDrop(1);
				break;
			case SCULK_SENSOR:
			case SCULK_SHRIEKER:
			case SCULK_CATALYST:
				event.setExpToDrop(5);
				break;
			default:
				break;
			}
		}
	}

	/**
	 * @apiNote Process block break in the world and send packets (after
	 *          AsyncBlockBreakEvent, see
	 *          {@link v1_19_R1#processBlockBreak(PacketPlayInBlockDig, Player, EntityPlayer, IBlockData, BlockPosition, Position, boolean, boolean)}
	 */
	public void breakBlock(Player player, Position pos, IBlockData iblockdata, EntityPlayer nmsPlayer, PacketPlayInBlockDig packet, AsyncBlockBreakEvent breakEvent,
			net.minecraft.world.item.ItemStack itemInHand) {
		LootTable loot = breakEvent.getLoot();

		WorldServer worldServer = ((CraftWorld) pos.getWorld()).getHandle();
		boolean ANTI_XRAY = IS_PAPER ? worldServer.chunkPacketBlockController instanceof ChunkPacketBlockControllerAntiXray : false;
		Set<Position> physics = new HashSet<>();
		physics.add(pos);

		for (Entry<Position, BlockActionContext> modifyBlock : breakEvent.getModifiedBlocks().entrySet()) {
			if (modifyBlock.getValue().shouldUpdatePhysics())
				physics.add(modifyBlock.getKey());
			if (breakEvent.isDropItems() && modifyBlock.getValue().getLoot() != null)
				for (ItemStack stack : modifyBlock.getValue().getLoot())
					loot.add(stack);
			if (breakEvent.isDropItems() && breakEvent.doTileDrops() && modifyBlock.getValue().getTileLoot() != null)
				for (ItemStack stack : modifyBlock.getValue().getTileLoot())
					loot.add(stack);
			if (modifyBlock.getValue().isDestroy()) {
				if (ANTI_XRAY)
					worldServer.chunkPacketBlockController.onBlockChange(worldServer, (BlockPosition) modifyBlock.getKey().getBlockPosition(), Blocks.a.m(),
							(IBlockData) modifyBlock.getValue().getDestroyedIBlockData(), 2, 2);
				removeEntitiesFrom(modifyBlock.getKey(), worldServer, loot);
				modifyBlock.getKey().setTypeAndUpdate(modifyBlock.getValue().getData(), false);
				if (modifyBlock.getValue().isDripstone())
					fallDripstone(worldServer, (BlockPosition) modifyBlock.getKey().getBlockPosition(), (IBlockData) modifyBlock.getValue().getIBlockData());
			} else if (modifyBlock.getValue().getIBlockData() != null || modifyBlock.getValue().getType() != null)
				modifyBlock.getKey().setTypeAndUpdate(modifyBlock.getValue().getData(), false);
		}

		// water & lava physics
		BukkitLoader.getNmsProvider().postToMainThread(() -> physics.forEach(t -> t.updatePhysics(AIR)));

		// Damage tool
		short maxDamage = CraftMagicNumbers.getMaterial(itemInHand.c()).getMaxDurability();
		if (maxDamage > 0) { // Is tool/armor
			int damage = damageTool(nmsPlayer, itemInHand, itemInHand.u() != null && itemInHand.u().q("Unbreakable") ? 0 : 1);
			if (damage > 0)
				if (itemInHand.j() + damage >= maxDamage)
					nmsPlayer.a(EnumHand.a, net.minecraft.world.item.ItemStack.b);
				else
					itemInHand.b(itemInHand.j() + damage);
		}

		// Packet response
		BukkitLoader.getPacketHandler().send(player, new ClientboundBlockChangedAckPacket(packet.e()));

		// Drop items & exp
		Location dropLoc = pos.add(0.5, 0, 0.5).toLocation();
		if (!loot.getItems().isEmpty() && breakEvent.isDropItems() || breakEvent.getExpToDrop() > 0)
			MinecraftServer.getServer().execute(() -> {

				// Do not call event if isn't registered any listener
				if (AsyncBlockDropItemEvent.getHandlerList().getRegisteredListeners().length == 0) {
					if (!loot.getItems().isEmpty() && breakEvent.isDropItems())
						for (ItemStack drop : loot.getItems())
							player.getWorld().dropItem(dropLoc, drop, breakEvent.getItemConsumer());
					if (breakEvent.getExpToDrop() > 0)
						player.getWorld().spawn(dropLoc, EntityType.EXPERIENCE_ORB.getEntityClass(), c -> {
							ExperienceOrb orb = (ExperienceOrb) c;
							orb.setExperience(breakEvent.getExpToDrop());
						});
					return;
				}
				AsyncBlockDropItemEvent event = new AsyncBlockDropItemEvent(breakEvent, dropLoc, loot, breakEvent.getExpToDrop());
				Bukkit.getPluginManager().callEvent(event);
				if (!event.isCancelled()) {
					for (ItemStack drop : event.getLoot().getItems())
						player.getWorld().dropItem(event.getLocation(), drop, breakEvent.getItemConsumer());
					if (breakEvent.getExpToDrop() > 0)
						player.getWorld().spawn(event.getLocation(), EntityType.EXPERIENCE_ORB.getEntityClass(), c -> {
							ExperienceOrb orb = (ExperienceOrb) c;
							orb.setExperience(breakEvent.getExpToDrop());
						});
				}
			});
	}

	/**
	 * @apiNote Call PlayerItemDamageEvent and damage item
	 */
	private int damageTool(EntityPlayer player, net.minecraft.world.item.ItemStack item, int damage) {
		int enchant = EnchantmentManager.a(Enchantments.w, item);

		if (enchant > 0 && genDamageChance(item, enchant, RANDOM_SOURCE))
			--damage;

		PlayerItemDamageEvent event = new PlayerItemDamageEvent(player.getBukkitEntity(), CraftItemStack.asCraftMirror(item), damage);
		if (!Bukkit.isPrimaryThread())
			Ref.set(event, async, true);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled())
			return 0;

		return event.getDamage();
	}

	private boolean genDamageChance(net.minecraft.world.item.ItemStack item, int level, ThreadAccessRandomSource random) {
		if (item.c() instanceof net.minecraft.world.item.ItemArmor && random.floatChance() < 0.6F)
			return false;
		return random.percentChance(level + 1) > 0;
	}

	/**
	 * @apiNote Send to the player block break cancel packets
	 */
	private void sendCancelPackets(PacketPlayInBlockDig packet, Player player, BlockPosition blockPos, IBlockData iblockdata) {
		BukkitLoader.getPacketHandler().send(player, new PacketPlayOutBlockChange(blockPos, iblockdata));
		BukkitLoader.getPacketHandler().send(player, new ClientboundBlockChangedAckPacket(packet.e()));
	}

	/**
	 * @apiNote Kill connected entities to the block
	 */
	private void removeEntitiesFrom(Position pos, WorldServer world, LootTable items) {
		Chunk chunk = (Chunk) pos.getNMSChunk();
		if (IS_PAPER)
			for (org.bukkit.entity.Entity entity : chunk.getChunkHolder().getEntityChunk().getChunkEntities())
				removeEntity(pos, items, entity);
		else {
			@SuppressWarnings("unchecked")
			PersistentEntitySectionManager<Entity> entityManager = (PersistentEntitySectionManager<Entity>) Ref.get(world, persistentEntitySectionManager);
			for (org.bukkit.entity.Entity entity : entityManager.getEntities(new ChunkCoordIntPair(pos.getBlockX() >> 4, pos.getBlockZ() >> 4)).stream().map(Entity::getBukkitEntity)
					.filter(Objects::nonNull).toArray(paramInt -> new org.bukkit.entity.Entity[paramInt]))
				removeEntity(pos, items, entity);
		}
	}

	/**
	 * @apiNote Kill entities, see
	 *          {@link #removeEntitiesFrom(Position, WorldServer, Position, LootTable, boolean)}
	 */
	private void removeEntity(Position pos, LootTable items, org.bukkit.entity.Entity entity) {
		if (entity instanceof ItemFrame frame) {
			Location loc = entity.getLocation().add(frame.getAttachedFace().getDirection());
			if (loc.getBlockX() == pos.getBlockX() && loc.getBlockY() == pos.getBlockY() && loc.getBlockZ() == pos.getBlockZ()) {
				items.add(frame.getItem());
				items.add(new ItemStack(Material.ITEM_FRAME));
				frame.remove();
			}
		}
		if (entity instanceof Painting paint) {
			Location loc = entity.getLocation().add(paint.getAttachedFace().getDirection());
			if (loc.getBlockX() == pos.getBlockX() && loc.getBlockY() == pos.getBlockY() && loc.getBlockZ() == pos.getBlockZ()) {
				items.add(new ItemStack(Material.PAINTING));
				paint.remove();
			}
		}
	}

	/**
	 * @apiNote Apply physics on dripstone
	 */
	private void fallDripstone(WorldServer world, BlockPosition bPos, IBlockData data) {
		if (!Bukkit.isPrimaryThread()) {
			BukkitLoader.getNmsProvider().postToMainThread(() -> {
				fallDripstone(world, bPos, data);
			});
			return;
		}
		EntityFallingBlock dripstone = EntityFallingBlock.a(world, bPos, data);
		int i = Math.max(1 + bPos.v() - bPos.i().v(), 6);
		float f = 1.0F * i;
		dripstone.b(f, 40);
	}
}
