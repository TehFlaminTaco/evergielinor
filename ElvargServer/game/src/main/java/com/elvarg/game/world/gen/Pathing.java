package com.elvarg.game.world.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Walks a route across the island.
 *
 * Roads and village lanes were previously traced by stepping to whichever
 * neighbour looked cheapest. That is a hill-descent, and hill-descents get stuck:
 * on the amplified terrain the walker oscillated between two tiles until its
 * guard counter ran out, so a road between two settlements came out as three or
 * four painted tiles next to the first one and then nothing. The whole island had
 * 390 road tiles across 27 settlements.
 *
 * This is a proper least-cost search instead. Cost is distance plus a penalty for
 * climbing, so a route contours around a hill rather than going over it, and an
 * already-worn tile is cheap, so separate routes merge into shared lanes. Because
 * the search is exhaustive within its expansion budget it either finds the route
 * or proves there isn't one - it never gives up halfway and leaves a stub.
 *
 * @author EverGielinor world generator
 */
final class Pathing {

    /**
     * Multiplier applied to an already-worn tile's step cost. Low enough that
     * joining an existing track and following it beats cutting a parallel one.
     */
    private static final double WORN_DISCOUNT = 0.35;

    /**
     * Per-tile cost floor, used by the heuristic. It has to be no greater than the
     * cheapest a step can possibly be, or the search stops being admissible and
     * starts returning routes that are not the cheapest.
     */
    private static final double MIN_STEP_COST = WORN_DISCOUNT;

    private Pathing() {
    }

    /**
     * Finds the cheapest walking route between two tiles.
     *
     * @param worn        tiles already carrying a track, which are cheaper to
     *                    re-use; may be null
     * @param climbWeight cost charged per height byte of ascent or descent. Around
     *                    0.35 gives roads that bend around hills but will still
     *                    cross a saddle; higher values hug the contours harder.
     * @param budget      maximum tiles the search may expand before giving up
     * @return the route including both endpoints, or null if there is no walkable
     *         route within the budget
     */
    static List<int[]> route(IslandGeography geography, int fromX, int fromY, int toX, int toY,
                             int[][] worn, double climbWeight, int budget) {
        if (!geography.isWalkable(fromX, fromY) || !geography.isWalkable(toX, toY)) {
            return null;
        }
        int size = geography.size;
        int start = fromX * size + fromY;
        int goal = toX * size + toY;
        if (start == goal) {
            List<int[]> single = new ArrayList<>();
            single.add(new int[]{fromX, fromY});
            return single;
        }

        // Open set keyed on f = g + h. Arrays rather than maps: 590k tiles of
        // boxed keys would cost more than the search itself.
        double[] best = new double[size * size];
        java.util.Arrays.fill(best, Double.MAX_VALUE);
        int[] cameFrom = new int[size * size];
        java.util.Arrays.fill(cameFrom, -1);
        boolean[] closed = new boolean[size * size];

        PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        best[start] = 0;
        open.add(new long[]{0, start});

        int expanded = 0;
        while (!open.isEmpty() && expanded < budget) {
            long[] head = open.poll();
            int current = (int) head[1];
            if (closed[current]) {
                continue;
            }
            closed[current] = true;
            expanded++;
            if (current == goal) {
                return reconstruct(cameFrom, current, size);
            }
            int cx = current / size;
            int cy = current % size;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (!geography.isWalkable(nx, ny)) {
                        continue;
                    }
                    // A diagonal that squeezes past a corner is not walkable on the
                    // ground either, so require both orthogonal neighbours.
                    if (dx != 0 && dy != 0
                            && (!geography.isWalkable(cx + dx, cy) || !geography.isWalkable(cx, cy + dy))) {
                        continue;
                    }
                    int next = nx * size + ny;
                    if (closed[next]) {
                        continue;
                    }
                    double step = (dx != 0 && dy != 0) ? 1.4142 : 1.0;
                    step += Math.abs(geography.height[nx][ny] - geography.height[cx][cy]) * climbWeight;
                    if (worn != null && worn[nx][ny] != 0) {
                        step *= WORN_DISCOUNT;
                    }
                    double candidate = best[current] + step;
                    if (candidate >= best[next]) {
                        continue;
                    }
                    best[next] = candidate;
                    cameFrom[next] = current;
                    double heuristic = Math.hypot(toX - nx, toY - ny) * MIN_STEP_COST;
                    // Scaled to a long so the queue orders on a primitive; the
                    // factor is large enough that ties are genuine ties.
                    open.add(new long[]{(long) ((candidate + heuristic) * 64), next});
                }
            }
        }
        return null;
    }

    private static List<int[]> reconstruct(int[] cameFrom, int goal, int size) {
        List<int[]> route = new ArrayList<>();
        for (int node = goal; node != -1; node = cameFrom[node]) {
            route.add(new int[]{node / size, node % size});
        }
        java.util.Collections.reverse(route);
        return route;
    }
}
