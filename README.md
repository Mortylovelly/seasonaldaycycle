# Seasonal Day Cycle / Vertex

Seasonal Day Cycle for **Minecraft 1.20.1 + Fabric + Java 17**.

## Features

- Configurable duration of a complete day/night cycle, from 1 second to 24 hours.
- New worlds default to a **2-hour** complete day/night cycle.
- When Serene Seasons is installed, new worlds start in **mid-summer** (MID_SUMMER); already initialized worlds keep their saved season.
- Per-world cycle length, stored in the world save and synchronized by the server.
- Seasonal day/night proportions for spring, summer, autumn and winter.
- Optional Serene Seasons integration (9.1.0+): the season page reads the current season and sub-season from its public API.
- Manual selection of spring, summer, autumn or winter through Serene Seasons' normal saved-data/update path.
- Big Globe biomes use Serene Seasons' normal biome hooks; no Big Globe-specific patch is required.
- Continuous client-side sun/moon motion uses the configured duration of the current day or night phase. Integer time packets are used for gradual phase correction rather than noisy per-packet speed estimates.
- Optional in-world calendar HUD: elapsed days, cycle number, current season, days until season change, world time, configured daylight duration and full cycle duration.
- HUD settings include an enable switch, six screen positions and text scaling, saved locally in `config/vertex-hud.json`.
- The floating Vertex panel uses the free pointer owned by Minecraft AI Agent. Press **G** in the world to enable/disable it; Vertex does not register another cursor key or unlock/lock the mouse itself.
- The AI Agent bridge is optional and reflective, so the mod can still load without Minecraft AI Agent installed. The floating Vertex UI requires AI Agent's free cursor to interact.

## Commands

- `/daycycle gui` — open the Vertex utility and seasonal calendar panel.
- `/daycycle info` — show the current season, elapsed world days, day/night durations and time until the next phase.

## Quick cycle presets

1 second, 20 minutes, 30 minutes, 1 hour, 2 hours, 3 hours, 6 hours and 24 hours. The slider can select intermediate values.

## Permissions

Singleplayer worlds can edit settings directly. On dedicated servers, setting the day-cycle length and manually changing the season requires permission level 2 (OP).

## Serene Seasons behavior

Serene Seasons is optional. Without it, the regular day-cycle controls continue to work, and the seasonal page reports that Serene Seasons is unavailable.

A selected season is not implemented by forcibly deleting snow or ice. SeasonalDayCycle changes Serene Seasons' saved season state and invokes its normal update path; its own melting and temperature logic then handles the transition.
