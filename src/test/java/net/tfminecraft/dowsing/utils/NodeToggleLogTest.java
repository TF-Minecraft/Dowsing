package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NodeToggleLogTest {

	@Test
	void durationShowsHoursAndMinutes() {
		assertEquals("0h 00m", NodeToggleLog.duration(0));
		assertEquals("0h 59m", NodeToggleLog.duration(3599));
		assertEquals("23h 30m", NodeToggleLog.duration(23 * 3600 + 30 * 60));
		assertEquals("0h 00m", NodeToggleLog.duration(-5));
	}
}
