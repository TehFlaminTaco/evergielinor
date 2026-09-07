package com.elvarg.game.world.gen;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The island's physical form: elevation, coastline and biomes.
 *
 * Geography is generated first and everything downstream reads from it, so that
 * biome, difficulty, resources and settlement all agree about the same landscape
 * instead of being scattered independently.
 *
 * @author EverGielinor world generator
 */
public final class IslandGeography {

    /** Elevation below this is sea. */
    private static final double SEA_LEVEL = 0.32;
    /** Band above sea level that renders as beach. */
    private static final double BEACH_BAND = 0.035;

    private static final double HIGHLAND = 0.62;
    private static final double MOUNTAIN = 0.73;
    private static final double PEAK = 0.84;

    /** Height byte at sea level, and the range mapped above it. */
    private static final int SEA_HEIGHT = 0;
    private static final int MAX_HEIGHT = 190;
    /**
     * Amplitude of the fine roughness laid over the broad landform, in height
     * bytes. One byte is 8 world units and a tile is 128 wide, so this is a few
     * feet of undulation - enough to stop flat ground looking poured.
     */
    private static final int LOCAL_RELIEF = 7;

    public final int size = IslandLayout.SIZE;
    public final double[][] elevation = new double[size][size];
    public final double[][] moisture = new double[size][size];
    public final Biome[][] biome = new Biome[size][size];
    public final int[][] height = new int[size][size];
    /** Distance in tiles to the nearest ocean tile, used for difficulty gradient. */
    public final int[][] distanceFromSea = new int[size][size];
    /** Whether a land tile is reachable on foot from the start beach. */
    public final boolean[][] reachable = new boolean[size][size];

    public int landTiles;
    public int reachableLandTiles;
    /** Centres of the deliberately placed hostile zones, in island-local tiles. */
    public int[] volcanoCentre;
    public int[] wildernessCentre;

    private final Noise noise;

    public IslandGeography(long seed) {
        this.noise = new Noise(seed);
    }

    public void generate() {
        buildElevation();
        normaliseMoisture();
        buildBiomes();
        buildHeights();
        buildDistanceFromSea();
    }

    // ------------------------------------------------------------------
    // Elevation
    // ------------------------------------------------------------------

