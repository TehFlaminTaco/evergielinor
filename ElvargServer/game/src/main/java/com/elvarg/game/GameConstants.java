package com.elvarg.game;

import com.elvarg.game.definition.PlayerBotDefinition;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.elvarg.game.entity.impl.player.persistence.dynamodb.DynamoDBPlayerPersistence;
import com.elvarg.game.entity.impl.player.persistence.jsonfile.JSONFilePlayerPersistence;
import com.elvarg.game.entity.impl.playerbot.fightstyle.impl.*;
import com.elvarg.game.entity.impl.player.persistence.PlayerPersistence;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.rights.PlayerRights;

import java.util.*;

/**
 * A class containing different attributes which affect the game in different
 * ways.
 *
 * @author Professor Oak
 */
public class GameConstants {

	/**
	 * The name of the game.
	 */
	public static final String NAME = "RspsApp";

	/**
	 * The secure game UID /Unique Identifier/
	 */
	public static final int CLIENT_UID = 8784521;

	/**
	 * The directory of the definition files.
	 */
	public static final String DEFINITIONS_DIRECTORY = "../data/definitions/";

	/**
	 * The directory of the clipping files.
	 */
	public static final String CLIPPING_DIRECTORY = "../data/clipping/";

	/**
	 * The method used to save/load players.
	 *
	 * Currently supports DynamoDBPlayerPersistence or JSONFilePlayerPersistence
	 */
	public static final PlayerPersistence PLAYER_PERSISTENCE = new JSONFilePlayerPersistence(); // new DynamoDBPlayerPersistence();

	/**
	 * The flag that determines if processing should be parallelized, improving the
	 * performance of the server times {@code n} (where
	 * {@code n = Runtime.getRuntime().availableProcessors()}) at the cost of
	 * substantially more CPU usage.
	 */
	public static final boolean CONCURRENCY = (Runtime.getRuntime().availableProcessors() > 1);

	/**
	 * The game engine cycle rate in milliseconds.
	 */
	public static final int GAME_ENGINE_PROCESSING_CYCLE_RATE = 600;

	/**
	 * The maximum amount of iterations for a queue/list that should occur each
	 * cycle.
	 */
	public static final int QUEUED_LOOP_THRESHOLD = 45;

	/**
	 * The default position, where players will spawn upon logging in for the first
	 * time.
	 */
	public static final Location DEFAULT_LOCATION = new Location(3089, 3524);

	/**
	 * Should the inventory be refreshed immediately on switching items or should it
	 * be delayed until next game cycle?
	 */
	public static final boolean QUEUE_SWITCHING_REFRESH = true;

	/**
	 * The maximum amount of drops that can be rolled from the dynamic drop table.
	 */
	public static final int DROP_THRESHOLD = 2;

	/**
	 * Multiplies the experience gained.
	 */
	/**
	 * Default combat XP multiplier. The live value is
	 * {@code WorldConfig.get().combatSkillsXpMultiplier}; this remains as the
	 * fallback used when no world configuration has been loaded.
	 */
	public static final double COMBAT_SKILLS_EXP_MULTIPLIER = 6;
	/** Default non-combat XP multiplier; see {@link #COMBAT_SKILLS_EXP_MULTIPLIER}. */
	public static final double REGULAR_SKILLS_EXP_MULTIPLIER = 18;

	/**
	 * Enabled debugging of attack distance for {@link PlayerRights} DEVELOPER
	 */
	public static final boolean DEBUG_ATTACK_DISTANCE = false;

	/**
	 * The gameframe's tab interface ids.
	 */
	public static final int TAB_INTERFACES[] = { 2423, 3917, 31000, 3213, 1644, 5608, -1, 37128, 5065, 5715, 2449,
			42500, 147, 32000 };

	/**
	 * Removed in EverGielinor along with the spawn tab. Nothing reads this now;
	 * items are obtained by playing for them.
	 */
	// public static final Set<Integer> ALLOWED_SPAWNS = ...


	public static final PlayerBotDefinition[] PLAYER_BOTS = new PlayerBotDefinition[]{
			new PlayerBotDefinition("Bot Hello123", new Location(3085, 3528), new ObbyMaulerFighterPreset()),
			new PlayerBotDefinition("Elvemage", new Location(3093, 3529), new NHPureFighterPreset()),
			new PlayerBotDefinition("Bot 1337Pk", new Location(3087, 3530),  new DDSPureRFighterPreset()),
			new PlayerBotDefinition("Bot Kids Ranqe", new Location(3089, 3530), new GRangerFighterPreset()),
			new PlayerBotDefinition("Bot Josh", new Location(3091, 3533), new DDSPureMFighterPreset()),
			new PlayerBotDefinition("Bot Odablock", new Location(3091, 3536), new TribridMaxFighterPreset()),
			new PlayerBotDefinition("Bot SKillSpecs", new Location(3095, 3535), new MidTribridMaxFighterPreset()),
			new PlayerBotDefinition("Bot F2P Pure", new Location(3096, 3530), new F2PMeleeFighterPreset()),
	};

	// The password for every player bot account
	public static String PLAYER_BOT_PASSWORD = "wirfunerpro4n!1";

	// The list of roles who can "steal" a bot from any player
	public static List<PlayerRights> PLAYER_BOT_OVERRIDE = Arrays.asList(PlayerRights.MODERATOR, PlayerRights.ADMINISTRATOR, PlayerRights.DEVELOPER, PlayerRights.OWNER);
}
