package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

class LastCycleResultTest {

	@Test
	void roundTripsExtractedItems() {
		Map<String, Integer> extracted = new LinkedHashMap<>();
		extracted.put("m.materials.niter", 1);
		extracted.put("m.materials.arcane_crystal", 3);

		JSONObject stored = LastCycleResult.toJson(extracted);
		Map<String, Integer> loaded = LastCycleResult.fromJson(stored);

		assertEquals(extracted, loaded);
	}

	@SuppressWarnings("unchecked")
	@Test
	void readsNumericAmountsWrittenByTheJsonParser() {
		JSONObject stored = new JSONObject();
		stored.put("m.materials.niter", 1L);
		stored.put("m.materials.arcane_crystal", 3.0d);

		Map<String, Integer> loaded = LastCycleResult.fromJson(stored);

		assertEquals(1, loaded.get("m.materials.niter"));
		assertEquals(3, loaded.get("m.materials.arcane_crystal"));
	}

	@Test
	void missingOrEmptyResultsStayEmpty() {
		assertTrue(LastCycleResult.fromJson(null).isEmpty());
		assertTrue(LastCycleResult.toJson(Map.of()).isEmpty());
		assertTrue(LastCycleResult.toJson(null).isEmpty());
	}
}