    private void buildElevation() {
        Noise base = noise.channel(1);
        Noise wet = noise.channel(2);
        Noise ridges = noise.channel(3);

        double centre = size / 2.0;
        // A rounder falloff would read as an obvious disc, so the radial mask is
        // perturbed by its own noise field before it is applied.
        Noise coast = noise.channel(4);

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                double dx = (x - centre) / centre;
                double dy = (y - centre) / centre;
                double radial = Math.sqrt(dx * dx + dy * dy);
                // Push the coastline in and out by up to ~18% of the radius.
                double wobble = (coast.fbm(x, y, 70, 5) - 0.5) * 0.62;
                double masked = clamp01(1.0 - smoothstep(0.40, 1.02, radial + wobble));

                double continental = base.warpedFbm(x, y, 120, 5, 90);
                double ridge = ridges.ridged(x, y, 70, 4);
                // Ridges only bite where the land is already high, which keeps
                // mountain chains inland instead of spiking out of the sea.
                double combined = continental * 0.66 + ridge * continental * 0.62;

                // The mask decides land from sea, but raising it to a fractional
                // power stops it also doming the interior - without this the island
                // reads as concentric rings of biome around a central peak.
                elevation[x][y] = clamp01(combined * Math.pow(masked, 0.42) * 1.16);
                moisture[x][y] = wet.warpedFbm(x, y, 95, 4, 50);
            }
        }
    }

    /**
     * Stretches the moisture field to fill [0,1].
     *
     * Summed-octave noise clusters hard around 0.5, so fixed thresholds against
     * the raw field produce almost no desert and almost no swamp. Rescaling
     * against the field's own range makes the thresholds mean what they say.
     */
    private void normaliseMoisture() {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                min = Math.min(min, moisture[x][y]);
                max = Math.max(max, moisture[x][y]);
            }
        }
        double range = Math.max(1e-6, max - min);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                moisture[x][y] = (moisture[x][y] - min) / range;
            }
        }
    }

    /**
     * Temperature at a tile: warm in the south, cold in the north and cold at
     * altitude, with noise so the boundaries are not straight lines. Giving the
     * island an axis stops snow and desert appearing as rings and makes the map
     * legible - players learn that north means cold.
     */
    private double temperature(int x, int y, Noise tempNoise) {
        double latitude = (double) y / size;
        // Warm baseline everywhere, falling toward the north and with altitude, so
        // snow needs both height and latitude rather than latitude alone.
        double base = 0.30 + (1.0 - latitude) * 0.52 + tempNoise.fbm(x, y, 130, 3) * 0.20;
        return clamp01(base - elevation[x][y] * 0.38);
    }

    // ------------------------------------------------------------------
    // Biomes
    // ------------------------------------------------------------------

    private void buildBiomes() {
        // Two hostile zones are placed deliberately rather than emerging from the
        // noise, so that every world has one of each and they sit somewhere the
        // difficulty gradient can justify: deep inland, away from the start.
        Noise patch = noise.channel(5);
        Noise tempNoise = noise.channel(6);

        // The four signature biomes are placed deliberately rather than left to the
        // noise. The brief asks for a world that feels designed, and for every world
        // to contain recognisable biome families; leaving a desert to chance means
        // most seeds simply have no desert. Noise still decides where each zone goes
        // and what shape it takes - it just no longer decides whether it exists.
        java.util.List<int[]> placed = new java.util.ArrayList<>();
        int[] volcano = pickZoneCentre(noise.channel(11), 0.34, placed, size * 0.30, ZonePreference.HIGH);
        placed.add(volcano);
        int[] wilderness = pickZoneCentre(noise.channel(12), 0.26, placed, size * 0.32, ZonePreference.ANY);
        placed.add(wilderness);
        int[] desert = pickZoneCentre(noise.channel(15), 0.10, placed, size * 0.30, ZonePreference.WARM_LOWLAND);
        placed.add(desert);
        int[] swamp = pickZoneCentre(noise.channel(16), 0.06, placed, size * 0.26, ZonePreference.LOW);

        double volcanoRadius = 42 + noise.channel(13).at(0, 0) * 16;
        double wildernessRadius = 58 + noise.channel(14).at(0, 0) * 26;
        double desertRadius = 54 + noise.channel(17).at(0, 0) * 24;
        double swampRadius = 34 + noise.channel(18).at(0, 0) * 16;

        this.volcanoCentre = volcano;
        this.wildernessCentre = wilderness;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                double e = elevation[x][y];
                if (e < SEA_LEVEL) {
                    biome[x][y] = (e < SEA_LEVEL - 0.06) ? Biome.OCEAN : Biome.SHALLOWS;
                    continue;
                }
                landTiles++;

                if (e < SEA_LEVEL + BEACH_BAND) {
                    biome[x][y] = Biome.BEACH;
                    continue;
                }

                // Zone membership is tested against a noise-perturbed radius, so the
                // edges meander instead of reading as circles.
                if (inZone(x, y, volcano, volcanoRadius, patch, 0)) {
                    biome[x][y] = Biome.VOLCANIC;
                    continue;
                }
                if (inZone(x, y, wilderness, wildernessRadius, patch, 999)) {
                    biome[x][y] = Biome.WILDERNESS;
                    continue;
                }

                double t = temperature(x, y, tempNoise);
                double m = moisture[x][y];

                if (e <= HIGHLAND && inZone(x, y, desert, desertRadius, patch, 2222)) {
                    biome[x][y] = Biome.DESERT;
                    continue;
                }
                if (e <= HIGHLAND && inZone(x, y, swamp, swampRadius, patch, 3333)) {
                    biome[x][y] = Biome.SWAMP;
                    continue;
                }

                if (e > MOUNTAIN) {
                    biome[x][y] = (t < 0.30) ? Biome.SNOW : Biome.MOUNTAIN;
                } else if (e > HIGHLAND) {
                    biome[x][y] = (t < 0.20) ? Biome.SNOW : Biome.ROCKY_HIGHLAND;
                } else if (t > 0.68 && m < 0.42) {
                    biome[x][y] = Biome.DESERT;
                } else if (m > 0.72 && e < SEA_LEVEL + 0.14) {
                    biome[x][y] = Biome.SWAMP;
                } else if (m > 0.62) {
                    biome[x][y] = Biome.DENSE_FOREST;
                } else if (m > 0.48) {
                    biome[x][y] = Biome.FOREST;
                } else if (m > 0.34) {
                    biome[x][y] = Biome.GRASSLAND;
                } else {
                    biome[x][y] = Biome.PLAINS;
                }
            }
        }
    }

    /**
     * Picks a land point at least {@code minRadialDepth} of the way in from the
     * coast, scanning deterministically so the choice does not depend on call
     * order.
     */
    /** What kind of ground a deliberately placed zone wants to sit on. */
    private enum ZonePreference { ANY, HIGH, LOW, WARM_LOWLAND }

    private boolean inZone(int x, int y, int[] centre, double radius, Noise patch, int salt) {
        double wobble = 0.72 + patch.fbm(x + salt, y + salt, 26, 3) * 0.56;
        return distance(x, y, centre[0], centre[1]) < radius * wobble;
    }

    /**
     * Chooses a zone centre on land, far enough from the coast and from zones
     * already placed. The scan is a fixed deterministic sweep, so the result
     * depends only on the seed and not on the order zones are requested.
     */
    private int[] pickZoneCentre(Noise source, double minRadialDepth, java.util.List<int[]> avoid,
                                 double minSeparation, ZonePreference preference) {
        double centre = size / 2.0;
        int bestX = size / 2;
        int bestY = size / 2;
        double bestScore = -1;
        for (int x = 12; x < size - 12; x += 3) {
            for (int y = 12; y < size - 12; y += 3) {
                double e = elevation[x][y];
                if (e < SEA_LEVEL + 0.06) {
                    continue;
                }
                double dx = (x - centre) / centre;
                double dy = (y - centre) / centre;
                double depth = 1.0 - Math.sqrt(dx * dx + dy * dy);
                if (depth < minRadialDepth) {
                    continue;
                }
                boolean tooClose = false;
                for (int[] other : avoid) {
                    if (distance(x, y, other[0], other[1]) < minSeparation) {
                        tooClose = true;
                        break;
                    }
                }
                if (tooClose) {
                    continue;
                }

                double fit;
                switch (preference) {
                    case HIGH -> fit = e;
                    case LOW -> fit = 1.0 - e + moisture[x][y] * 0.6;
                    case WARM_LOWLAND -> {
                        if (e > HIGHLAND) {
                            continue;
                        }
                        // Warm means southerly here; see temperature().
                        fit = (1.0 - (double) y / size) + (1.0 - moisture[x][y]) * 0.8;
                    }
                    default -> fit = 0.5;
                }
                double score = fit * 0.7 + source.at(x, y) * 0.3 + depth * 0.2;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return new int[]{bestX, bestY};
    }

    // ------------------------------------------------------------------
    // Heights
    // ------------------------------------------------------------------

    private void buildHeights() {
        Noise detail = noise.channel(7);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (biome[x][y].isWater()) {
                    height[x][y] = SEA_HEIGHT;
                    continue;
                }
                // Remap land elevation onto the byte range so that the shore starts
                // just above sea level and peaks reach most of the way up.
                double t = (elevation[x][y] - SEA_LEVEL) / (1.0 - SEA_LEVEL);
                // Ease the low end so beaches and plains stay gently rolling and the
                // dramatic relief is reserved for highland.
                double eased = Math.pow(clamp01(t), 1.55);
                // Fine-grained roughness on top of the broad shape. Without it the
                // lowlands map to a narrow band of the height byte and render as a
                // flat plane - the hills read, but the ground between them does not.
                double roughness = (detail.fbm(x, y, 14, 3) - 0.5) * 2.0;
                int h = (int) Math.round(SEA_HEIGHT + eased * MAX_HEIGHT + roughness * LOCAL_RELIEF);
                // 1 is reserved by the format; TerrainRegion also guards this.
                height[x][y] = (h == 1) ? 2 : h;
            }
        }
        smoothHeights();
    }

    /**
     * One pass of neighbour averaging over land. Raw fBm mapped straight to the
     * height byte produces visible per-tile stepping on slopes, because a single
     * byte step is 8 world units and tiles are 128 wide.
     */
    private void smoothHeights() {
        int[][] out = new int[size][size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (biome[x][y].isWater()) {
                    out[x][y] = SEA_HEIGHT;
                    continue;
                }
                int sum = 0;
                int count = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= size || ny >= size) {
                            continue;
                        }
                        sum += height[nx][ny];
                        count++;
                    }
                }
                int h = Math.max(0, sum / count);
                out[x][y] = (h == 1) ? 2 : h;
            }
        }
        for (int x = 0; x < size; x++) {
            System.arraycopy(out[x], 0, height[x], 0, size);
        }
    }

    // ------------------------------------------------------------------
    // Distance field and connectivity
    // ------------------------------------------------------------------

    private void buildDistanceFromSea() {
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (biome[x][y].isWater()) {
                    distanceFromSea[x][y] = 0;
                    queue.add(new int[]{x, y});
                } else {
                    distanceFromSea[x][y] = Integer.MAX_VALUE;
                }
            }
        }
        // Tiles on the island edge count as coastal even if the map is land there,
        // so the gradient never runs off the end of the array.
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] d : NEIGHBOURS) {
                int nx = cur[0] + d[0];
                int ny = cur[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) {
                    continue;
                }
                if (distanceFromSea[nx][ny] > distanceFromSea[cur[0]][cur[1]] + 1) {
                    distanceFromSea[nx][ny] = distanceFromSea[cur[0]][cur[1]] + 1;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
    }

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Flood-fills walkable land from a starting tile, recording what is reachable.
     * Used by validation to prove every settlement can actually be walked to.
     */
    public void computeReachability(int startX, int startY) {
        for (boolean[] row : reachable) {
            java.util.Arrays.fill(row, false);
        }
        reachableLandTiles = 0;
        if (!IslandLayout.inBounds(startX, startY) || biome[startX][startY].isWater()) {
            return;
        }
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        reachable[startX][startY] = true;
        reachableLandTiles++;
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] d : NEIGHBOURS) {
                int nx = cur[0] + d[0];
                int ny = cur[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= size || ny >= size || reachable[nx][ny]) {
                    continue;
                }
                if (biome[nx][ny].isWater()) {
                    continue;
                }
                reachable[nx][ny] = true;
                reachableLandTiles++;
                queue.add(new int[]{nx, ny});
            }
        }
    }

    public boolean isLand(int x, int y) {
        return IslandLayout.inBounds(x, y) && !biome[x][y].isWater();
    }

    private static double distance(int x, int y, int px, int py) {
        double dx = x - px;
        double dy = y - py;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }
}
