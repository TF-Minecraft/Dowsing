package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.dowsing.loaders.PMLoader;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeSlot;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.dowsing.objects.ProductionMethod;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.path.ArtifactPathParser;
import net.tfminecraft.magic.artifact.path.ArtifactPathSpec;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;

class ArtifactDropNamesTest {
    Locale locale;
    MockedStatic<Bukkit> bukkit;
    @BeforeEach void setUp() { locale = Locale.getDefault(); bukkit = mockStatic(Bukkit.class); }
    @AfterEach void tearDown() { bukkit.close(); Locale.setDefault(locale); }

    @Test
    void pathDetectionAndFallbackLabelsWorkWithoutTheMagicPlugin() {
        assertFalse(ArtifactDropNames.isMagicPath(null)); assertFalse(ArtifactDropNames.isMagicPath("v.STONE"));
        assertTrue(ArtifactDropNames.isMagicPath("MAGIC.artifact")); assertNull(ArtifactDropNames.label("v.STONE"));
        Map<String, String> examples = Map.ofEntries(
                Map.entry("magic.", "Random Artifact"), Map.entry("magic.artifact", "Random Artifact"),
                Map.entry("magic.invalid", "Random Artifact"), Map.entry("magic.(", "Random Artifact"),
                Map.entry("magic.()", "Random Artifact"),
                Map.entry("magic.(rarity=rare;primary=fire)", "Rare Fire Artifact"),
                Map.entry("magic.(rarity=very_rare)", "Random Very Rare Artifact"),
                Map.entry("magic.(element=water)", "Random Water Artifact"),
                Map.entry("magic.( earth ; ignored=value ;rarity= ;element= ;rarity)", "Random Earth Artifact"),
                Map.entry("magic.(rarity= ;primary= ;unknown=value)", "Random Artifact"));
        examples.forEach((path, expected) -> assertEquals(expected, ArtifactDropNames.label(path), path));
        PluginManager manager = mock(PluginManager.class); bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        assertEquals("Random Artifact", ArtifactDropNames.label("magic.artifact"));
    }

