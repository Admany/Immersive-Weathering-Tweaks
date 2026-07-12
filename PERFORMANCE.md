# Immersive Weathering Tweaks Performance and Memory Report

## Scope and result

IWT replaces Immersive Weathering 2.0.5's heavy `BlockPos` list creation and iteration with reusable packed offset templates and a per-thread `long[]` scratch buffer.

Basically, instead of constantly creating thousands of temporary objects and expecting Java's garbage collector to clean everything up without crying, IWT reuses a tiny amount of already prepared data.

Quantified API V2 prewarms the eight known templates in the background. It helps avoid the first lazy cache build, but it is **not** where the main performance gain comes from. The actual improvement comes from the packed templates and reusable scratch buffers used during gameplay.

Before any performance result was accepted, every supported area shape passed correctness testing:

* Same unshuffled position order
* Same seeded `Random` and Fisher-Yates shuffle order
* No missing positions
* No duplicate positions
* Matching checksums

These results only cover the tested Immersive Weathering code paths. They do not mean every server will magically get 2x TPS or client FPS xd.

## Test environment

| Item                           | Value                                                                         |
| ------------------------------ | ----------------------------------------------------------------------------- |
| CPU                            | 12th Gen Intel Core i5-12500H, 16 logical processors                          |
| Isolated benchmark JVM         | Eclipse Temurin 17.0.16 HotSpot                                               |
| Packaged runtime JVM           | Eclipse Temurin 25.0.3 HotSpot                                                |
| Minecraft / Forge              | 1.20.1 / 47.4.10                                                              |
| Immersive Weathering           | 1.20.1-2.0.5                                                                  |
| Quantified API                 | 2.1.0                                                                         |
| Warm-up / samples / iterations | 2,000 / 9 / 10,000 per sample                                                 |
| Allocation measurement         | `com.sun.management.ThreadMXBean`                                             |
| Retained-memory measurement    | JOL 0.17 object graph                                                         |
| Runtime profile                | 180-second Spark Java-engine profile, random tick speed 1000, one fake player |

The native comparison recreates Immersive Weathering's original behaviour using a list of position objects followed by Java's `Collections.shuffle`.

The fresh-cache test copies a packed template into a new `long[]` every time.

The production test copies the template into a reusable thread-local scratch array and then performs the same Fisher-Yates shuffle.

Nothing was removed or changed just to make the benchmark look better. That would kinda defeat the whole point :DDD

## Largest Immersive Weathering shape `(4,3,4)`

This shape contains 567 positions.

Previous independent runs showed a production scratch speedup between `1.31x` and `1.97x`, so the range should be used instead of pretending one exact result applies to every CPU and JVM setup.

| Metric                                 |               Native IW |   IWT fresh-array cache | IWT production scratch | Improvement vs native |
| -------------------------------------- | ----------------------: | ----------------------: | ---------------------: | --------------------: |
| Runtime for 10,000 iterations          |              197.362 ms |              140.928 ms |             100.363 ms |      49.15% less time |
| Median ns/op                           |                19,736.2 |                14,092.8 |               10,036.3 |          1.97x faster |
| Median operations/s                    |                  50,668 |                  70,958 |                 99,638 |           96.64% more |
| Total allocation for 10,000 operations |              159.200 MB |               45.760 MB |              0.2446 MB |           99.85% less |
| Bytes allocated per operation          |             15,920.00 B |              4,576.00 B |                24.46 B |           99.85% less |
| p95 ns/op                              |                29,449.2 | Not separately measured |               10,670.6 |          63.77% lower |
| p99 ns/op                              | Not separately measured | Not separately measured |               11,149.6 |          Not compared |
| Peak live heap                         |            Not isolated |            Not isolated |           Not isolated |      Not attributable |
| GC collections and pauses              |   Not isolated per path |   Not isolated per path |  Not isolated per path |      Not attributable |

The remaining `24.46 B/op` in the production path comes from the short-lived `Random` object used to reproduce Immersive Weathering's native shuffle behaviour.

So nawh, this is not being advertised as literally zero allocation. It is just extremely close compared with the original path.

## Retained memory versus temporary allocation

