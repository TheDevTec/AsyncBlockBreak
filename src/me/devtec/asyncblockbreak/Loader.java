package me.devtec.asyncblockbreak;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import me.devtec.asyncblockbreak.events.AsyncBlockBreakEvent;
import me.devtec.asyncblockbreak.utils.BlockDestroyHandler;
import me.devtec.shared.Ref;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.DataType;
import me.devtec.shared.scheduler.Tasker;
import me.devtec.theapi.bukkit.packetlistener.PacketListener;

public class Loader extends JavaPlugin implements Listener {
	public static Map<UUID, Integer> destroyedCountInTick = new ConcurrentHashMap<>();
	public static List<UUID> kick = new ArrayList<>();
	public static BlockDestroyHandler handler;
	private PacketListener listener;

	@EventHandler
	public void quit(PlayerQuitEvent e) {
		kick.remove(e.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onBreak(AsyncBlockBreakEvent e) {
		e.setCancelled(true);
	}

	@Override
	public void onEnable() {
		initConfig();

		initProvider();

		if (Settings.AntiCheat.ACTION_KICK_PLAYER)
			Bukkit.getPluginManager().registerEvents(this, this);

		new Tasker() {
			@Override
			public void run() {
				destroyedCountInTick.clear();
			}
		}.runRepeating(1, 1);

		listener = new PacketListener() {

			@Override
			public boolean playOut(String playerName, Object packet, Object channel) {
				return false;
			}

			@Override
			public boolean playIn(String playerName, Object oPacket, Object channel) {
				if (playerName == null)
					return false;
				if (oPacket.getClass().getSimpleName().equals("PacketPlayInBlockDig"))
					return handler.handle(playerName, oPacket);
				return false;
			}
		};
		listener.register();
	}

	private void initProvider() {
		handler = (BlockDestroyHandler) Ref.newInstanceByClass("me.devtec.asyncblockbreak.providers.nms." + Ref.serverVersion());
		System.out.println(handler.getClass().getName());
	}

	private void initConfig() {
		Config config = Config.loadFromPlugin(getClass(), "config.yml", "plugins/AsyncBlockBreak/config.yml").save(DataType.YAML);
		// AntiCheat
		Settings.AntiCheat.ACTION_KICK_PLAYER = config.getBoolean("anticheat.actions.kick_player");
		Settings.AntiCheat.ACTION_BROADCAST_CONSOLE = config.getBoolean("anticheat.actions.broadcast_console");
		Settings.AntiCheat.ACTION_BROADCAST_ADMINS = config.getBoolean("anticheat.actions.broadcast_admins");
		Settings.AntiCheat.DESTROYED_BLOCKS_LIMIT = config.getInt("anticheat.destroyed_blocks_limit");
		Settings.AntiCheat.MAXIMUM_RADIUS_WITHIN_BLOCK_AND_PLAYER = config.getInt("anticheat.max_radius_within_block_and_player");
		// Plugins
		Settings.Plugins.SYNC_EVENT = config.getBoolean("settings.plugins.syncEvent");
		// Performance
		Settings.Performance.CHORUS_PLANT = config.getBoolean("settings.performance.chorus_plant");
		Settings.Performance.CONNECTED_BLOCKS = config.getBoolean("settings.performance.connected_blocks");
		Settings.Performance.TICK_LEAVES = config.getBoolean("settings.performance.tick_leaves");
		Settings.Performance.TICK_NEARBY_BLOCKS = config.getBoolean("settings.performance.tick_nearby_blocks");
		Settings.Performance.DISABLE_TILE_DROPS = config.getBoolean("settings.performance.disable_tile_drops");
		Settings.Performance.CAN_DESTROY_ADVENTURE_FUNCTION = config.getBoolean("settings.performance.can_destroy_adventure_function");
		Settings.Performance.CAN_DESTROY_ONLY_IN_ADVENTURE = config.getBoolean("settings.performance.can_destroy_only_in_adventure_function");
		// Gameplay
		Settings.Gameplay.BREAKING_WITH_SWORD = config.getBoolean("settings.gameplay.breaking_with_sword");
		Settings.Gameplay.LADDER_WORKS_AS_VINE = config.getBoolean("settings.gameplay.ladder_works_as_vine");
		Settings.Gameplay.STACK_DROPS_INSTANTLY = config.getBoolean("settings.gameplay.stack_instantly_without_dropping_item");
		Settings.Gameplay.STACK_EXPS_INSTATNTLY = config.getBoolean("settings.gameplay.stack_instantly_exps_without_spawning_orb");
	}

	@Override
	public void onDisable() {
		if (listener != null)
			listener.unregister();
	}
}
