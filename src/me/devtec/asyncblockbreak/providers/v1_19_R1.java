package me.devtec.asyncblockbreak.providers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Consumer;

import com.destroystokyo.paper.antixray.ChunkPacketBlockControllerAntiXray;
import com.google.common.base.Predicates;

import me.devtec.asyncblockbreak.Settings;
import me.devtec.asyncblockbreak.api.LootTable;
import me.devtec.asyncblockbreak.events.AsyncBlockBreakEvent;
import me.devtec.asyncblockbreak.events.AsyncBlockDropItemEvent;
import me.devtec.asyncblockbreak.providers.math.ThreadAccessRandomSource;
import me.devtec.asyncblockbreak.utils.BlockActionContext;
import me.devtec.asyncblockbreak.utils.BlockDestroyHandler;
import me.devtec.shared.Ref;
import me.devtec.shared.dataholder.Config;
import me.devtec.theapi.bukkit.BukkitLoader;
import me.devtec.theapi.bukkit.game.Position;
import me.devtec.theapi.bukkit.xseries.XMaterial;
import net.minecraft.core.BlockPosition;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.PacketPlayInBlockDig;
import net.minecraft.network.protocol.game.PacketPlayInBlockDig.EnumPlayerDigType;
import net.minecraft.network.protocol.game.PacketPlayOutBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.EnumHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityExperienceOrb;
import net.minecraft.world.entity.item.EntityFallingBlock;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.item.ItemDebugStick;
import net.minecraft.world.item.ItemSword;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.ITileEntity;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.AxisAlignedBB;

public class v1_19_R1 implements BlockDestroyHandler {

	private Config spigot = new Config("spigot.yml");

	static Field async = Ref.field(Event.class, "async");
	static IBlockData AIR = Blocks.a.m();

	private boolean IS_PAPER;
	private Field persistentEntitySectionManager;
	private ThreadAccessRandomSource RANDOM_SOURCE;
	private BlocksCalculator_v1_19_R1 calculator;

	public v1_19_R1() {
		IS_PAPER = Ref.getClass("io.papermc.paper.chunk.system.scheduling.NewChunkHolder") != null;
		persistentEntitySectionManager = IS_PAPER ? null : Ref.field(Ref.nms("server.level", "WorldServer"), "P");
		RANDOM_SOURCE = new ThreadAccessRandomSource();
		calculator = new BlocksCalculator_v1_19_R1(RANDOM_SOURCE);
	}

	@Override
	public Map<Position, BlockActionContext> calculateChangedBlocks(Position destroyed, Player player) {
		return calculator.calculateChangedBlocks(new HashMap<>(), destroyed, player, ((CraftPlayer) player).getHandle().fA().f());
	}

	@Override
	public boolean handle(String playerName, Object packetObject) {
		PacketPlayInBlockDig packet = (PacketPlayInBlockDig) packetObject;
		if (packet.d() == EnumPlayerDigType.c) { // stop
			BlockPosition blockPos = packet.b();
			Player player = Bukkit.getPlayer(playerName);
			Position pos = new Position(player.getWorld(), blockPos.u(), blockPos.v(), blockPos.w());
			IBlockData iblockdata = (IBlockData) pos.getIBlockData();
			EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
			net.minecraft.world.item.ItemStack hand = nmsPlayer.fA().f();
			if (hand.c() instanceof ItemDebugStick)
				return false;

			if (cannotBeDestroyed(hand, iblockdata.getBukkitMaterial(), player.getGameMode()) || isInvalid(player, pos)) {
				sendCancelPackets(packet, player, blockPos, iblockdata);
				return true;
			}

			processBlockBreak(packet, player, hand, nmsPlayer, iblockdata, blockPos, pos, true, false);
			return true;
		}
		if (packet.d() == EnumPlayerDigType.a) { // start
			BlockPosition blockPos = packet.b();
			Player player = Bukkit.getPlayer(playerName);
			Position pos = new Position(player.getWorld(), blockPos.u(), blockPos.v(), blockPos.w());
			IBlockData iblockdata = (IBlockData) pos.getIBlockData();
			EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
			net.minecraft.world.item.ItemStack hand = nmsPlayer.fA().f();
			if (hand.c() instanceof ItemDebugStick)
				return false;

			if (player.getGameMode() == GameMode.CREATIVE) {
				if (cannotBeDestroyed(hand, iblockdata.getBukkitMaterial(), player.getGameMode()) || isInvalid(player, pos)
						|| !Settings.Gameplay.BREAKING_WITH_SWORD && hand.c() instanceof ItemSword) {
					sendCancelPackets(packet, player, blockPos, iblockdata);
					return true;
				}
				processBlockBreak(packet, player, hand, nmsPlayer, iblockdata, blockPos, pos, false, true);
				return true;
			}
			float damage = getDamageOfBlock(iblockdata, nmsPlayer, nmsPlayer.s, blockPos); // get damage
			if (damage >= 1.0F) {
				if (cannotBeDestroyed(hand, iblockdata.getBukkitMaterial(), player.getGameMode()) || isInvalid(player, pos)) {
					sendCancelPackets(packet, player, blockPos, iblockdata);
					return true;
				}
				processBlockBreak(packet, player, hand, nmsPlayer, iblockdata, blockPos, pos, true, true);
				return true;
			}
		}
		return false;
	}

