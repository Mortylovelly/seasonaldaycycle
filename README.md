# Seasonal Day Cycle

Fabric port of Seasonal Day Cycle for Minecraft 1.21.1.

The mod extends the vanilla day/night cycle without changing server TPS and ties the day/night duration to the current Serene Seasons sub-season.

## Default durations

- Spring: 30 min day / 30 min night
- Summer: 35 min day / 25 min night
- Autumn: 30 min day / 30 min night
- Winter: 25 min day / 35 min night

## Dependencies

- Minecraft 1.21.1
- Fabric Loader 0.19.3+
- Fabric API 0.116.15+
- Serene Seasons 10.1.0+
- GlitchCore 2.0.0+

## Configuration

On first run the mod creates:

`config/seasonaldaycycle.json`

The values are real ticks at 20 TPS. For example, 36000 ticks = 30 real minutes.

## Command

`/daycycle info`

## Versions

The original Forge 1.20.1 version remains on the `main` branch.
The Fabric 1.21.1 port is on the `fabric-1.21.1` branch.