JOL measured the full prewarmed cache object graph, including:

* The `ConcurrentHashMap`
* Its internal table
* Eight map nodes
* Eight `Key` records
* Eight `Template` objects
* Eight backing arrays

The full prewarmed cache uses **15,680 B**.

The empty map itself uses 64 B, meaning the actual prewarmed entries account for **15,616 B**.

The raw packed position data contains:

`1,854 longs × 8 B = 14,832 B`

| Memory category                        |               Native IW |                       IWT |
| -------------------------------------- | ----------------------: | ------------------------: |
| Static retained template payload       |                     0 B |                  14,832 B |
| Full retained cache after prewarm      |                     0 B |            15,680 B total |
| Cache above empty-map baseline         |                     0 B |                  15,616 B |
| Map table, nodes, keys and templates   |                     0 B |                     720 B |
| Largest thread-local scratch buffer    | 0 B retained after call | 4,552 B per active thread |
| Scratch for 1 thread                   |                     0 B |                   4,552 B |
| Scratch for 4 threads                  |                     0 B |                  18,208 B |
| Scratch for 8 threads                  |                     0 B |                  36,416 B |
| Scratch for 16 threads                 |                     0 B |                  72,832 B |
| Theoretical 64-template raw maximum    |                     0 B |             512,000,000 B |
| Allocation per largest-shape operation |             15,920.00 B |      24.46 B with scratch |
| Allocation for 10,000 operations       |              159.200 MB |    0.2446 MB with scratch |
| Allocation reduction                   |          Not applicable |                    99.85% |

The scratch array only grows when a larger shape is used. It does not shrink while the thread remains alive, but it becomes collectible once the thread and its `ThreadLocal` are gone.

For Immersive Weathering's eight real shapes, the largest scratch buffer is only **4,552 B per active thread**.

With eight participating threads:

* Scratch buffers: `36,416 B`
* Shared template cache: `15,680 B`
* Total: `52,096 B`

So IWT retains around **52 KB** in that setup to avoid around **159 MB of temporary allocations per 10,000 largest-shape operations**.

Pretty fair trade ngl.

The 64-template limit is only a theoretical safety cap. Stock Immersive Weathering uses eight known shapes and retains only 15,680 B of shared cache data. The 488.28 MiB figure is not observed server usage and should not be read like it is.

## Every supported shape

| Shape     | Positions | Native ns/op | IWT scratch ns/op | Speedup | Native B/op | IWT B/op | Allocation reduction |
| --------- | --------: | -----------: | ----------------: | ------: | ----------: | -------: | -------------------: |
| `(1,1,1)` |        27 |        689.0 |             444.1 |   1.55x |      829.72 |    24.02 |               97.10% |
| `(2,2,2)` |       125 |      2,784.0 |           2,265.3 |   1.23x |    3,544.00 |    24.10 |               99.32% |
| `(2,3,2)` |       175 |      4,315.6 |           2,581.3 |   1.67x |    4,944.00 |    24.14 |               99.51% |
| `(2,4,2)` |       225 |      5,760.5 |           4,042.3 |   1.43x |    6,344.00 |    24.18 |               99.62% |
| `(3,1,3)` |       147 |      4,489.4 |           2,850.3 |   1.58x |    4,160.00 |    24.12 |               99.42% |
| `(3,2,3)` |       245 |      6,835.1 |           4,894.0 |   1.40x |    6,904.00 |    24.20 |               99.65% |
| `(3,3,3)` |       343 |     10,730.4 |           6,199.8 |   1.73x |    9,648.00 |    24.28 |               99.75% |
| `(4,3,4)` |       567 |     19,736.2 |          10,036.3 |   1.97x |   15,920.00 |    24.46 |               99.85% |

Checksums in shape order:

```text
2628114679096906730
-7767868282347192950
1431857895558847644
6003504621440015458
3840029538223826300
1253176313319962462
-7986590848726634078
7102284551939754046
```

All checksums matched between the native and optimized implementations.

## Cold, warm and Quantified API-prewarmed states

