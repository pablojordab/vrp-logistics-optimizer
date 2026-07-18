#  VRP Enterprise Logistics Engine

A high-performance backend routing engine built in **Java 17** to solve the Capacitated Vehicle Routing Problem (CVRP). It combines real-world geospatial data, memory optimization techniques, and advanced metaheuristics.

##  Core Architecture

This engine is built with a focus on high throughput and low-latency processing, utilizing industrial-grade libraries and custom spatial data structures.

### 1. Spatial Intelligence (Quadtrees)
Implements a custom **Quadtree** for $O(\log N)$ spatial range queries. This allows the engine to perform massive location-based filtering instantly.

### 2. Data-Oriented Design (DOD) & Zero-Allocation
To avoid GC (Garbage Collector) pauses during real-time route calculations, the engine utilizes:

* **Primitive Flattening:** Translates complex `Shipment` objects into contiguous primitive arrays.
* **Object Pooling:** Recycles matrix structures to eliminate memory allocation overhead on the "Hot Path".

### 3. Advanced Optimization (Google OR-Tools)
* Integrates Google's C++ native binary solvers via JNI.
* Utilizes *Tabu Search* and *Guided Local Search* metaheuristics.

##  Tech Stack
* **Language:** Java 17
* **Optimization:** Google OR-Tools
* **Routing/GIS:** GraphHopper Core API
* **Frontend:** HTML5, Tailwind CSS, Leaflet.js

##  How to Run
* Ensure you have Java 17 and Maven installed.
* Place monaco-latest.osm.pbf in src/main/resources/.
* Build and execute:
       `mvn clean install
       mvn exec:java`

* The engine will generate route_monaco_V-001.json.
* Open index.html in your browser and upload the generated JSON.

Developed by Pablo Jorda | Software Engineering Student (Computing & AI)


