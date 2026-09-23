package net.tfminecraft.dowsing.utils;

import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.path.ArtifactPathParser;
import net.tfminecraft.magic.artifact.path.ArtifactPathSpec;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;

@SuppressWarnings("deprecation") // Magic artifact labels are normalized from legacy formatted text.
final class ArtifactDropNamesMagic {

	private ArtifactDropNamesMagic() {}

	static String label(String path) {
		ArtifactPathSpec spec = ArtifactPathParser.parse(path);
		if (spec == null || spec.isFullyRandom()) {
			return "Random Artifact";
		}
		String rarityName = rarityName(spec.getRarityId());
		String elementName = elementName(spec.getPrimaryId());
		if (rarityName != null && elementName != null) {
			return rarityName + " " + elementName + " Artifact";
		}
		if (rarityName != null) {
			return "Random " + rarityName + " Artifact";
		}
		if (elementName != null) {
			return "Random " + elementName + " Artifact";
		}
		return "Random Artifact";
	}

	private static String rarityName(String rarityId) {
		if (rarityId == null || rarityId.isBlank()) {
			return null;
		}
		ArtifactRarityDef rarity = ArtifactRarityRegistry.getById(rarityId);
		if (rarity == null) {
			for (ArtifactRarityDef candidate : ArtifactRarityRegistry.getAll()) {
				if (candidate.getId() != null && candidate.getId().equalsIgnoreCase(rarityId)) {
					rarity = candidate;
					break;
				}
			}
		}
		if (rarity != null && rarity.getName() != null && !rarity.getName().isBlank()) {
			return plain(rarity.getName());
		}
		return WordUtils.capitalize(rarityId.replace('_', ' '));
	}

	private static String elementName(String elementId) {
		if (elementId == null || elementId.isBlank()) {
			return null;
		}
		ElementDef element = ElementRegistry.getById(elementId);
		if (element == null) {
			for (ElementDef candidate : ElementRegistry.getAll()) {
				if (candidate.getId() != null && candidate.getId().equalsIgnoreCase(elementId)) {
					element = candidate;
					break;
				}
			}
		}
		if (element != null && element.getName() != null && !element.getName().isBlank()) {
			return plain(element.getName());
		}
		return WordUtils.capitalize(elementId.replace('_', ' '));
	}

	private static String plain(String raw) {
		String cleaned = StringFormatter.clean(raw);
		return ChatColor.stripColor(cleaned).trim();
	}
}
