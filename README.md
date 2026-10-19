# VRP Logistics Optimizer

[![CI](https://github.com/pablojordab/vrp-logistics-optimizer/actions/workflows/ci.yml/badge.svg)](https://github.com/pablojordab/vrp-logistics-optimizer/actions/workflows/ci.yml)
[![Live Demo](https://img.shields.io/badge/Demo-Live%20Visualizer-brightgreen)](https://pablojordab.github.io/vrp-logistics-optimizer/frontend/)
![Java 17](https://img.shields.io/badge/Java-17-orange)
![OR-Tools](https://img.shields.io/badge/Google-OR--Tools%209.10-blue)
![GraphHopper](https://img.shields.io/badge/Routing-GraphHopper%208.0-green)

<p align="center">
  <img src="docs/images/visualizer-preview.png?v=2" alt="VRP Spatial Visualizer Preview" width="100%">
  <br>
  <em>Figure 1: Multi-vehicle CVRP solution with time windows over Monaco's road network (5 active vans, 1,109 stitched road geometry points via GraphHopper, solved with Google OR-Tools).</em>
</p>

A Java 17 backend that solves a single-depot **Capacitated Vehicle Routing Problem (CVRP)** using a *cluster-first, route-second* strategy: shipments are grouped into capacity-feasible zones with a custom greedy heuristic, and each zone's stop order is then optimized with **Google OR-Tools**. Travel times come from a **real road network** (OpenStreetMap data for Monaco, via GraphHopper Core), not straight-line approximations. Output is a GeoJSON file you can drop straight into a map.

This project was built to get hands-on with combinatorial optimization, real routing engines, and JNI-backed native solvers in Java — it's a learning/portfolio project, not a production routing system. See [Known Limitations](#known-limitations--roadmap) below for an honest breakdown of what it does and doesn't do yet.

---

## What it actually does

```
Shipments (random around Monaco, or loaded from a CSV file)
        │
        ▼
AgentEstimatorService ──► how many vans are needed (total demand / van capacity)
        │
        ▼
ClusteringService ──► greedy capacity-constrained clustering (1 zone per van),
                       with each cluster's centroid recentered to the mean of
                       its assigned shipments as they're added
        │
        ▼
 For each zone:
   CostMatrixCalculator ──► pairwise driving-time matrix (GraphHopper queries)
        │
        ▼
   VRPSolverService ──► OR-Tools single-vehicle solve over that zone, with time windows
                        (PATH_CHEAPEST_ARC + Guided Local Search)
        │
        ▼
   Road geometry stitched stop-to-stop (GraphHopper)
        │
        ▼
GeoJsonSerializer ──► route_monaco_fleet.json
```

**Important nuance:** capacity handling and route optimization are split across two different components. `ClusteringService` decides *which shipments go to which van* (a greedy nearest-with-capacity-check heuristic, not part of the OR-Tools model). `VRPSolverService` then solves a **single-vehicle problem with time windows** per zone — OR-Tools is given a `Time` dimension with a `cumulVar` range per stop, so arrivals do respect each shipment's `TimeWindow`, but OR-Tools is not given multiple vehicles or a capacity dimension directly. This is a legitimate and common decomposition of CVRP, but it means the "combinatorial optimization" happens over stop *ordering under time constraints*, while fleet *assignment* is a simpler heuristic.

**Why the van count can exceed the estimate.** `AgentEstimatorService` gives `ClusteringService` a starting number of vans (`k`), but that number only seeds the initial clusters — it isn't a hard cap. If no existing cluster anywhere in the tree has capacity for a shipment, `ClusteringService` opens one more "emergency" cluster rather than overloading a van past its capacity. This is intentional — needing one extra van is a far safer outcome than silently exceeding a van's capacity — and it's logged as a `WARN` whenever it happens, so the gap between the estimate and the actual zone count is always visible, never silent.

### Spatial Quadtree

The repo includes a custom hierarchical `Quadtree<T>` with three query modes: `query(BoundingBox)` for bounding-box range search, `nearest(Coordinates)` for plain nearest-neighbor search, and `nearestFeasible(Coordinates, Predicate)` — the same branch-and-bound search, but skipping any candidate that fails an arbitrary predicate (e.g. "has spare capacity"). All three are backed by real pruning: `nearest`/`nearestFeasible` compute the true minimum possible distance from the target to each quadrant's boundary and skip the quadrant entirely if it can't beat the best match found so far, rather than just checking box containment.

**`ClusteringService` uses `nearestFeasible` directly**, as the entire implementation of "find the closest cluster with spare capacity for this shipment." This replaced an earlier hand-written version that grew a bounding box around the shipment, querying it with `query(BoundingBox)`, until it found *a* feasible cluster inside it — which has a real correctness gap: a feasible cluster can sit just outside a given box while a farther (but still feasible) one sits inside it, near a corner, so the box can return a feasible match that isn't the *nearest* one. `nearestFeasible` doesn't have that gap, since its pruning is based on true distance to each quadrant's boundary, not box containment — it always returns the exact nearest feasible cluster, or `null` if none exists anywhere in the tree. This is covered by `QuadtreeTest` (see [Testing & CI](#testing--ci)).

`query(BoundingBox)` and `nearest(Coordinates)` are implemented and tested but have no caller in the pipeline today — `query` is only exercised by `QuadtreeBenchmark`, and both are building blocks for a planned feature (pruning candidate pairs before cost-matrix computation, see [Known Limitations](#known-limitations--roadmap)).

`QuadtreeBenchmark` compares `query(BoundingBox)` against a linear scan over 10,000 points, using a shared query set for both methods, a JIT warmup phase, multiple timed trials (mean ± stddev), and a correctness check that aborts the benchmark if the two methods disagree before any timing is trusted. It is a hand-rolled microbenchmark, not JMH — an earlier version of this README overstated it as "JMH-style." Run it yourself with `mvn compile exec:java "-Dexec.mainClass=service.QuadtreeBenchmark"`; it measures roughly **20–25x faster** than linear scan on a 10,000-point index, though the exact figure depends on the machine it runs on.

---

## Tech Stack

* **Core language:** Java 17
* **Optimization:** Google OR-Tools 9.10 (native C++ bindings via JNI) — `PATH_CHEAPEST_ARC` first-solution strategy + `GUIDED_LOCAL_SEARCH` metaheuristic, 2s time limit per zone
* **Routing / geospatial:** GraphHopper Core 8.0, importing a real `.osm.pbf` extract (Monaco)
* **Spatial indexing:** custom 2D Quadtree — `nearestFeasible()` wired into `ClusteringService` for capacity-aware nearest-cluster lookup; `query()` and `nearest()` are implemented and tested but have no caller in the pipeline today (see above). `query()` benchmarks ~20–25x faster than linear scan on a 10,000-point index (see [Spatial Quadtree](#spatial-quadtree))
* **Build:** Apache Maven
* **Visualization:** static HTML + Leaflet.js frontend that renders the generated GeoJSON

---

## Repository Layout

```text
├── .github/
│   └── workflows/
│       └── ci.yml                      # GitHub Actions: mvn test on every push/PR
├── frontend/
│   └── index.html                     # Leaflet.js GeoJSON route viewer
├── sample-data/
│   └── shipments-example.csv          # Example CSV for custom demand (see Getting Started)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── domain/                # Coordinates, Shipment, Vehicle, TimeWindow, etc.
│   │   │   ├── main/Main.java         # Orchestrates the pipeline end to end
│   │   │   ├── routing/               # GraphHopperManager (road network wrapper)
│   │   │   ├── service/               # Clustering, cost matrix, VRP solver, GeoJSON export
│   │   │   └── spatial/               # Quadtree + QuadNode (see note above)
│   │   └── resources/
│   │       └── monaco-latest.osm.pbf  # Real OpenStreetMap extract
│   └── test/java/                     # Unit + integration tests (GraphHopper, clustering,
│                                       # quadtree, solver, cost matrix, full pipeline — see
│                                       # Testing & CI for what is and isn't covered)
├── pom.xml
└── README.md
```

---

## Getting Started

### Prerequisites
* JDK 17+
* Apache Maven 3.8+

### 1. Build
```bash
mvn clean compile
```

### 2. Run the pipeline
```bash
mvn exec:java -Dexec.mainClass="main.Main"
```
On first run, GraphHopper parses `monaco-latest.osm.pbf` and builds a local graph cache under `graphhopper-cache/` (git-ignored, and slow the first time — subsequent runs reuse it). The pipeline then generates 30 random shipments around Monaco, estimates fleet size, clusters, solves each zone, and writes `route_monaco_fleet.json`.

**Using your own shipments instead of random demand:** pass a CSV file as the first argument. See [`sample-data/shipments-example.csv`](sample-data/shipments-example.csv) for the format — one row per shipment (`id,lat,lon,demand`), with two optional trailing columns (`windowStartSeconds,windowEndSeconds`) for a per-shipment time window; rows that omit them fall back to the default 08:00–18:00 window.
```bash
mvn exec:java "-Dexec.mainClass=main.Main" -Dexec.args="sample-data/shipments-example.csv"
```

### 3. Visualize
1. Open `frontend/index.html` in a browser.
2. Upload `route_monaco_fleet.json`.
3. Inspect per-vehicle routes on the map.

### Run tests
```bash
mvn test
```

---

## Testing & CI

Every push and pull request runs `mvn test` via [GitHub Actions](.github/workflows/ci.yml) (see the badge at the top). The suite covers each component in isolation (GraphHopper wrapper, clustering, Quadtree, cost matrix, VRP solver) plus a few things worth calling out:

- **`QuadtreeTest`** covers all three query modes — `query`, `nearest`, `nearestFeasible` — including null/empty-tree handling, points exactly on a subdivision line, a predicate rejecting the closest candidate, and, for each mode, a seeded oracle test asserting the Quadtree's result matches a brute-force linear scan over thousands of random points and hundreds of random queries. It specifically covers the quadrant-boundary edge case that motivated replacing `ClusteringService`'s old growing-box search with `nearestFeasible` (see above).
- **`CostMatrixCalculatorTest`** is a regression test for a real bug: `CostMatrixCalculator` used to return a fixed-size matrix (sized to `maxNodesPerCluster`) instead of one sized to the actual zone, which made `VRPSolverService` reject almost every zone. The test builds a small zone against a large pool capacity and asserts the returned matrix is sized to the zone, not the pool.
- **`MainIntegrationTest`** runs the *entire* pipeline end to end — real GraphHopper instance, real clustering, real cost matrix, real solver — for a normal-sized shipment set, a deliberately small one, and one loaded from a CSV file. This is the level at which the bug above was actually caught; the per-component unit tests alone didn't exercise that seam.
- **`ClusteringServiceTest#centroidRecentersToTheMeanOfAssignedShipments`** asserts a cluster's centroid ends up as the true mean of its assigned shipment locations, not stuck at the seed location it started from.

---

## Known Limitations / Roadmap

Documented deliberately, since knowing the edges of a system matters as much as building it:

- **Not a multi-vehicle model inside OR-Tools.** Each zone is solved as an independent single-vehicle problem. A truer CVRP would give OR-Tools all vehicles and shipments at once with a capacity `Dimension`, letting the solver — not a pre-clustering heuristic — decide the split.
- **O(n²) sequential routing calls for the cost matrix.** `CostMatrixCalculator` calls GraphHopper once per coordinate pair rather than using a batch/matrix API, which is fine at the current `maxNodesPerCluster = 50` cap but wouldn't scale past that without rework. Any pair GraphHopper can't route is now logged as a `WARN` with a fallback cost, rather than silently biasing the matrix.
- **Quadtree is only wired into clustering, not yet into the cost matrix.** `ClusteringService` uses `nearestFeasible()` for capacity-aware nearest-cluster lookup, but `CostMatrixCalculator` still computes every pair in a zone; using the Quadtree's `query()` or `nearest()` to prune obviously-distant pairs before calling GraphHopper is a planned follow-up, not done here. (An earlier version of this project kept a separate `SpatialRoutingService` scaffold for this; it was removed as dead, untested code with no consumer in the current single-depot batch pipeline — the cost-matrix pruning use case belongs directly in `CostMatrixCalculator`, not behind a separate wrapper class.)
- **Single depot, single vehicle profile.** Time windows *are* enforced in the OR-Tools model (a `Time` dimension constrains each stop's arrival to its `TimeWindow`), but there's still one depot and one vehicle profile per run.
- **No API layer** — it's a CLI-style batch job (`Main.main`) that writes a file; there's no HTTP endpoint or persistence.

---

## Author

**Pablo Jorda**
Software Engineering Student (Computing and AI)
GitHub: [@pablojordab](https://github.com/pablojordab)
