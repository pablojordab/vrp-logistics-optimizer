package service;

import spatial.Quadtree;
import spatial.QuadNode;
import domain.Coordinates;
import domain.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QuadtreeBenchmark {

    public static void main(String[] args) {
        int N = 10000;
        int QUERIES = 1000;

        double minLat = 43.72, maxLat = 43.75;
        double minLon = 7.40, maxLon = 7.45;

        Random rand = new Random(42);

        List<Coordinates> points = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            double lat = minLat + rand.nextDouble() * (maxLat - minLat);
            double lon = minLon + rand.nextDouble() * (maxLon - minLon);
            points.add(new Coordinates(lat, lon));
        }

        BoundingBox worldBoundary = new BoundingBox(minLat, minLon, maxLat, maxLon);
        Quadtree<Integer> tree = new Quadtree<>(worldBoundary);
        for (int i = 0; i < N; i++) {
            tree.insert(new QuadNode<>(points.get(i), i));
        }

        double queryW = (maxLon - minLon) * 0.05;
        double queryH = (maxLat - minLat) * 0.05;

        long startQ = System.nanoTime();
        for (int i = 0; i < QUERIES; i++) {
            double qLat = minLat + rand.nextDouble() * (maxLat - minLat - queryH);
            double qLon = minLon + rand.nextDouble() * (maxLon - minLon - queryW);
            BoundingBox range = new BoundingBox(qLat, qLon, qLat + queryH, qLon + queryW);
            tree.query(range, new ArrayList<>());
        }
        long endQ = System.nanoTime();
        double quadTreeMs = (endQ - startQ) / 1_000_000.0;

        Random rand2 = new Random(42);
        long startL = System.nanoTime();
        for (int i = 0; i < QUERIES; i++) {
            double qLat = minLat + rand2.nextDouble() * (maxLat - minLat - queryH);
            double qLon = minLon + rand2.nextDouble() * (maxLon - minLon - queryW);
            double qLatMax = qLat + queryH;
            double qLonMax = qLon + queryW;

            List<Coordinates> found = new ArrayList<>();
            for (Coordinates c : points) {
                if (c.lat() >= qLat && c.lat() <= qLatMax &&
                    c.lon() >= qLon && c.lon() <= qLonMax) {
                    found.add(c);
                }
            }
        }
        long endL = System.nanoTime();
        double linearMs = (endL - startL) / 1_000_000.0;

        System.out.println("=== Quadtree vs Linear Search Benchmark ===");
        System.out.println("Points indexed: " + N);
        System.out.println("Queries executed: " + QUERIES);
        System.out.println("Quadtree total time: " + quadTreeMs + " ms (" + (quadTreeMs / QUERIES) + " ms/query)");
        System.out.println("Linear search total time: " + linearMs + " ms (" + (linearMs / QUERIES) + " ms/query)");
        System.out.println("Speedup: " + (linearMs / quadTreeMs) + "x");
    }
}