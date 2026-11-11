package net.tfminecraft.dowsing.utils;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.inactivity.InactivityService;

/**
 * The guild members who count toward nodes. SimpleFactions marks a member inactive after
 * weeks offline; they stay in the guild but add no node capacity, do not meet the minimum
 * member count and do not raise node efficiency until they log in again.
 */
public final class ActiveMembers {
	private ActiveMembers() {}

	public static int count(Guild guild) {
		if (guild == null || guild.getMembers() == null) return 0;
		int active = 0;
		for (String member : guild.getMembers()) {
			if (!inactive(member)) active++;
		}
		return active;
	}

	/** A member whose inactivity cannot be read counts, as before inactivity was checked. */
	static boolean inactive(String member) {
		try {
			return InactivityService.isMemberInactive(member);
		} catch (LinkageError | RuntimeException unreadable) {
			return false;
		}
	}
}
