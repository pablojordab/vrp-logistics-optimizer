package spatial;

import domain.BoundingBox;
import domain.Coordinates;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuadtreeTest {

    @Test
    void testQuadtreeInsertionAndQuery() {
        BoundingBox spainRegion = new BoundingBox(35.0, -10.0, 44.0, 5.0);
        Quadtree<String> tree = new Quadtree<>(spainRegion);

        QuadNode<String> madridCenter = new QuadNode<>(new Coordinates(40.41, -3.70), "Madrid Center");
        QuadNode<String> madridSouth = new QuadNode<>(new Coordinates(40.30, -3.70), "Madrid South");
        QuadNode<String> barcelona = new QuadNode<>(new Coordinates(41.38, 2.15), "Barcelona");
        QuadNode<String> valencia = new QuadNode<>(new Coordinates(39.46, -0.37), "Valencia");
        QuadNode<String> bilbao = new QuadNode<>(new Coordinates(43.26, -2.93), "Bilbao");

        tree.insert(madridCenter);
        tree.insert(madridSouth);
        tree.insert(barcelona);
        tree.insert(valencia);
        tree.insert(bilbao);

        BoundingBox centralArea = new BoundingBox(39.5, -4.5, 41.5, -3.0);
        List<QuadNode<String>> results = tree.query(centralArea, new ArrayList<>());

        assertEquals(2, results.size(), "The query should find exactly 2 clients in the central area.");

        boolean foundMadridCenter = results.stream().anyMatch(n -> n.getData().equals("Madrid Center"));
        boolean foundMadridSouth = results.stream().anyMatch(n -> n.getData().equals("Madrid South"));

        assertTrue(foundMadridCenter, "It should have found Madrid Center.");
        assertTrue(foundMadridSouth, "It should have found Madrid South.");

        boolean foundBarcelona = results.stream().anyMatch(n -> n.getData().equals("Barcelona"));
        assertFalse(foundBarcelona, "It should NOT find Barcelona with that bounding box.");
    }
}