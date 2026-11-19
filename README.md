# Seasonal Day Cycle / Vertex

Seasonal Day Cycle for **Minecraft 1.20.1 + Fabric + Java 17**.

## Features

- Configurable length of a complete Minecraft day/night cycle, from 1 second to 24 hours.
- Per-world cycle length, stored in the world save and synchronized by the server.
- Day/night proportions scale with the chosen full-day duration.
- Optional Serene Seasons integration (9.1.0+): spring, summer, autumn and winter use their own day/night ratios.
- The season screen reads Serene Seasons' actual season duration and sub-season duration from its API.
- Server-authorized season selection: selecting spring/summer/autumn/winter moves Serene Seasons to the middle sub-season of the chosen season. Serene Seasons then performs its normal synchronized color, weather, snow and ice updates.
- The elapsed day counter follows the actual custom world-time progression. Changing the cycle length changes how quickly the counter advances; it does not use the real-world clock or server TPS.
- Big Globe biomes are processed by Serene Seasons' generic biome hooks. Temperature and precipitation still follow Serene Seasons' normal rules, including its tropical/blacklist and biome-temperature conditions.

## Commands

- `/daycycle gui` — open the cycle and seasonal calendar panel.
- `/daycycle info` — show the current season, elapsed world days, day/night durations and time until the next phase.

## Quick cycle presets

1 second, 20 minutes, 30 minutes, 1 hour, 2 hours, 3 hours, 6 hours and 24 hours. The slider can select intermediate values.

## Permissions

Singleplayer worlds can edit settings directly. On dedicated servers, setting the day-cycle length and manually changing the season requires permission level 2 (OP).

## Serene Seasons behavior

Serene Seasons is optional. Without it, the regular day-cycle controls continue to work, and the seasonal page reports that Serene Seasons is unavailable.

A selected season is not implemented by forcibly deleting snow or ice. SeasonalDayCycle changes Serene Seasons' saved season state and invokes Serene Seasons' normal update path; its own melting and temperature logic then handles the transition.
