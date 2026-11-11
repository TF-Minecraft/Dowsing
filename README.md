# Dowsing

> Turn discovered resources into configurable production nodes for TF-Minecraft factions.

Dowsing connects exploration, faction ownership, and resource production. Players can
use a dowsing item to inspect natural yields in a chunk, then operate configurable
nodes that turn inputs, upgrades, and production choices into server resources.

## Features

- **Discoverable resources** — a configured MMOItems dowsing tool reports the resource
  type and yield assigned to the current chunk.
- **Faction-owned nodes** — guilds can claim, transfer, operate, and remove eligible
  ItemsAdder furniture nodes.
- **Configurable production** — node types, levels, slots, production methods, costs,
  timings, and drops are defined in YAML.
- **Capacity and efficiency** — membership, purchased capacity, natural yield, and
  production choices influence what a guild can run and how efficiently it operates.
- **Faction integration** — active nodes contribute configured prestige and wealth
  modifiers, while node upkeep is exposed to the SimpleFactions ledger.
- **Persistent state** — node ownership, configuration, progress, and purchased guild
  capacity survive server restarts.

Originally created by [Drefvelin](https://github.com/Drefvelin).

## Upgrading

Dowsing writes its YAML files only when they are missing, so a new jar never changes an
existing server's `production_methods.yml`. Apply default changes listed in the release
notes by hand, then run `dowsing reload` or restart.

- **Cooking foods from nodes:** any node drop listed in Cooking's `conversions.yml` must use
  the Cooking food path from that list (prefixed `c.`), not the plain item. Cooking converts
  plain items only when a player picks them up off the ground, and node output that goes
  into the hopper above the node is never picked up. For example, replace
  `add_drop(v.sugar,N)` with `add_drop(c.sweetener(type=sweetener_1;origin=Sugar),N)` and
  `add_drop(ia.tfmc_cooking:salt,N)` with `add_drop(c.salt(type=seasoning_1;origin=Salt),N)`,
  keeping each weight `N`. Each cycle rolls one quality for its food, and different qualities
  don't stack, so a hopper that doesn't drain into a chest fills sooner. Cooking must be
  loaded: without it a `c.` drop counts as unavailable and the node holds its cycle.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Dowsing/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

With Java 21 and the pinned plugin dependencies installed, run `mvn clean verify`.
The suite uses JUnit 5, Mockito, and MockBukkit. The build enforces
100% executable runtime **line coverage** with JaCoCo, without production-class
exclusions. Instruction and branch coverage are reported separately.

Surefire test results are in `target/surefire-reports/`. The HTML coverage report
is `target/site/jacoco/index.html`; the XML report is
`target/site/jacoco/jacoco.xml`. CI uploads the test and coverage reports.
The suite does not validate live furniture interactions, client effects, or the
complete server plugin stack.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and pre-existing
material retain their own licenses.
