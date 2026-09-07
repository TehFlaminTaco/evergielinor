package com.elvarg.game.content.presets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import com.elvarg.game.GameConstants;
import com.elvarg.game.content.PrayerHandler;
import com.elvarg.game.content.PrayerHandler.PrayerData;
import com.elvarg.game.content.combat.CombatFactory;
import com.elvarg.game.content.combat.CombatSpecial;
import com.elvarg.game.content.combat.bountyhunter.BountyHunter;
import com.elvarg.game.content.combat.magic.Autocasting;
import com.elvarg.game.content.skill.SkillManager;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.entity.impl.playerbot.PlayerBot;
import com.elvarg.game.entity.impl.playerbot.fightstyle.impl.F2PMeleeFighterPreset;
import com.elvarg.game.model.Flag;
import com.elvarg.game.model.Item;
import com.elvarg.game.model.MagicSpellbook;
import com.elvarg.game.model.Skill;
import com.elvarg.game.model.areas.impl.WildernessArea;
import com.elvarg.game.model.container.impl.Bank;
import com.elvarg.game.model.rights.PlayerRights;
import com.elvarg.util.Misc;

/**
 * A class for handling {@code Presetable} sets.
 * 
 * Holy, this became messy quickly. Sorry about that.
 * 
 * @author Professor Oak
 */
/**
 * Gear loadouts.
 *
 * This was a player-facing preset system: an interface that re-equipped a player
 * from a saved kit, opened again on death. In a progression world that hands out
 * the result of playing, so the interface, the editor and the death-time re-gear
 * are gone.
 *
 * The loading mechanism survives because the player-bot system uses it to equip
 * its fighters - see PlayerBot and playerbot/interaction/CombatInteraction. It is
 * no longer reachable by a player.
 */
public class Presetables {

	/**
	 * The max amount of premade/custom presets.
	 */
	public static final int MAX_PRESETS = 10;

	/**
	 * The presets interface id.
	 */
	private static final int INTERFACE_ID = 45000;

	/**
	 * Pre-made sets by the server which everyone can use.
	 */
	public static final Presetable[] GLOBAL_PRESETS = new Presetable[] {
			PredefinedPresets.OBBY_MAULER_57,
			PredefinedPresets.G_MAULER_70,
			PredefinedPresets.DDS_PURE_M_73,
			PredefinedPresets.DDS_PURE_R_73,
			PredefinedPresets.NH_PURE_83,
			F2PMeleeFighterPreset.PRESETABLE,
			PredefinedPresets.ATT_70_ZERKER_97,
			PredefinedPresets.MAIN_RUNE_126,
			PredefinedPresets.MAIN_HYBRID_126,
			PredefinedPresets.MAIN_TRIBRID_126,
	};

	
	

	
	/**
	 * Loads a preset.
	 * 
	 * @param player
	 *            The player.
	 * @param preset
	 *            The preset to load.
	 */
	public static void load(Player player, final Presetable preset) {
		final int oldCbLevel = player.getSkillManager().getCombatLevel();

		// Close!
		player.getPacketSender().sendInterfaceRemoval();

		// Check if we can load...
		if (player.getArea() instanceof WildernessArea) {
			if (!(player instanceof PlayerBot) && player.getRights() != PlayerRights.DEVELOPER) {
				player.getPacketSender().sendMessage("You can't load a preset in the wilderness!");
				return;
			}
		}
		if (player.getDueling().inDuel()) {
			player.getPacketSender().sendMessage("You can't load a preset during a duel!");
			return;
		}

		// EverGielinor: presets are no longer a player-facing tool, so the
		// spawnability gate and the bank shuffling that went with it are gone.
		// What remains is the loadout mechanism the player-bot system needs in
		// order to equip its fighters.
		// Add inventory
		Arrays.stream(preset.getInventory()).filter(t -> !Objects.isNull(t) && t.isValid())
				.forEach(t -> player.getInventory().add(t));

		// Set equipment
		Arrays.stream(preset.getEquipment()).filter(t -> !Objects.isNull(t) && t.isValid())
				.forEach(t -> player.getEquipment().setItem(t.getDefinition().getEquipmentType().getSlot(), t.clone()));

		// Set magic spellbook
		player.setSpellbook(preset.getSpellbook());
		Autocasting.setAutocast(player, null);

		// Set levels
		long totalExp = 0;
		for (int i = 0; i < preset.getStats().length; i++) {
			Skill skill = Skill.values()[i];
			int level = preset.getStats()[i];
			int exp = SkillManager.getExperienceForLevel(level);
			player.getSkillManager().setCurrentLevel(skill, level).setMaxLevel(skill, level).setExperience(skill, exp);
			totalExp += exp;
		}

		// Update prayer tab with prayer info
		player.getPacketSender().sendString(687, player.getSkillManager().getCurrentLevel(Skill.PRAYER) + "/"
				+ player.getSkillManager().getMaxLevel(Skill.PRAYER));

		// Send total level
		player.getPacketSender().sendString(31200, "" + player.getSkillManager().getTotalLevel());

		// Send combat level
		final int newCbLevel = player.getSkillManager().getCombatLevel();
		final String combatLevel = "Combat level: " + newCbLevel;
		player.getPacketSender().sendString(19000, combatLevel).sendString(5858, combatLevel);

		if (newCbLevel != oldCbLevel) {
			BountyHunter.unassign(player);
		}

		// Send new spellbook
		player.getPacketSender().sendTabInterface(6, player.getSpellbook().getInterfaceId());
		player.getPacketSender().sendConfig(709, PrayerHandler.canUse(player, PrayerData.PRESERVE, false) ? 1 : 0);
		player.getPacketSender().sendConfig(711, PrayerHandler.canUse(player, PrayerData.RIGOUR, false) ? 1 : 0);
		player.getPacketSender().sendConfig(713, PrayerHandler.canUse(player, PrayerData.AUGURY, false) ? 1 : 0);
		player.resetAttributes();
		player.getPacketSender().sendMessage("Preset loaded!");
		player.getPacketSender().sendTotalExp(totalExp);

		// Restore special attack
		player.setSpecialPercentage(100);
		CombatSpecial.updateBar(player);

		player.getUpdateFlag().flag(Flag.APPEARANCE);
	}

	}
