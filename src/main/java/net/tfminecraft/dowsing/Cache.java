package net.tfminecraft.dowsing;

public class Cache {
	public static String dowsingStick;
	public static Integer cycleLength;
	public static Boolean extraCapacity;
	public static Integer membersPerCapacity;
	public static Boolean naturalYieldEnabled;
	public static Double refundPercentage;
	
	public static double extraCapacityCost;
	
	public static int maxMemberCapacity;

	public static int minMembersForNode;

	public static double maxEfficiencyPerMember;
	public static double efficiencyGrowthPerMember;

	public static double efficiencyLossType;
	public static double efficiencyLossPM;

	/** Producing nodes' chunks are loaded only around drops and inputs; false keeps them loaded all the time. */
	public static boolean timedNodeChunks = true;
	/** Minutes before a drop that a node's chunk starts loading in the background. */
	public static int nodeChunkLeadMinutes = 1;
	/** Minutes before an input that a node's chunk is loaded, so hoppers can fill the barrel first. */
	public static int nodeChunkInputLeadMinutes = 5;
	/** Minutes a node's chunk stays loaded after a drop or an input, so hoppers can move the output. */
	public static int nodeChunkDrainMinutes = 5;
	/** How often the drain window is extended while the node's hopper still holds items. */
	public static int nodeChunkMaxExtensions = 3;
	/** Chunks kept force-loaded all the time ("world x z" in block coordinates), so a world keeps ticking. */
	public static java.util.List<String> keepActiveChunks = java.util.List.of();
}
