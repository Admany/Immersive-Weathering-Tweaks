# 《▓ Immersive Weathering: Tweaks ▓》

<p align="center">
  <a href="https://www.admany.dev/">
    <img src="https://i.imghippo.com/files/fC4894ILw.png" alt="Powered by Quantified API">
  </a>
</p>

Immersive Weathering: Tweaks, or IWT, is a performance addon for Immersive Weathering that makes its area checks, growth processing, and weathering logic a LOT less heavy.

Immersive Weathering normally creates a ton of temporary position lists and objects while checking nearby blocks. IWT replaces the heavier parts of that system with reusable packed templates and per-thread scratch buffers.

Same checks, same order, same results. Just faster, lighter, and a lot less bullying toward Java's garbage collector xd

## 《▒ What it actually does ▒》

- Speeds up Immersive Weathering area checks
- Reduces temporary memory allocations by up to **99.85%**
- Uses reusable packed position templates instead of rebuilding large lists constantly
- Uses per-thread scratch buffers to avoid useless array allocations
- Preserves native position order, random shuffling, rule checks, and results
- Uses Quantified API to prewarm its known templates safely in the background

Quantified API handles the startup prewarm, while the main speedup comes from reusing the packed templates during gameplay.

## 《▒ Performance ▒》

Largest tested Immersive Weathering area shape, containing 567 positions:

| Metric | Normal IW | With IWT | Gain |
|---|---:|---:|---:|
| Median processing time | 19.736 µs | 10.036 µs | **1.97x faster** |
| Memory allocated per check | 15,920 B | 24.46 B | **99.85% less** |
| Allocation over 10,000 checks | 159.2 MB | 0.2446 MB | **99.85% less** |
| Mean MSPT under load | 90.949 | 86.252 | **5.16% lower** |
| P95 MSPT under load | 117.509 | 107.184 | **8.79% lower** |
| Actual TPS under load | 10.991 | 11.504 | **4.67% higher** |

The isolated area-check benchmarks showed between **1.31x and 1.97x faster processing**, depending on the run.

The TPS and MSPT results come from paired 180-second Forge stress tests, so exact gains will ofc depend on your hardware, modpack, and how much random chaos is happening around the player :DDD

## 《▒ Memory usage ▒》

IWT keeps a tiny amount of reusable data in memory:

- Shared template cache: around **15.7 KB**
- Largest scratch buffer: around **4.55 KB per active thread**
- Cache with eight active scratch buffers: around **52 KB**

So yh, it keeps a few kilobytes around to avoid creating hundreds of megabytes of temporary garbage. Pretty fair trade ngl.

## 《▒ Compatibility ▒》

- Minecraft **1.20.1**
- Immersive Weathering **2.0.5**
- Quantified API **V2.1+**
- Forge
- Fabric

## 《▒ Building ▒》

```bash
./gradlew clean build
````

Release jars are written to `build/libs/`:

- `immersive-weathering-tweaks-forge-1.1.0.jar`
- `immersive-weathering-tweaks-fabric-1.1.0.jar`
- `immersive-weathering-tweaks-1.1.0.jar` (combined omni jar)

Build-time mod dependencies are project-local under `libs/`; the Gradle scripts contain no machine-specific dependency paths.

## 《▒ Licensing and support ▒》

Licensed under **BRSSLA V1.5**, which is free for personal and non-commercial use.

Found a bug, got an idea, or somehow managed to break it? Feel free to open a GitHub issue or even a pull request :]

<p align="center">
  <sub>Developed and Maintained by Admany - BlackRift Studios 2026</sub>
</p>
