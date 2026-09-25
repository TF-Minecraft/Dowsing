package net.tfminecraft.dowsing.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.simplefactions.guild.Guild;

class NodeUpkeepTest {
	private List<Node> previousNodes;
	private Guild guild;

	@BeforeEach
	void setUp() {
		previousNodes = NodeManager.nodes;
		NodeManager.nodes = new ArrayList<>();
		guild = mock(Guild.class);
		when(guild.getId()).thenReturn("The_Chisels");
	}

	@AfterEach
	void tearDown() {
		NodeManager.nodes = previousNodes;
	}

	@Test
	void onlyActiveNodesOweUpkeep() {
		NodeManager.nodes.add(node(guild, true, 40.0, 1.0));
		NodeManager.nodes.add(node(guild, false, 25.0, 1.0));

		assertEquals(40.0, NodeManager.getTotalUpkeep(guild));
	}

	@Test
	void overCapacityMultiplierIsCharged() {
		NodeManager.nodes.add(node(guild, true, 40.0, 1.5));

		assertEquals(60.0, NodeManager.getTotalUpkeep(guild));
	}

	@Test
	void upkeepRoundsHalfUpToTheCent() {
		NodeManager.nodes.add(node(guild, true, 0.29, 1.5));

		assertEquals(0.44, NodeManager.getTotalUpkeep(guild));
	}

	@Test
	void otherGuildsNodesAreIgnored() {
		Guild other = mock(Guild.class);
		when(other.getId()).thenReturn("Other");
		NodeManager.nodes.add(node(other, true, 40.0, 1.0));

		assertEquals(0.0, NodeManager.getTotalUpkeep(guild));
	}

	private static Node node(Guild owner, boolean active, double upkeep, double costIncrease) {
		Node node = new Node(java.util.UUID.randomUUID(), null, null, null, active, 1, 0, null, 10, 0, 50) {
			@Override
			public Boolean hasGuild() {
				return true;
			}

			@Override
			public Guild getGuild() {
				return owner;
			}
		};
		node.setUpkeep(upkeep);
		node.setCostIncrease(costIncrease);
		return node;
	}
}
