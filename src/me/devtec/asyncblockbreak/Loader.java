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

import me.devtec.asyncblockbreak.utils.BlockDestroyHandler;
import me.devtec.shared.Ref;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.DataType;
import me.devtec.shared.scheduler.Tasker;
import me.devtec.theapi.bukkit.packetlistener.PacketListener;

public class Loader extends JavaPlugin implements Listener {
	public static boolean DISABLE_TILE_DROPS;
	public static int DESTROYED_BLOCKS_LIMIT;
	public static double MAXIMUM_RADIUS_WITHIN_BLOCK_AND_PLAYER;
	public static boolean SYNC_EVENT;
	public static boolean KICK_PLAYER;
	public static boolean BROADCAST_CONSOLE;
	public static boolean BROADCAST_ADMINS;
	public static boolean TICK_LEAVES;

	public static Map<UUID, Integer> destroyedCountInTick = new ConcurrentHashMap<>();
	public static List<UUID> kick = new ArrayList<>();
	private BlockDestroyHandler handler;
	private PacketListener listener;

	@EventHandler
	public void quit(PlayerQuitEvent e) {
		kick.remove(e.getPlayer().getUniqueId());
	}

	@Override
	public void onEnable() {
		initConfig();

		initProvider();

		if (KICK_PLAYER)
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
		handler = (BlockDestroyHandler) Ref.newInstanceByClass("me.devtec.asyncblockbreak.providers." + Ref.serverVersion());
	}

	private void initConfig() {
		Config config = Config.loadFromPlugin(getClass(), "config.yml", "plugins/AsyncBlockBreak/config.yml").save(DataType.YAML);
		// settings
		SYNC_EVENT = config.getBoolean("settings.syncEvent");
		DISABLE_TILE_DROPS = config.getBoolean("settings.disable_tile_drops");
		// anticheat
		DESTROYED_BLOCKS_LIMIT = config.getInt("anticheat.destroyed_blocks_limit");
		MAXIMUM_RADIUS_WITHIN_BLOCK_AND_PLAYER = config.getDouble("anticheat.max_radius_within_block_and_player");
		// anticheat actions
		KICK_PLAYER = config.getBoolean("anticheat.actions.kick_player");
		BROADCAST_CONSOLE = config.getBoolean("anticheat.actions.broadcast_console");
		BROADCAST_ADMINS = config.getBoolean("anticheat.actions.broadcast_admins");
		TICK_LEAVES = config.getBoolean("settings.tick_leaves");
	}

	@Override
	public void onDisable() {
		if (listener != null)
			listener.unregister();
	}
}
