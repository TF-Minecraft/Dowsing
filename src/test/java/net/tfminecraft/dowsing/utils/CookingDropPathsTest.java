package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Cooking turns plain sugar into food only on a player's ground pickup. Node output that goes
 * straight into a hopper never gets picked up, so nodes must drop Cooking's food path directly.
 */
class CookingDropPathsTest {
	static final String SUGAR = "c.sweetener(type=sweetener_1;origin=Sugar)";

	@Test
	void cookingFoodPathSurvivesTheAddDropEffectAndTheStoredToken() {
		String[] effect = DropPaths.parseAddDropEffect("add_drop(" + SUGAR + ",40)");
		assertArrayEquals(new String[]{SUGAR, "40"}, effect);

		String stored = DropPaths.formatStored(effect[0], Double.parseDouble(effect[1]));
		assertEquals(SUGAR + "(40)", stored);
		assertArrayEquals(new String[]{SUGAR, "40"}, DropPaths.parseStored(stored));
	}

	@Test
	void bundledPlantationsDropCookingSugarInsteadOfPlainSugar() throws Exception {
		YamlConfiguration config = bundled("production_methods.yml");
		List<String> sugarDrops = new ArrayList<>();
		for (String key : config.getKeys(false)) {
			for (String effect : config.getStringList(key + ".effects")) {
				String[] drop = DropPaths.parseAddDropEffect(effect);
				if (drop == null) continue;
				assertNotEquals("v.sugar", drop[0].toLowerCase(Locale.ROOT), key + " still drops plain sugar");
				if (drop[0].equals(SUGAR)) sugarDrops.add(key);
			}
		}
		assertEquals(3, sugarDrops.size(), sugarDrops.toString());
		assertEquals(Set.of("material_plantation", "spice_plantation", "sweet_spice_plantation"), Set.copyOf(sugarDrops));
	}

	static YamlConfiguration bundled(String name) throws Exception {
		try (InputStream in = CookingDropPathsTest.class.getClassLoader().getResourceAsStream(name)) {
			assertNotNull(in, name);
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}
}