| State or measurement                |                                                Result |
| ----------------------------------- | ----------------------------------------------------: |
| Cold first use of largest shape     |           134.600 µs and 9,208 B temporary allocation |
| Warm known-shape cache lookup       | 32.12 ns per lookup and 0 B/op across 1,000,000 calls |
| Quantified API-prewarmed path       |                             Same warm production path |
| Template build time                 |                 Included in the cold first-use result |
| Quantified API submission overhead  |                               Not separately measured |
| Quantified API dispatch delay       |                               Not separately measured |
| Quantified API temporary allocation |                               Not separately measured |
| Retained data after prewarm         |                               Same 15,680 B IWT cache |

The Quantified API task uses one parallel unit because it is one cache-prewarm task. It is not pretending that eight templates magically mean eight fully parallel jobs xd.

Quantified API mainly moves the first template construction away from the server thread.

The actual hot-path improvement comes from:

* Reusing packed templates
* Avoiding `BlockPos` object lists
* Reusing per-thread scratch arrays
* Avoiding repeated large allocations

## Packaged Forge runtime profile

Two 180-second Spark profiles used the same setup:

* Forge 47.4.10
* Immersive Weathering 2.0.5
* Quantified API 2.1.0
* Random tick speed 1000
* One fake player

The baseline server did not have IWT installed.

The comparison server used the final packaged IWT jar.

These are whole-server results, not isolated IWT-only measurements. The worlds also had slightly different entity counts, with 25 entities in the baseline and 21 with IWT, so the results should be treated as directional evidence.

| Runtime metric         | Native IW server |      IWT server |                Difference |
| ---------------------- | ---------------: | --------------: | ------------------------: |
| Profile duration       |        180.478 s |       180.196 s |                   Similar |
| Ticks completed        |            1,973 |           2,073 |                +100 ticks |
| Mean MSPT              |           90.949 |          86.252 |               5.16% lower |
| Median MSPT            |           88.902 |          85.891 |               3.39% lower |
| p95 MSPT               |          117.509 |         107.184 |               8.79% lower |
| Actual TPS             |           10.991 |          11.504 |              4.67% higher |
| Heap used at capture   |  1,275,385,264 B | 1,076,218,712 B |        Lower sampled heap |
| Heap committed         |  1,426,063,360 B | 2,522,873,856 B | Higher committed capacity |
| G1 young collections   |              240 |             404 |          More collections |
| Average young-GC pause |         8.371 ms |        5.089 ms |            Shorter pauses |
| Process RSS            |     Not captured |    Not captured |             Not available |

The heap values are only snapshots captured by Spark. They are not peak heap, settled heap, retained IWT memory, or proof that IWT alone saved roughly 200 MB.

Both tests completed the full observation period, but a longer same-world JFR test would be needed before making strong claims about long-term heap stability or total server RAM savings.

Java heap and process RAM are also not the same thing, so they are intentionally kept separate.

## Verification

The final Forge jar:

* Started successfully
* Loaded alongside Immersive Weathering and Quantified API
* Applied its mixins without errors
* Completed the stress profile
* Preserved native area ordering and shuffle behaviour
* Passed template, cache, concurrency and boundary tests

Fabric compiles and remaps successfully, but its runtime result is not being claimed yet because the available Fabric harness failed during Quantified API Vulkan-device initialization before IWT itself could be properly exercised.

Benchmark sources:

* `IwtAreaTemplatesTest.java`
  Tests equivalence, ordering, shuffle behaviour, boundaries, cache limits and concurrency.

* `IwtPerformanceReport.java`
  Produces the eight-shape CPU and allocation results.

* `IwtMemoryReport.java`
  Measures the retained object graph using JOL.

## Final result

For the exact Immersive Weathering area path tested here, IWT reduced largest-shape temporary allocation by **99.85%** and lowered median execution time by **49.15%** in the current run.

Across independent runs, the measured speedup ranged from **1.31x to 1.97x**.

That is a real hot-path improvement, but it ain't a promise that every random modpack suddenly gets double TPS or FPS.

Basically:

> IWT keeps the same Immersive Weathering behaviour, does the same checks in the same order, and gets rid of almost all the pointless temporary garbage created while doing it.

Java GC can finally breathe a little xd.
