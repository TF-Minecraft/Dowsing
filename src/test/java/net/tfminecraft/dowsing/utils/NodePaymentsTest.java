package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.tfminecraft.denareconomy.api.*;
import net.tfminecraft.dowsing.managers.NodeManager;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class NodePaymentsTest {
    @TempDir Path directory;
    Database database;
    Guild guild;
    Node node;
    UUID incarnation;
    NodePayments payments;
    AtomicInteger level;
    MockedStatic<Denars> denars;
    MockedStatic<FactionManager> factions;
    List<Node> previousNodes;
    Map<String,Integer> previousCapacity;
    final List<String> operations = new ArrayList<>();
    Result result;
    @BeforeEach void setup() {
        database = mock(Database.class);
        when(database.saveNode(any())).thenReturn(true);
        when(database.saveGuildCapacity()).thenReturn(true);
        guild = mock(Guild.class); incarnation = UUID.randomUUID();
        when(guild.getId()).thenReturn("guild"); when(guild.getIncarnationId()).thenReturn(incarnation);
        node = mock(Node.class); when(node.getId()).thenReturn(UUID.randomUUID()); when(node.getGuild()).thenReturn(guild);
        level = new AtomicInteger(1); when(node.getLevel()).thenAnswer(i -> level.get());
        doAnswer(i -> { level.set(i.getArgument(0)); return null; }).when(node).setLevel(anyInt());
        previousNodes = new ArrayList<>(NodeManager.nodes); NodeManager.nodes.clear(); NodeManager.nodes.add(node);
        previousCapacity = new HashMap<>(NodeManager.extraCapacityByGuild); NodeManager.extraCapacityByGuild.clear();
        denars = mockStatic(Denars.class, CALLS_REAL_METHODS);
        result = Result.APPLIED;
        denars.when(() -> Denars.post(anyString(), anyString(), anyList())).thenAnswer(i -> {
            assertTrue(Files.exists(directory.resolve("node-payment.json")), "intent precedes the posting");
            operations.add(i.getArgument(0)); return result;
        });
        factions = mockStatic(FactionManager.class);
        factions.when(() -> FactionManager.getGuildByString("guild")).thenReturn(guild);
        payments = reopen();
    }
    NodePayments reopen() { return new NodePayments(directory, database, Logger.getAnonymousLogger()); }
    @AfterEach void cleanup() {
        factions.close(); denars.close(); NodeManager.nodes.clear(); NodeManager.nodes.addAll(previousNodes);
        NodeManager.extraCapacityByGuild.clear(); NodeManager.extraCapacityByGuild.putAll(previousCapacity);
    }
    @Test void successfulChangesPersistExactLevelCapacityAndRefund() {
        assertTrue(payments.recover());
        assertTrue(payments.change(node,guild,"level",1,2,12.345));
        List<Leg> expected=List.of(new Leg(Denars.guild(incarnation),-1235), new Leg(Denars.MINT,1235));
        denars.verify(() -> Denars.post(anyString(), eq("dowsing:level"), eq(expected)));
        assertEquals(2,level.get()); assertFalse(payments.blocked());
        assertTrue(reopen().recover()); assertEquals(1,operations.size());
        assertTrue(payments.change(node,guild,"level",2,1,-5.0));
        assertEquals(1,level.get());
        assertTrue(payments.change(node,guild,"capacity",0,1,10));
        assertEquals(1,NodeManager.getExtraCapacity(guild)); verify(database, atLeastOnce()).saveGuildCapacity();
    }
    @Test void refusalLeavesStateWhileUnavailableBlocksNewIntentAndRecoversSameId() {
        result = Result.REFUSED;
        assertFalse(payments.change(node,guild,"level",1,2,10)); assertEquals(1,level.get()); assertFalse(payments.blocked());
        result = Result.UNAVAILABLE;
        assertFalse(payments.change(node,guild,"level",1,2,10)); String operation=operations.getLast();
        assertTrue(payments.blocked()); assertFalse(payments.change(node,guild,"capacity",0,1,20));
        assertEquals(2,operations.size());
        result = Result.APPLIED; payments=reopen(); assertTrue(payments.recover());
        assertEquals(operation,operations.getLast()); assertEquals(2,level.get());
    }
    @Test void crashAfterPostingReappliesTheSavedTargetAndNeverIncrementsAgain() {
        when(database.saveNode(node)).thenReturn(true, false, true);
        assertFalse(payments.change(node,guild,"level",1,2,10)); assertEquals(2,level.get());
        payments=reopen(); assertTrue(payments.recover()); assertEquals(2,level.get());
        assertEquals(operations.getFirst(),operations.getLast());
        assertTrue(reopen().recover()); assertEquals(2,operations.size());
    }
    @Test void failedCapacitySaveIsReplayedWithSamePayment() {
        when(database.saveGuildCapacity()).thenReturn(true, false, true);
        assertFalse(payments.change(node,guild,"capacity",0,1,10));
        assertTrue(reopen().recover()); assertEquals(1,NodeManager.getExtraCapacity(guild));
        assertEquals(operations.getFirst(),operations.getLast());
    }
    @Test void conflictsAndChangedOwnersRemainBlockedForStaff() {
        result=Result.CONFLICT; assertFalse(payments.change(node,guild,"level",1,2,10));
        when(guild.getIncarnationId()).thenReturn(UUID.randomUUID());
        assertFalse(reopen().recover()); assertEquals(1,operations.size());
    }
    @Test void conflictingTargetStateAndInvalidJournalNeverPost() throws Exception {
        result=Result.UNAVAILABLE; assertFalse(payments.change(node,guild,"level",1,2,10));
        level.set(7); assertFalse(reopen().recover()); assertEquals(1,operations.size());
        Files.writeString(directory.resolve("node-payment.json"), "null");
        assertFalse(reopen().recover());
        Files.writeString(directory.resolve("node-payment.json"), "broken json");
        assertFalse(reopen().recover());
    }
    @Test void initialPersistenceAndInvalidAmountsFailBeforePosting() throws Exception {
        when(database.saveNode(node)).thenReturn(false);
        assertFalse(payments.change(node,guild,"level",1,2,10)); assertTrue(operations.isEmpty());
        assertTrue(payments.recover()); when(database.saveNode(node)).thenReturn(true);
        when(database.saveGuildCapacity()).thenReturn(false);
        assertFalse(payments.change(node,guild,"level",1,2,10)); assertTrue(operations.isEmpty());
        assertTrue(payments.recover()); when(database.saveGuildCapacity()).thenReturn(true);
        assertFalse(payments.change(node,guild,"level",1,2,Double.NaN)); assertTrue(operations.isEmpty());
        assertTrue(payments.recover()); Files.createDirectory(directory.resolve("node-payment.json"));
        assertFalse(payments.change(node,guild,"level",1,2,10)); assertTrue(operations.isEmpty());
    }
    @Test void invalidOperationAndRefusedRecoveryAreRetainedOrCompletedSafely() {
        assertFalse(payments.change(node,guild,"wrong",1,2,10)); assertTrue(operations.isEmpty());
    }
}
