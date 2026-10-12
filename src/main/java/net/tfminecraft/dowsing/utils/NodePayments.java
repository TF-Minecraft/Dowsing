package net.tfminecraft.dowsing.utils;

import com.google.gson.Gson;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.Leg;
import net.tfminecraft.denareconomy.api.Result;
import net.tfminecraft.dowsing.managers.NodeManager;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;

/** One durable intent spans a ledger posting and its exact node/capacity state change. */
public final class NodePayments {
    private static final Gson JSON = new Gson();
    private final Path file;
    private final Database database;
    private final Logger logger;
    private boolean blocked;

    public NodePayments(Path directory, Database database, Logger logger) {
        this.file = directory.resolve("node-payment.json");
        this.database = database;
        this.logger = logger;
    }

    public boolean blocked() { return blocked; }

    public boolean change(Node node, Guild guild, String kind, int before, int after, double cost) {
        if (blocked) return false;
        try {
            long cents = BigDecimal.valueOf(cost).setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2).longValueExact();
            Intent intent = new Intent("dowsing:" + UUID.randomUUID(), node.getId(), guild.getId(),
                    guild.getIncarnationId(), kind, before, after, cents, false);
            // A new node must exist on disk before recovery can reference it.
            if (!database.saveNode(node)) throw new IOException("Could not save the node before payment");
            if (!database.saveGuildCapacity()) throw new IOException("Could not save capacity before payment");
            blocked = true;
            write(intent);
            return apply(intent, node, guild);
        } catch (Exception failure) {
            blocked = true;
            logger.log(Level.SEVERE, "Node payment paused; its journal must be recovered before more changes", failure);
            return false;
        }
    }

    /** Called after faction and node loading, before any timer or command can mutate nodes. */
    public boolean recover() {
        try {
            if (!Files.exists(file)) { blocked = false; return true; }
            Intent intent = JSON.fromJson(Files.readString(file), Intent.class);
            if (intent == null) throw new IOException("Empty node payment journal");
            if (intent.done()) { blocked = false; return true; }
            blocked = true;
            Guild guild = FactionManager.getGuildByString(intent.guild());
            Node node = NodeManager.nodes.stream().filter(n -> n.getId().equals(intent.node())).findFirst().orElse(null);
            if (guild == null || !guild.getIncarnationId().equals(intent.incarnation()) || node == null
                    || node.getGuild() == null || !node.getGuild().getIncarnationId().equals(intent.incarnation())) {
                throw new IOException("Node payment owner or node no longer matches; retain journal for staff");
            }
            return apply(intent, node, guild) || !blocked;
        } catch (Exception failure) {
            blocked = true;
            logger.log(Level.SEVERE, "Could not recover node payment; node changes remain paused", failure);
            return false;
        }
    }

    private boolean apply(Intent intent, Node node, Guild guild) throws IOException {
        if (!intent.kind().equals("level") && !intent.kind().equals("capacity"))
            throw new IOException("Unknown node payment kind");
        int current = intent.kind().equals("level") ? node.getLevel() : NodeManager.getExtraCapacity(guild);
        if (current != intent.before() && current != intent.after())
            throw new IOException("Node state differs from saved payment intent");
        Result result = Denars.post(intent.operation(), "dowsing:" + intent.kind(), List.of(
                new Leg(Denars.guild(intent.incarnation()), Math.negateExact(intent.cents())),
                new Leg(Denars.MINT, intent.cents())));
        if (result == Result.REFUSED) {
            finish(intent);
            return false;
        }
        if (result != Result.APPLIED) return false;
        if (intent.kind().equals("level")) {
            node.setLevel(intent.after());
            node.update();
            if (!database.saveNode(node)) throw new IOException("Could not persist paid node level");
        } else {
            NodeManager.extraCapacityByGuild.put(guild.getId(), intent.after());
            if (!database.saveGuildCapacity()) throw new IOException("Could not persist paid capacity");
        }
        finish(intent);
        return true;
    }

    private void finish(Intent intent) throws IOException {
        write(new Intent(intent.operation(), intent.node(), intent.guild(), intent.incarnation(),
                intent.kind(), intent.before(), intent.after(), intent.cents(), true));
        blocked = false;
    }

    private void write(Intent intent) throws IOException { DurableFiles.write(file, JSON.toJson(intent)); }

    private record Intent(String operation, UUID node, String guild, UUID incarnation, String kind,
                          int before, int after, long cents, boolean done) { }
}
