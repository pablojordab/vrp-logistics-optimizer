# VRP Logistics Optimizer

[![Java 17](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![OR-Tools](https://img.shields.io/badge/Solver-Google%20OR--Tools-4285F4?style=flat&logo=google&logoColor=white)](https://developers.google.com/optimization)
[![GraphHopper](https://img.shields.io/badge/Routing-GraphHopper%20Core-00B0FF?style=flat)](https://www.graphhopper.com/)

A Java 17 backend that solves a single-depot **Capacitated Vehicle Routing Problem (CVRP)** using a *cluster-first, route-second* strategy: shipments are grouped into capacity-feasible zones with a custom greedy heuristic, and each zone's stop order is then optimized with **Google OR-Tools**. Travel times come from a **real road network** (OpenStreetMap data for Monaco, via GraphHopper Core), not straight-line approximations. Output is a GeoJSON file you can drop straight into a map.

This project was built to get hands-on with combinatorial optimization, real routing engines, and JNI-backed native solvers in Java — it's a learning/portfolio project, not a production routing system. See [Known Limitations](#known-limitations--roadmap) below for an honest breakdown of what it does and doesn't do yet.

---

## What it actually does

```
Random shipments (Monaco)
        │
        ▼
AgentEstimatorService ──► how many vans are needed (total demand / van capacity)
        │
        ▼
ClusteringService ──► greedy capacity-constrained clustering (1 zone per van)
        │
        ▼
 For each zone:
   CostMatrixCalculator ──► pairwise driving-time matrix (GraphHopper queries)
        │
        ▼
   VRPSolverService ──► OR-Tools TSP over that zone (PATH_CHEAPEST_ARC + Guided Local Search)
        │
        ▼
   Road geometry stitched stop-to-stop (GraphHopper)
        │
        ▼
GeoJsonSerializer ──► route_monaco_fleet.json
```

**Important nuance:** capacity handling and route optimization are split across two different components. `ClusteringService` decides *which shipments go to which van* (a greedy nearest-with-capacity-check heuristic, not part of the OR-Tools model). `VRPSolverService` then solves a **single-vehicle TSP** per zone — OR-Tools is not given multiple vehicles or a capacity dimension directly. This is a legitimate and common decomposition of CVRP, but it means the "combinatorial optimization" happens over stop *ordering*, while fleet *assignment* is a simpler heuristic.

### Spatial Quadtree — currently a standalone module

The repo includes a custom hierarchical `Quadtree<T>` (with `O(log n)`-ish bounding-box queries) and a `SpatialRoutingService` built on top of it, plus a JMH-style benchmark (`QuadtreeBenchmark`). **These are not wired into the `Main` pipeline yet** — the main run never touches them. They're exercised in isolation via unit tests and the benchmark, and are kept in the repo as a spatial-indexing building block for a planned feature (candidate pruning before matrix computation — see roadmap).

---

## Tech Stack

* **Core language:** Java 17
* **Optimization:** Google OR-Tools 9.10 (native C++ bindings via JNI) — `PATH_CHEAPEST_ARC` first-solution strategy + `GUIDED_LOCAL_SEARCH` metaheuristic, 2s time limit per zone
* **Routing / geospatial:** GraphHopper Core 8.0, importing a real `.osm.pbf` extract (Monaco)
* **Spatial indexing:** custom 2D Quadtree (currently standalone, see above)
* **Build:** Apache Maven
* **Visualization:** static HTML + Leaflet.js frontend that renders the generated GeoJSON

---

## Repository Layout

```text
├── frontend/
│   └── index.html                     # Leaflet.js GeoJSON route viewer
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── domain/                # Coordinates, Shipment, Vehicle, TimeWindow, etc.
│   │   │   ├── main/Main.java         # Orchestrates the pipeline end to end
│   │   │   ├── routing/               # GraphHopperManager (road network wrapper)
│   │   │   ├── service/               # Clustering, cost matrix, VRP solver, GeoJSON export
│   │   │   └── spatial/               # Quadtree + QuadNode (standalone, see note above)
│   │   └── resources/
│   │       └── monaco-latest.osm.pbf  # Real OpenStreetMap extract
│   └── test/java/                     # Unit tests for GraphHopper, clustering, solver, quadtree
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

### 3. Visualize
1. Open `frontend/index.html` in a browser.
2. Upload `route_monaco_fleet.json`.
3. Inspect per-vehicle routes on the map.

### Run tests
```bash
mvn test
```

---

## Known Limitations / Roadmap

Documented deliberately, since knowing the edges of a system matters as much as building it:

- **Not a multi-vehicle model inside OR-Tools.** Each zone is solved as an independent single-vehicle TSP. A truer CVRP would give OR-Tools all vehicles and shipments at once with a capacity `Dimension`, letting the solver — not a pre-clustering heuristic — decide the split.
- **Cluster centroids are static.** `ClusteringService` fixes each cluster's centroid at the seed shipment and never recomputes it as shipments are added, so assignment quality can drift for larger, denser demand sets.
- **O(n²) sequential routing calls for the cost matrix.** `CostMatrixCalculator` calls GraphHopper once per coordinate pair rather than using a batch/matrix API, which is fine at the current `maxNodesPerCluster = 50` cap but wouldn't scale past that without rework.
- **Quadtree isn't in the live pipeline yet** (see above) — next step is using it to prune candidate destinations before matrix computation, which is what `SpatialRoutingService` is scaffolded for.
- **Synthetic demand.** Shipments are randomly generated with a fixed seed for reproducibility, not sourced from real order data.
- **Single depot, single vehicle profile, no time windows enforced in the solver** (a `TimeWindow` domain type exists but isn't yet passed into the OR-Tools model).
- **No API layer** — it's a CLI-style batch job (`Main.main`) that writes a file; there's no HTTP endpoint or persistence.

---

## Author

**Pablo Jorda**
Software Engineering Student (Computing and AI)
GitHub: [@pablojordab](https://github.com/pablojordab)
