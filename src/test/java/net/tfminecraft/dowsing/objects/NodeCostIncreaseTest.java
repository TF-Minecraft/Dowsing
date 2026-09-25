package net.tfminecraft.dowsing.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NodeCostIncreaseTest {

	@Test
	void withinCapacityCostsTheBaseAmount() {
		assertEquals(1.0, Node.costIncreaseFor(1, 1));
		assertEquals(1.0, Node.costIncreaseFor(0, 3));
	}

	@Test
	void eachNodeOverCapacityAddsHalf() {
		assertEquals(1.5, Node.costIncreaseFor(2, 1));
		assertEquals(2.0, Node.costIncreaseFor(3, 1));
	}

	@Test
	void buyingCapacityLowersTheMultiplier() {
		assertEquals(1.5, Node.costIncreaseFor(3, 2));
	}
}
