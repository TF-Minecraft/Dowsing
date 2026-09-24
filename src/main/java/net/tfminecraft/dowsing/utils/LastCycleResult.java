package net.tfminecraft.dowsing.utils;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

public final class LastCycleResult {

	public static final String KEY = "last result";

	private LastCycleResult() {}

	@SuppressWarnings("unchecked")
	public static JSONObject toJson(Map<String, Integer> result) {
		JSONObject object = new JSONObject();
		if(result == null) {
			return object;
		}
		for(Map.Entry<String, Integer> entry : result.entrySet()) {
			if(entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0) {
				continue;
			}
			object.put(entry.getKey(), entry.getValue());
		}
		return object;
	}

	public static Map<String, Integer> fromJson(Object raw) {
		Map<String, Integer> result = new HashMap<>();
		if(!(raw instanceof JSONObject object)) {
			return result;
		}
		for(Object key : object.keySet()) {
			if(key == null) {
				continue;
			}
			int amount = amountOf(object.get(key));
			if(amount > 0) {
				result.put(String.valueOf(key), amount);
			}
		}
		return result;
	}

	static int amountOf(Object value) {
		if(value instanceof Number number) {
			return number.intValue();
		}
		if(value == null) {
			return 0;
		}
		try {
			return (int) Math.round(Double.parseDouble(value.toString()));
		} catch (NumberFormatException ex) {
			return 0;
		}
	}
}
