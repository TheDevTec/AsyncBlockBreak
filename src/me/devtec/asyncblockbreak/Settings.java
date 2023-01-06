package me.devtec.asyncblockbreak;

public class Settings {

	public class Gameplay {
		public static boolean LADDER_WORKS_AS_VINE;
		public static boolean BREAKING_WITH_SWORD;
	}

	public class Performance {
		public static boolean CHORUS_PLANT;
		public static boolean CONNECTED_BLOCKS;
		public static boolean TICK_LEAVES;
		public static boolean TICK_NEARBY_BLOCKS;
		public static boolean DISABLE_TILE_DROPS;
	}

	public class Plugins {
		public static boolean SYNC_EVENT;
	}

	public class AntiCheat {
		public static boolean ACTION_KICK_PLAYER;
		public static boolean ACTION_BROADCAST_CONSOLE;
		public static boolean ACTION_BROADCAST_ADMINS;
		public static String ANTICHEAT_BROADCAST_PERMISSION = "asyncblockbreak.anticheat";

		public static int DESTROYED_BLOCKS_LIMIT;
		public static int MAXIMUM_RADIUS_WITHIN_BLOCK_AND_PLAYER;
	}
}