    @Test
    void uppercaseMagicPathsAreIndependentOfTheServersLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertTrue(ArtifactDropNames.isMagicPath("MAGIC.artifact"));
        assertEquals("Random Artifact", ArtifactDropNames.label("MAGIC.artifact"));
    }

    @Test
    void installedMagicUsesItsRegistryNamesAndUnavailableApiFallsBackSafely() {
        PluginManager manager = mock(PluginManager.class); Plugin magic = mock(Plugin.class);
        when(manager.getPlugin("Magic")).thenReturn(magic); bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        try (var parser = mockStatic(ArtifactPathParser.class)) {
            parser.when(() -> ArtifactPathParser.parse("magic.artifact")).thenReturn(new ArtifactPathSpec(null, null, null));
            assertEquals("Random Artifact", ArtifactDropNames.label("magic.artifact"));
            parser.when(() -> ArtifactPathParser.parse("magic.(rarity=rare;primary=fire)")).thenThrow(new NoClassDefFoundError("Magic unavailable"));
            assertEquals("Rare Fire Artifact", ArtifactDropNames.label("magic.(rarity=rare;primary=fire)"));
        }
    }

    @Test
    void magicLabelsHandleInvalidRandomAndUnnamedSpecifications() {
        try (var parser = mockStatic(ArtifactPathParser.class)) {
            assertEquals("Random Artifact", ArtifactDropNamesMagic.label("invalid"));
            parser.when(() -> ArtifactPathParser.parse("random")).thenReturn(new ArtifactPathSpec(null, null, null));
            assertEquals("Random Artifact", ArtifactDropNamesMagic.label("random"));
            parser.when(() -> ArtifactPathParser.parse("extras")).thenReturn(new ArtifactPathSpec(null, null, new LinkedHashMap<>(Map.of("earth", 2.0))));
            assertEquals("Random Artifact", ArtifactDropNamesMagic.label("extras"));
            parser.when(() -> ArtifactPathParser.parse("blank")).thenReturn(new ArtifactPathSpec(" ", " ", null));
            assertEquals("Random Artifact", ArtifactDropNamesMagic.label("blank"));
        }
    }

    @Test
    void magicRegistryNamesAreCleanedAndResolvedCaseInsensitively() {
        ArtifactRarityDef rarity = rarity("Rare", " §6#ff0000Rare Name ");
        ElementDef element = element("Fire", " §c#abcdefFire Name ");
        ArtifactRarityDef unrelated = rarity("Other", "Other"), unidentified = rarity(null, "Unknown");
        ElementDef otherElement = element("Other", "Other"), unidentifiedElement = element(null, "Unknown");
        try (var parser = mockStatic(ArtifactPathParser.class); var rarities = mockStatic(ArtifactRarityRegistry.class); var elements = mockStatic(ElementRegistry.class)) {
            parser.when(() -> ArtifactPathParser.parse("both")).thenReturn(new ArtifactPathSpec("fire", "rare", null));
            rarities.when(ArtifactRarityRegistry::getAll).thenReturn(List.of(unidentified, unrelated, rarity));
            elements.when(ElementRegistry::getAll).thenReturn(List.of(unidentifiedElement, otherElement, element));
            assertEquals("Rare Name Fire Name Artifact", ArtifactDropNamesMagic.label("both"));
            rarities.when(() -> ArtifactRarityRegistry.getById("rare")).thenReturn(rarity);
            elements.when(() -> ElementRegistry.getById("fire")).thenReturn(element);
            parser.when(() -> ArtifactPathParser.parse("rarity")).thenReturn(new ArtifactPathSpec(null, "rare", null));
            parser.when(() -> ArtifactPathParser.parse("element")).thenReturn(new ArtifactPathSpec("fire", null, null));
            assertEquals("Random Rare Name Artifact", ArtifactDropNamesMagic.label("rarity"));
            assertEquals("Random Fire Name Artifact", ArtifactDropNamesMagic.label("element"));
        }
    }

    @Test
    void missingAndNamelessMagicDefinitionsUseHumanReadableIds() {
        try (var parser = mockStatic(ArtifactPathParser.class); var rarities = mockStatic(ArtifactRarityRegistry.class); var elements = mockStatic(ElementRegistry.class)) {
            parser.when(() -> ArtifactPathParser.parse(anyString())).thenReturn(new ArtifactPathSpec("deep_water", "very_rare", null));
            assertEquals("Very Rare Deep Water Artifact", ArtifactDropNamesMagic.label("missing"));
            ArtifactRarityDef rarity = rarity("very_rare", null); ElementDef element = element("deep_water", " ");
            rarities.when(() -> ArtifactRarityRegistry.getById("very_rare")).thenReturn(rarity);
            elements.when(() -> ElementRegistry.getById("deep_water")).thenReturn(element);
            assertEquals("Very Rare Deep Water Artifact", ArtifactDropNamesMagic.label("blank-names"));
        }
    }

    @Test
    void dropTokensRoundTripNestedMagicPathsAndFractionalWeights() {
        String path = "magic.(rarity=rare;primary=fire)";
        assertEquals(path + "(2)", DropPaths.formatStored(path, 2.0));
        assertEquals(path + "(2.5)", DropPaths.formatStored(path, 2.5));
        assertArrayEquals(new String[]{path, "2.5"}, DropPaths.parseStored("  " + path + "(2.5)  "));
        assertArrayEquals(new String[]{path, "2.5"}, DropPaths.parseAddDropEffect(" add_drop(" + path + ", 2.5) "));
        assertEquals("-2", DropPaths.formatWeight(-2)); assertEquals("0.5", DropPaths.formatWeight(0.5));
    }

    @Test
    void malformedDropTokensAreRejectedWithoutThrowing() {
        assertNull(DropPaths.parseStored(null)); assertNull(DropPaths.parseAddDropEffect(null));
        for (String token : List.of("", "bad", "bad(", "(2)", "v.STONE()", "bad(1")) assertNull(DropPaths.parseStored(token), token);
        for (String token : List.of("", "bad", "bad(", "add_drop(v.STONE)", "add_drop(,2)", "add_drop(v.STONE,)", "add_drop(v.STONE,2")) assertNull(DropPaths.parseAddDropEffect(token), token);
    }

    @Test
    void nodeReloadRestoresKnownSlotChoicesWithoutTouchingNewSlots() {
        Node node = mock(Node.class); NodeType type = mock(NodeType.class); NodeSlot fuel = mock(NodeSlot.class), added = mock(NodeSlot.class);
        ProductionMethod original = mock(ProductionMethod.class), replacement = mock(ProductionMethod.class);
        when(node.getCurrentType()).thenReturn(type); when(type.getSlots()).thenReturn(List.of(fuel));
        when(fuel.getId()).thenReturn("fuel"); when(fuel.getActivePm()).thenReturn(original); when(original.getId()).thenReturn("advanced");
        NodeReloader reloader = new NodeReloader(); reloader.cache(node);
        when(added.getId()).thenReturn("new"); when(type.getSlots()).thenReturn(List.of(fuel, added));
        try (var methods = mockStatic(PMLoader.class)) {
            methods.when(() -> PMLoader.getByString("advanced")).thenReturn(replacement);
            reloader.reload(node); verify(fuel).setActivePm(replacement); verify(added, never()).setActivePm(any());
        }
    }

    private static ArtifactRarityDef rarity(String id, String name) { ArtifactRarityDef rarity = mock(ArtifactRarityDef.class); when(rarity.getId()).thenReturn(id); when(rarity.getName()).thenReturn(name); return rarity; }
    private static ElementDef element(String id, String name) { ElementDef element = mock(ElementDef.class); when(element.getId()).thenReturn(id); when(element.getName()).thenReturn(name); return element; }
}