	private boolean cannotBeDestroyed(net.minecraft.world.item.ItemStack itemTool, Material bukkitMaterial, GameMode mode) {
		switch (bukkitMaterial) {
		case AIR:
		case CAVE_AIR:
		case VOID_AIR:
		case WATER:
		case LAVA:
		case BUBBLE_COLUMN:
			return true;
		case BARRIER:
		case LIGHT:
		case BEDROCK:
		case STRUCTURE_BLOCK:
		case STRUCTURE_VOID:
		case COMMAND_BLOCK:
		case CHAIN_COMMAND_BLOCK:
		case REPEATING_COMMAND_BLOCK:
		case END_PORTAL_FRAME:
		case END_PORTAL:
		case NETHER_PORTAL:
			return mode != GameMode.CREATIVE;
		default:
			List<Material> canDestroy;
			if (Settings.Performance.CAN_DESTROY_ADVENTURE_FUNCTION && (Settings.Performance.CAN_DESTROY_ONLY_IN_ADVENTURE ? mode == GameMode.ADVENTURE : true)
					&& (canDestroy = getBlocksWhichCanBeDestroyed(itemTool)) != null && !canDestroy.contains(bukkitMaterial))
				return true;
			break;
		}
		return false;
	}

	private List<Material> getBlocksWhichCanBeDestroyed(net.minecraft.world.item.ItemStack itemTool) {
		if (itemTool.u() == null)
			return null;
		NBTTagList list;
		if ((list = itemTool.u().c("CanDestroy", 8)) != null) {
			List<Material> valid = new ArrayList<>(list.size());
			for (NBTBase key : list) {
				String stringType = key.e_();
				if (stringType.length() > 10)
					stringType = stringType.substring(10);
				Optional<XMaterial> type = XMaterial.matchXMaterial(stringType);
				if (type.isPresent())
					valid.add(type.get().parseMaterial());
			}
			return valid.isEmpty() ? null : valid;
		}
		return null;
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
	private void processBlockBreak(PacketPlayInBlockDig packet, Player player, net.minecraft.world.item.ItemStack hand, EntityPlayer nmsPlayer, IBlockData iblockdata, BlockPosition blockPos,
			Position pos, boolean dropItems, boolean instantlyBroken) {

		AsyncBlockBreakEvent event = new AsyncBlockBreakEvent(pos, calculateChangedBlocks(pos, player), player,
				BukkitLoader.getNmsProvider().toMaterial(iblockdata)
						.setNBT(iblockdata.b() instanceof ITileEntity ? BukkitLoader.getNmsProvider().getNBTOfTile(pos.getNMSChunk(), pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()) : null),
				instantlyBroken, BlockFace.valueOf(packet.c().name()));
		event.setDropItems(dropItems);
		if (player.getGameMode() == GameMode.CREATIVE)
			event.setTileDrops(!Settings.Performance.DISABLE_TILE_DROPS);

		if (instantlyBroken) {
			PlayerInteractEvent interactEvent = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, player.getItemInHand(), pos.getBlock(), event.getBlockFace());
			if (PlayerInteractEvent.getHandlerList().getRegisteredListeners().length > 0) {
				if (Settings.Plugins.SYNC_EVENT) {
					BukkitLoader.getNmsProvider().postToMainThread(() -> {
						Bukkit.getPluginManager().callEvent(interactEvent);
						if (interactEvent.isCancelled() || interactEvent.useInteractedBlock() == Result.DENY) {
							sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
							return;
						}

						// Drop exp only in survival / adventure gamemode
						if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL)
							genExpsFromBlock(event, nmsPlayer, hand, iblockdata);

						// Do not call event if isn't registered any listener - instantly process async
						if (BlockExpEvent.getHandlerList().getRegisteredListeners().length == 0) {
							breakBlock(player, pos, iblockdata, nmsPlayer, packet, event, hand);
							return;
						}
						Bukkit.getPluginManager().callEvent(event);
						event.setCompleted();
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
				if (interactEvent.isCancelled() || interactEvent.useInteractedBlock() == Result.DENY) {
					sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
					return;
				}
			}
		}

		// Drop exp only in survival / adventure gamemode
		if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL)
			genExpsFromBlock(event, nmsPlayer, hand, iblockdata);

		// Do not call event if isn't registered any listener - instantly process async
		if (BlockExpEvent.getHandlerList().getRegisteredListeners().length == 0) {
			breakBlock(player, pos, iblockdata, nmsPlayer, packet, event, hand);
			return;
		}

		if (Settings.Plugins.SYNC_EVENT)
			BukkitLoader.getNmsProvider().postToMainThread(() -> {
				if (event.isAsynchronous())
					Ref.set(event, async, false);
				Bukkit.getPluginManager().callEvent(event);
				event.setCompleted();
				if (event.isCancelled()) {
					sendCancelPackets(packet, player, blockPos, (IBlockData) event.getBlockData().getIBlockData());
					return;
				}
				breakBlock(player, pos, iblockdata, nmsPlayer, packet, event, hand);
			});
		else {
			if (!event.isAsynchronous())
				Ref.set(event, async, true);
			Bukkit.getPluginManager().callEvent(event);
			event.setCompleted();
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
			if (Settings.Performance.TICK_NEARBY_BLOCKS && modifyBlock.getValue().shouldUpdatePhysics())
				physics.add(modifyBlock.getKey());
			if (breakEvent.isDropItems() && modifyBlock.getValue().getLoot() != null)
				for (ItemStack stack : modifyBlock.getValue().getLoot())
					loot.add(stack);
			if (breakEvent.doTileDrops() && modifyBlock.getValue().getTileLoot() != null)
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
		if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
			short maxDamage = CraftMagicNumbers.getMaterial(itemInHand.c()).getMaxDurability();
			if (maxDamage > 0) { // Is tool/armor
				int damage = damageTool(nmsPlayer, itemInHand, itemInHand.u() != null && itemInHand.u().q("Unbreakable") ? 0 : 1);
				if (damage > 0)
					if (itemInHand.j() + damage >= maxDamage)
						nmsPlayer.a(EnumHand.a, net.minecraft.world.item.ItemStack.b);
					else
						itemInHand.b(itemInHand.j() + damage);
			}
		}

		// Packet response
		BukkitLoader.getPacketHandler().send(player, new ClientboundBlockChangedAckPacket(packet.e()));

		// Drop items & exp
		Location dropLoc = pos.add(0.5, 0, 0.5).toLocation();
		if (!loot.getItems().isEmpty() || breakEvent.getExpToDrop() > 0)
			MinecraftServer.getServer().execute(() -> {

				// Do not call event if isn't registered any listener
				if (AsyncBlockDropItemEvent.getHandlerList().getRegisteredListeners().length == 0) {
					if (!loot.getItems().isEmpty())
						dropItemsAt(dropLoc, loot.getItems(), breakEvent.getItemConsumer());
					if (breakEvent.getExpToDrop() > 0)
						dropExpsAt(dropLoc, breakEvent.getExpToDrop());
					return;
				}
				AsyncBlockDropItemEvent event = new AsyncBlockDropItemEvent(breakEvent, dropLoc, loot, breakEvent.getExpToDrop());
				Bukkit.getPluginManager().callEvent(event);
				if (!event.isCancelled()) {
					if (!loot.getItems().isEmpty())
						dropItemsAt(event.getLocation(), loot.getItems(), breakEvent.getItemConsumer());
					if (breakEvent.getExpToDrop() > 0)
						dropExpsAt(event.getLocation(), breakEvent.getExpToDrop());
				}
			});
	}

	private void dropItemsAt(Location dropLoc, List<ItemStack> items, Consumer<org.bukkit.entity.Item> itemConsumer) {
		org.bukkit.World world = dropLoc.getWorld();
		if (Settings.Gameplay.STACK_DROPS_INSTANTLY) {
			List<? extends Entity> nearby = getNearbyItems(dropLoc);
			if (nearby.isEmpty())
				for (ItemStack drop : items)
					world.dropItem(dropLoc, drop, itemConsumer);
			else
				for (ItemStack item : items) {
					EntityItem dropped = getStackableFor(item, nearby);
					if (dropped != null) {
						ItemStack itemStack = CraftItemStack.asBukkitCopy(dropped.i());
						int newSize = itemStack.getAmount() + item.getAmount();
						if (newSize <= item.getMaxStackSize()) {
							itemStack.setAmount(newSize);
							dropped.a(CraftItemStack.asNMSCopy(itemStack));
							if (itemConsumer != null)
								itemConsumer.accept((org.bukkit.entity.Item) dropped.getBukkitEntity());
							continue;
						}
						itemStack.setAmount(item.getMaxStackSize());
						dropped.a(CraftItemStack.asNMSCopy(itemStack));
						item.setAmount(newSize - item.getMaxStackSize());
					}
					world.dropItem(dropLoc, item, itemConsumer);
				}
			return;
		}
		for (ItemStack drop : items)
			world.dropItem(dropLoc, drop, itemConsumer);
	}

	private void dropExpsAt(Location dropLoc, int exps) {
		if (Settings.Gameplay.STACK_EXPS_INSTATNTLY) {
			List<? extends Entity> nearby = getNearbyOrbs(dropLoc);
			if (!nearby.isEmpty()) {
				EntityExperienceOrb orb = (EntityExperienceOrb) nearby.get(0);
				orb.aq = orb.aq;
				return;
			}
		}
		dropLoc.getWorld().spawn(dropLoc, EntityType.EXPERIENCE_ORB.getEntityClass(), c -> {
			ExperienceOrb orb = (ExperienceOrb) c;
			orb.setExperience(exps);
		});
	}

	private EntityItem getStackableFor(ItemStack item, List<? extends Entity> nearby) {
		for (Entity entity : nearby) {
			ItemStack stack;
			if ((stack = CraftItemStack.asBukkitCopy(((EntityItem) entity).i())).isSimilar(item) && stack.getMaxStackSize() != stack.getAmount())
				return (EntityItem) entity;
		}
		return null;
	}

	private List<? extends Entity> getNearbyItems(Location dropLoc) {
		double radius = spigot.getDouble("world-settings." + dropLoc.getWorld().getName() + ".merge-radius.item", spigot.getDouble("world-settings.default.merge-radius.item", 4.5));
		return getNearbyEntities(EntityItem.class, dropLoc, radius);
	}

	private List<? extends Entity> getNearbyEntities(Class<? extends Entity> entityClass, Location dropLoc, double radius) {
		BoundingBox boundingBox = BoundingBox.of(dropLoc, radius, radius, radius);
		AxisAlignedBB bb = new AxisAlignedBB(boundingBox.getMinX(), boundingBox.getMinY(), boundingBox.getMinZ(), boundingBox.getMaxX(), boundingBox.getMaxY(), boundingBox.getMaxZ());
		return ((CraftWorld) dropLoc.getWorld()).getHandle().a(EntityTypeTest.a(entityClass), bb, Predicates.alwaysTrue());
	}

	private List<? extends Entity> getNearbyOrbs(Location dropLoc) {
		double radius = spigot.getDouble("world-settings." + dropLoc.getWorld().getName() + ".merge-radius.exp", spigot.getDouble("world-settings.default.merge-radius.exp", 6.5));
		return getNearbyEntities(EntityExperienceOrb.class, dropLoc, radius);
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
