package net.tfminecraft.dowsing.testsupport;

import static org.mockito.Mockito.*;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;
import net.tfminecraft.simplefactions.identity.Members;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.extension.*;
import org.mockito.MockedStatic;

/** Existing node tests use a ready faction provider and human-readable member fixture keys. */
public final class FactionFixture implements BeforeEachCallback, BeforeTestExecutionCallback, AfterEachCallback {
    public static MockedStatic<SimpleFactionsApi> api;
    private MockedStatic<Members> members;
    private DowsingMain previous;
    @Override public void beforeEach(ExtensionContext context) {
        previous = DowsingMain.plugin;
        DowsingMain.plugin = mock(DowsingMain.class);
        when(DowsingMain.plugin.nodesReady()).thenReturn(true);
        api = mockStatic(SimpleFactionsApi.class);
        api.when(SimpleFactionsApi::ready).thenReturn(true);
        members = mockStatic(Members.class);
        members.when(() -> Members.display(anyString())).thenAnswer(i -> i.getArgument(0));
        members.when(() -> Members.player(anyString())).thenAnswer(i -> {
            Player player = Bukkit.getPlayerExact(i.getArgument(0, String.class));
            return player == null ? Bukkit.getPlayer(i.getArgument(0, String.class)) : player;
        });
    }
    @Override public void beforeTestExecution(ExtensionContext context) {
        if (DowsingMain.plugin != null && mockingDetails(DowsingMain.plugin).isMock())
            when(DowsingMain.plugin.nodesReady()).thenReturn(true);
    }
    @Override public void afterEach(ExtensionContext context) {
        members.close(); api.close(); DowsingMain.plugin = previous;
    }
}
