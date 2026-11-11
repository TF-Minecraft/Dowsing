package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.managers.NodeManager;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.inactivity.InactivityService;

class ActiveMembersTest {
    private MockedStatic<InactivityService> inactivity;
    private Boolean previousExtra;
    private Integer previousPer;
    private int previousMax;
    private Map<String, Integer> previousBought;

    @BeforeEach void setUp() {
        inactivity = mockStatic(InactivityService.class);
        inactivity.when(() -> InactivityService.isMemberInactive(anyString()))
                .thenAnswer(call -> call.getArgument(0, String.class).startsWith("away"));
        previousExtra = Cache.extraCapacity; previousPer = Cache.membersPerCapacity; previousMax = Cache.maxMemberCapacity;
        previousBought = new HashMap<>(NodeManager.extraCapacityByGuild); NodeManager.extraCapacityByGuild.clear();
    }

    @AfterEach void tearDown() {
        inactivity.close();
        Cache.extraCapacity = previousExtra; Cache.membersPerCapacity = previousPer; Cache.maxMemberCapacity = previousMax;
        NodeManager.extraCapacityByGuild.clear(); NodeManager.extraCapacityByGuild.putAll(previousBought);
    }

    private static Guild guild(String... members) {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("guild");
        when(guild.getMembers()).thenReturn(new ArrayList<>(List.of(members)));
        return guild;
    }

    @Test
    void inactiveMembersAreLeftOutOfTheCount() {
        assertEquals(2, ActiveMembers.count(guild("ann", "away1", "bob", "away2")));
        assertEquals(0, ActiveMembers.count(guild("away1")));
        assertEquals(0, ActiveMembers.count(null));
        Guild noRoster = mock(Guild.class);
        assertEquals(0, ActiveMembers.count(noRoster));
    }

    @Test
    void aMemberWhoseInactivityCannotBeReadStillCounts() {
        inactivity.when(() -> InactivityService.isMemberInactive("broken")).thenThrow(new IllegalStateException("unreadable"));
        inactivity.when(() -> InactivityService.isMemberInactive("old")).thenThrow(new NoSuchMethodError("older SimpleFactions"));
        assertEquals(3, ActiveMembers.count(guild("broken", "old", "ann", "away1")));
    }

    @Test
    void nodeCapacityGrowsOnlyWithActiveMembers() {
        Cache.extraCapacity = true; Cache.membersPerCapacity = 2; Cache.maxMemberCapacity = 10;
        assertEquals(2, NodeManager.getNodeCapacity(guild("ann", "bob", "away1", "away2", "away3")));
        assertEquals(3, NodeManager.getNodeCapacity(guild("ann", "bob", "cid", "dan", "away1")));
    }

    @Test
    void nodesAlreadyHeldAreKeptOnEveryMember() {
        Cache.extraCapacity = true; Cache.membersPerCapacity = 2; Cache.maxMemberCapacity = 10;
        assertEquals(3, NodeManager.getHeldNodeCapacity(guild("ann", "bob", "away1", "away2", "away3")));
        assertEquals(1, NodeManager.getHeldNodeCapacity(null));
        assertEquals(1, NodeManager.getHeldNodeCapacity(mock(Guild.class)));
    }
}
