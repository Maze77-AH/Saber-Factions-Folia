# RealFactions

![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-brightgreen)
![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%20%7C%20Folia-blue)
![License](https://img.shields.io/badge/license-GNU%20GPL%20v3.0-brightgreen)
![Status](https://img.shields.io/badge/status-active%20development-orange)
![Performance](https://img.shields.io/badge/focus-performance%20%26%20scalability-purple)

RealFactions is a modernized, performance-focused continuation of the classic factions experience built for modern Minecraft servers.

Originally based on SaberFactions, RealFactions is being actively refactored and upgraded for modern Paper/Purpur/Folia environments with a focus on scalability, maintainability, and long-term production stability.

The project prioritizes:

- Modern Minecraft compatibility
- Folia-aware scheduling and threading
- Performance under high player counts
- Stability during raids and large faction activity
- Clean architecture and maintainable internals
- Compatibility with modern plugin ecosystems

---

# Features

## Core Factions Gameplay
- Land claiming
- Power system
- Raiding and territory control
- Faction roles and permissions
- Ally / truce / enemy relationships
- Faction homes
- Wilderness and safezone support

---

## Advanced Systems
- Grace periods
- Faction missions
- Faction points currency
- Upgrades system
- Audit logging
- Wall/buffer check systems
- Command cooldowns
- Anti-spam protections
- Alt support systems
- Faction reserves
- Internal FTOP support

---

## Modern Platform Support
RealFactions is actively being adapted for:

- Paper
- Purpur
- Folia

### Folia Support Status
Folia support is currently considered:

```text
Experimental / Staging Validation
```

The project includes:
- Folia-aware schedulers
- Region-thread-safe task routing
- Runtime validation diagnostics
- Strict-mode protections for unsafe integrations

Production readiness testing is ongoing.

---

# Performance Philosophy

RealFactions is designed with large servers in mind.

Key goals include:
- Reduced synchronous main-thread work
- Safer async handling
- Cleaner scheduler abstractions
- Lower tick impact during large raids
- Modern API usage
- Removal of legacy compatibility overhead where appropriate

---

# Compatibility

## Supported Server Software
- Paper
- Purpur
- Folia

## Supported Minecraft Versions
Modern Minecraft only.

Legacy 1.8 compatibility is not a project priority.

---

# Integrations

## Supported Integrations
- Vault
- EssentialsX
- DiscordSRV
- PlaceholderAPI
- TAB
- CoreX
- HamsterAPI
- ProtocolLib
- PacketEvents

Additional integrations may continue to evolve.

---

# Building

## Maven

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.Imagicast-Official</groupId>
    <artifactId>RealFactions</artifactId>
    <version>1.6.x-SNAPSHOT</version>
</dependency>
```

---

# Development

## Current Focus Areas
- Folia runtime stability
- Scheduler modernization
- Runtime diagnostics
- XSeries/XMaterial removal
- Async safety validation
- Internal architecture cleanup
- Performance profiling

---

# Runtime Diagnostics

For Folia staging validation:

```yaml
realfactions:
  validation-diagnostics: true
  folia-strict-mode: true
```

Diagnostics help identify:
- Unsafe scheduler usage
- Region-thread violations
- Unsafe economy integrations
- Runtime mutation issues

---

# Contributing

Contributions, testing reports, profiling results, and issue reports are welcome.

When reporting issues, include:
- Minecraft version
- Server software/version
- Full stack traces
- Reproduction steps
- Plugin list if relevant

---

# Support

Community support and development discussion are handled through Discord and GitHub Issues.

Please include:
- Server version
- RealFactions version
- Reproduction details
- Relevant logs

---

# Credits

RealFactions is built upon years of work from the factions community and previous SaberFactions contributors.

This project continues that foundation while modernizing the codebase for current and future Minecraft server platforms.

---

# License

RealFactions is licensed under the GNU GPL v3 License.

See `LICENSE` for details.
