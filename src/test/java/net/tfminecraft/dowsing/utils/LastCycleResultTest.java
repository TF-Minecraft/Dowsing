package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

class LastCycleResultTest {

    @Test
    @SuppressWarnings("unchecked")
    void invalidEntriesAreDiscardedAndLegacyTextAmountsAreRead() {
        Map<String,Integer> input = new LinkedHashMap<>();
        input.put(null, 1); input.put(" ", 2); input.put("null", null);
        input.put("negative", -1); input.put("zero", 0); input.put("valid", 3);
        assertEquals(Map.of("valid", 3), LastCycleResult.toJson(input));
        JSONObject data = new JSONObject();
        data.put(null, 3); data.put("nil", null); data.put("invalid", "bad");
        data.put("legacy", "2.6"); data.put("negative", "-1");
        assertEquals(Map.of("legacy", 3), LastCycleResult.fromJson(data));
        assertTrue(LastCycleResult.fromJson("unexpected").isEmpty());
    }

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
