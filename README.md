# VRP Enterprise Logistics Engine

[![Java 17](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![OR-Tools](https://img.shields.io/badge/Solver-Google%20OR--Tools-4285F4?style=flat&logo=google&logoColor=white)](https://developers.google.com/optimization)
[![GraphHopper](https://img.shields.io/badge/Routing-GraphHopper%20Core-00B0FF?style=flat)](https://www.graphhopper.com/)

A high-throughput backend routing engine built in **Java 17** designed to solve the **Capacitated Vehicle Routing Problem (CVRP)** at enterprise scale. The system integrates real-world OpenStreetMap road networks, low-latency spatial pruning algorithms, zero-allocation memory patterns, and combinatorial optimization metaheuristics.

---

## Core Architecture

The platform executes a multi-stage optimization pipeline combining spatial indexing, real-world road graph extraction, and combinatorial optimization:

```
[ OSM Road Network (.osm.pbf) ] ──> [ GraphHopper Graph Engine ]
                                                │
                                                ▼ (Distance / Time Matrix)
[ Delivery Coordinates ] ──> [ Spatial Quadtree ] ──> [ Two-Phase Solver (OR-Tools) ] ──> [ GeoJSON Route Output ]
                               (O(log N) Pruning)       1. Greedy (Cheapest Arc)
                                                        2. Guided Local Search (GLS)
```

### 1. Spatial Intelligence and Pruning (Quadtree)
* Custom hierarchical **QuadTree** data structure for spatial point indexing.
* Reduces point lookup and bounding-box spatial range queries to $\mathcal{O}(\log N)$, eliminating spatial bottlenecks prior to cost matrix computation.

### 2. High-Precision Road Routing (GraphHopper Core API)
* Consumes raw OpenStreetMap Protocolbuffer data (`monaco-latest.osm.pbf`) into an in-memory directed graph.
* Computes exact real-world driving times and turn-by-turn routing distances instead of Euclidean approximations.

### 3. Two-Phase Optimization Pipeline (Google OR-Tools via JNI)
* **Phase 1 — Greedy Construction (`PATH_CHEAPEST_ARC`):** Rapidly constructs a feasible baseline solution by iteratively connecting the lowest-cost reachable nodes.
* **Phase 2 — Metaheuristic Exploration (`GUIDED_LOCAL_SEARCH`):** Employs Guided Local Search (GLS) to systematically escape local minima. When the underlying local search plateaus, GLS dynamically penalizes expensive solution features to force diversification toward the global optimum.

### 4. Data-Oriented Design (DOD) and Zero-Allocation Hot Path
* **Primitive Flattening:** Complex entities are flattened into contiguous primitive arrays (`int[]`, `double[]`) to maximize CPU cache locality.
* **Object Pooling:** Cost matrix instances and temporary spatial structures are recycled to suppress garbage collection (GC) pressure during real-time route calculations.

---

## Tech Stack

* **Core Language:** Java 17 (LTS)
* **Optimization Engine:** Google OR-Tools (Native C++ bindings via JNI)
* **Geospatial and Road Engine:** GraphHopper Core
* **Spatial Structures:** Custom 2D Spatial Quadtree
* **Build System:** Apache Maven
* **Interactive Frontend:** HTML5, Tailwind CSS, Leaflet.js

---

## Repository Layout

```text
├── frontend/
│   └── index.html               # Leaflet.js route visualizer
├── src/
│   ├── main/
│   │   ├── java/                # Routing engine, solver services, spatial quadtree
│   │   └── resources/
│   │       └── monaco-latest.osm.pbf  # Real-world OpenStreetMap extract
│   └── test/
│       └── java/                # Unit test suite (GraphHopper and VRP services)
├── .gitignore                   # Excludes runtime caches and simulation outputs
├── pom.xml                      # Maven dependencies and build lifecycle
└── README.md
```

---

## Getting Started

### Prerequisites
* **Java Development Kit (JDK) 17** or higher.
* **Apache Maven 3.8+**.

### 1. Build and Compile
```bash
mvn clean compile
```

### 2. Run the Optimization Engine
```bash
mvn exec:java -Dexec.mainClass="main.Main"
```
*On initial startup, GraphHopper parses the road network and generates a local index in `graphhopper-cache/` (excluded by Git). The solver then calculates optimal fleet assignments and outputs `route_monaco_fleet.json`.*

### 3. Visualize Fleet Routes
1. Open `frontend/index.html` in a web browser.
2. Upload the generated `route_monaco_fleet.json` file.
3. Inspect turn-by-turn paths, vehicle assignments, and delivery stops on the map.

---

## Author

**Pablo Jorda**  
Software Engineering Student (Computing and AI)  
GitHub: [@pablojordab](https://github.com/pablojordab)
