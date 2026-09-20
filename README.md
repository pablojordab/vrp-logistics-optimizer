# VRP Logistics Optimizer

[![CI](https://github.com/pablojordab/vrp-logistics-optimizer/actions/workflows/ci.yml/badge.svg)](https://github.com/pablojordab/vrp-logistics-optimizer/actions/workflows/ci.yml)
[![Java 17](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![OR-Tools](https://img.shields.io/badge/Solver-Google%20OR--Tools-4285F4?style=flat&logo=google&logoColor=white)](https://developers.google.com/optimization)
[![GraphHopper](https://img.shields.io/badge/Routing-GraphHopper%20Core-00B0FF?style=flat)](https://www.graphhopper.com/)

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

**Why the van count can exceed the estimate.** `AgentEstimatorService` gives `ClusteringService` a starting number of vans (`k`), but that number only seeds the initial clusters — it isn't a hard cap. If every existing cluster (checked via the quadtree, then a full scan as a last resort) is already at capacity for a shipment, `ClusteringService` opens one more "emergency" cluster rather than overloading a van past its capacity. This is intentional — needing one extra van is a far safer outcome than silently exceeding a van's capacity — and it's logged as a `WARN` whenever it happens, so the gap between the estimate and the actual zone count is always visible, never silent.

### Spatial Quadtree — partially wired in

The repo includes a custom hierarchical `Quadtree<T>` (with `O(log n)`-ish bounding-box queries), plus a JMH-style benchmark (`QuadtreeBenchmark`). **`ClusteringService` uses it directly**: for every shipment, it queries a small, growing bounding box around the shipment's location to find nearby clusters with spare capacity, instead of scanning every cluster — falling back to a full scan only if the local search comes up empty. A separate `SpatialRoutingService` built on the same Quadtree is **not** wired into the `Main` pipeline yet; it's exercised only via unit tests and is kept in the repo as a building block for a planned feature (pruning candidate destinations before cost-matrix computation — see roadmap).

---

## Tech Stack

* **Core language:** Java 17
* **Optimization:** Google OR-Tools 9.10 (native C++ bindings via JNI) — `PATH_CHEAPEST_ARC` first-solution strategy + `GUIDED_LOCAL_SEARCH` metaheuristic, 2s time limit per zone
* **Routing / geospatial:** GraphHopper Core 8.0, importing a real `.osm.pbf` extract (Monaco)
* **Spatial indexing:** custom 2D Quadtree, used by `ClusteringService` for candidate lookup (see above)
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
│                                       # solver, quadtree, cost matrix, full pipeline)
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

Every push and pull request runs `mvn test` via [GitHub Actions](.github/workflows/ci.yml) (see the badge at the top). The suite covers each component in isolation (GraphHopper wrapper, clustering, quadtree, VRP solver) plus two things worth calling out:

- **`CostMatrixCalculatorTest`** is a regression test for a real bug: `CostMatrixCalculator` used to return a fixed-size matrix (sized to `maxNodesPerCluster`) instead of one sized to the actual zone, which made `VRPSolverService` reject almost every zone. The test builds a small zone against a large pool capacity and asserts the returned matrix is sized to the zone, not the pool.
- **`MainIntegrationTest`** runs the *entire* pipeline end to end — real GraphHopper instance, real clustering, real cost matrix, real solver — for a normal-sized shipment set, a deliberately small one, and one loaded from a CSV file. This is the level at which the bug above was actually caught; the per-component unit tests alone didn't exercise that seam.
- **`ClusteringServiceTest#centroidRecentersToTheMeanOfAssignedShipments`** asserts a cluster's centroid ends up as the true mean of its assigned shipment locations, not stuck at the seed location it started from.

---

## Known Limitations / Roadmap

Documented deliberately, since knowing the edges of a system matters as much as building it:

- **Not a multi-vehicle model inside OR-Tools.** Each zone is solved as an independent single-vehicle problem. A truer CVRP would give OR-Tools all vehicles and shipments at once with a capacity `Dimension`, letting the solver — not a pre-clustering heuristic — decide the split.
- **O(n²) sequential routing calls for the cost matrix.** `CostMatrixCalculator` calls GraphHopper once per coordinate pair rather than using a batch/matrix API, which is fine at the current `maxNodesPerCluster = 50` cap but wouldn't scale past that without rework. Any pair GraphHopper can't route is now logged as a `WARN` with a fallback cost, rather than silently biasing the matrix.
- **Quadtree is only wired into clustering, not yet into the cost matrix.** `ClusteringService` uses it for candidate lookup (see above), but `CostMatrixCalculator` still computes every pair in a zone; using the Quadtree to prune obviously-distant pairs before calling GraphHopper — what `SpatialRoutingService` is scaffolded for — is the natural next step.
- **Single depot, single vehicle profile.** Time windows *are* enforced in the OR-Tools model (a `Time` dimension constrains each stop's arrival to its `TimeWindow`), but there's still one depot and one vehicle profile per run.
- **No API layer** — it's a CLI-style batch job (`Main.main`) that writes a file; there's no HTTP endpoint or persistence.

---

## Author

**Pablo Jorda**
Software Engineering Student (Computing and AI)
GitHub: [@pablojordab](https://github.com/pablojordab)
