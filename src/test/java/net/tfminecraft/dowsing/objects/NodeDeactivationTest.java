package net.tfminecraft.dowsing.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.tfminecraft.dowsing.Cache;

class NodeDeactivationTest {

	private Integer previousCycleLength;

	@BeforeEach
	void setCycleLength() {
		previousCycleLength = Cache.cycleLength;
		Cache.cycleLength = 60;
	}

	@AfterEach
	void restoreCycleLength() {
		Cache.cycleLength = previousCycleLength;
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void failedRefundStopsAndKeepsPendingInputsForRetry(int cycleTime) {
		RefundNode node = new RefundNode(3, 0);
		node.setCycleTime(cycleTime);

		node.deActivate();

		assertFalse(node.getIsActive());
		assertEquals(1, node.attempts);
		assertEquals(3, node.getInputCounter());
		assertTrue(node.hasPendingRefund());

		node.successfulRefundsRemaining = 3;
		node.deActivate();

		assertEquals(4, node.attempts);
		assertEquals(0, node.getInputCounter());
		assertFalse(node.hasPendingRefund());
	}

	@Test
	void activationRetriesPendingRefundWithoutStartingANewCycle() {
		RefundNode node = new RefundNode(3, 0);
		node.deActivate();
		node.successfulRefundsRemaining = 3;

		node.activate();

		assertEquals(0, node.getInputCounter());
		assertFalse(node.getIsActive());
		assertFalse(node.hasPendingRefund());
	}

	@Test
	void onlyInterruptedInactiveCyclesHavePendingRefunds() {
		RefundNode node = new RefundNode(3, 0);
		assertFalse(node.hasPendingRefund());
		node.isActive = false;
		assertTrue(node.hasPendingRefund());
		node.setCycleTime(0);
		assertTrue(node.hasPendingRefund());
		node.setCycleTime(-1);
		assertFalse(node.hasPendingRefund());
		node.setCycleTime(Cache.cycleLength);
		assertFalse(node.hasPendingRefund());
	}

	@Test
	void partialRefundStopsAtFirstFailure() {
		RefundNode node = new RefundNode(3, 1);

		node.deActivate();

		assertFalse(node.getIsActive());
		assertEquals(2, node.attempts);
		assertEquals(2, node.getInputCounter());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void successfulRefundsDrainPendingInputs(int cycleTime) {
		RefundNode node = new RefundNode(3, 3);
		node.setCycleTime(cycleTime);

		node.deActivate();

		assertFalse(node.getIsActive());
		assertEquals(3, node.attempts);
		assertEquals(0, node.getInputCounter());
	}

	@Test
	void noPendingInputsSkipsRefunds() {
		RefundNode node = new RefundNode(0, 0);

		node.deActivate();

		assertFalse(node.getIsActive());
		assertEquals(0, node.attempts);
	}

	private static final class RefundNode extends Node {
		private int attempts;
		private int successfulRefundsRemaining;
		private final int maximumAttempts;

		RefundNode(int pendingInputs, int successfulRefunds) {
			super(UUID.randomUUID(), null, null, null, true, 1, 1, null, 10, pendingInputs, 50);
			successfulRefundsRemaining = successfulRefunds;
			maximumAttempts = pendingInputs + 1;
		}

		@Override
		public boolean isClaimable() {
			return false;
		}

		@Override
		public void update() {
			// No world or production configuration is needed for the refund loop.
		}

		@Override
		public void refund() {
			// Fail deterministically on the old loop instead of hanging the test JVM.
			assertTrue(++attempts <= maximumAttempts, "Deactivation kept retrying a failed refund");
			if (successfulRefundsRemaining > 0) {
				successfulRefundsRemaining--;
				setInputCounter(getInputCounter() - 1);
			}
			// Missing barrel/hopper returns without consuming a pending input.
		}
	}
}
