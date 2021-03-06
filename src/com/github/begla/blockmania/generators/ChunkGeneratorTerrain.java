/*
 * Copyright 2011 Benjamin Glatzel <benjamin.glatzel@me.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.begla.blockmania.generators;

import com.github.begla.blockmania.main.Configuration;
import com.github.begla.blockmania.utilities.MathHelper;
import com.github.begla.blockmania.world.chunk.Chunk;

/**
 * Generates the terrain of the world using a hybrid voxel-/heightmap-based approach.
 *
 * @author Benjamin Glatzel <benjamin.glatzel@me.com>
 */
public class ChunkGeneratorTerrain extends ChunkGenerator {

    private static final int SAMPLE_RATE_3D_HOR = 8;
    private static final int SAMPLE_RATE_3D_VERT = 4;

    public enum BIOME_TYPE {
        MOUNTAINS, SNOW, DESERT, PLAINS
    }

    /**
     * @param seed
     */
    public ChunkGeneratorTerrain(String seed) {
        super(seed);
    }

    /**
     * @param c
     */
    @Override
    public void generate(Chunk c) {
        double[][][] densityMap = new double[(int) Configuration.CHUNK_DIMENSIONS.x + 1][(int) Configuration.CHUNK_DIMENSIONS.y + 1][(int) Configuration.CHUNK_DIMENSIONS.z + 1];

        /*
         * Create the density map at a lower sample rate.
         */
        for (int x = 0; x <= Configuration.CHUNK_DIMENSIONS.x; x += SAMPLE_RATE_3D_HOR) {
            for (int z = 0; z <= Configuration.CHUNK_DIMENSIONS.z; z += SAMPLE_RATE_3D_HOR) {
                BIOME_TYPE type = calcBiomeType(c.getBlockWorldPosX(x), c.getBlockWorldPosZ(z));

                for (int y = 0; y <= Configuration.CHUNK_DIMENSIONS.y; y += SAMPLE_RATE_3D_VERT) {
                    densityMap[x][y][z] = calcDensity(c.getBlockWorldPosX(x), y, c.getBlockWorldPosZ(z), type);
                }
            }
        }

        /*
         * Trilinear interpolate the missing values.
         */
        triLerpDensityMap(densityMap);

        /*
         * Generate the chunk from the density map.
         */
        for (int x = 0; x < Configuration.CHUNK_DIMENSIONS.x; x++) {
            for (int z = 0; z < Configuration.CHUNK_DIMENSIONS.z; z++) {
                BIOME_TYPE type = calcBiomeType(c.getBlockWorldPosX(x), c.getBlockWorldPosZ(z));
                int firstBlockHeight = -1;

                for (int y = (int) Configuration.CHUNK_DIMENSIONS.y; y >= 0; y--) {

                    if (y == 0) { // Hard stone ground layer
                        c.setBlock(x, y, z, (byte) 0x8);
                        break;
                    }

                    if (y <= 32 && y > 0) { // Ocean
                        c.setBlock(x, y, z, (byte) 0x4);

                        if (y == 32) {
                            // Ice layer
                            if (type == BIOME_TYPE.SNOW)
                                c.setBlock(x, y, z, (byte) 0x11);
                        }
                    }

                    double dens = densityMap[x][y][z];

                    if ((dens >= 0.008 && dens < 0.01)) {

                        // Some block was set...
                        if (firstBlockHeight == -1)
                            firstBlockHeight = y;

                        GenerateOuterLayer(x, y, z, firstBlockHeight, c, type);
                        continue;
                    } else if (dens >= 0.01) {

                        // Some block was set...
                        if (firstBlockHeight == -1)
                            firstBlockHeight = y;

                        if (calcCaveDensity(c.getBlockWorldPosX(x), y, c.getBlockWorldPosZ(z)) > -0.6)
                            GenerateInnerLayer(x, y, z, c, type);

                        continue;
                    }

                    // Nothing was set!
                    firstBlockHeight = -1;
                }
            }
        }
    }

    private void GenerateInnerLayer(int x, int y, int z, Chunk c, BIOME_TYPE type) {
        c.setBlock(x, y, z, (byte) 0x3);
    }

    private void GenerateOuterLayer(int x, int y, int z, int firstBlockHeight, Chunk c, BIOME_TYPE type) {

        double heightPercentage = (firstBlockHeight - y) / Configuration.CHUNK_DIMENSIONS.y;

        switch (type) {
            case PLAINS:
            case MOUNTAINS:
                // Beach
                if (y >= 26 && y <= 32) {
                    c.setBlock(x, y, z, (byte) 0x7);
                } else if (heightPercentage == 0 && y > 32) {
                    // Grass on top
                    c.setBlock(x, y, z, (byte) 0x1);
                } else if (heightPercentage > 0.02) {
                    // Stone
                    c.setBlock(x, y, z, (byte) 0x3);
                } else {
                    // Dirt
                    c.setBlock(x, y, z, (byte) 0x2);
                }

                if (type == BIOME_TYPE.PLAINS)
                    generateRiver(c, x, y, z, heightPercentage, type);
                break;
            case SNOW:

                if (heightPercentage == 0.0 && y > 32) {
                    // Snow on top
                    c.setBlock(x, y, z, (byte) 0x17);
                } else if (heightPercentage > 0.02) {
                    // Stone
                    c.setBlock(x, y, z, (byte) 0x3);
                } else {
                    // Dirt
                    c.setBlock(x, y, z, (byte) 0x2);
                }

                generateRiver(c, x, y, z, heightPercentage, type);
                break;

            case DESERT:
                if (heightPercentage > 0.2) {
                    // Stone
                    c.setBlock(x, y, z, (byte) 0x3);
                } else {
                    c.setBlock(x, y, z, (byte) 0x7);
                }

                break;

        }
    }

    private void generateRiver(Chunk c, int x, int y, int z, double heightPercentage, BIOME_TYPE type) {
        // Rivers under water? Nope.
        if (y <= 32)
            return;

        double lakeIntens = calcLakeIntensity(c.getBlockWorldPosX(x), c.getBlockWorldPosZ(z));

        if (lakeIntens < 0.2 && heightPercentage < 0.015) {
            c.setBlock(x, y, z, (byte) 0x0);
        } else if (lakeIntens < 0.2 && heightPercentage >= 0.015 && heightPercentage < 0.05) {
            if (type == BIOME_TYPE.SNOW) {
                c.setBlock(x, y, z, (byte) 0x11);
            } else {
                c.setBlock(x, y, z, (byte) 0x04);
            }
        }
    }

    protected BIOME_TYPE calcBiomeType(int x, int z) {
        double temp = calcTemperature(x, z);

        if (temp >= 60) {
            return BIOME_TYPE.DESERT;
        } else if (temp >= 32) {
            return BIOME_TYPE.MOUNTAINS;
        } else if (temp < 8) {
            return BIOME_TYPE.SNOW;
        }

        return BIOME_TYPE.PLAINS;
    }

    /**
     * @param densityMap
     */
    private void triLerpDensityMap(double[][][] densityMap) {
        for (int x = 0; x < Configuration.CHUNK_DIMENSIONS.x; x++) {
            for (int y = 0; y < Configuration.CHUNK_DIMENSIONS.y; y++) {
                for (int z = 0; z < Configuration.CHUNK_DIMENSIONS.z; z++) {
                    if (!(x % SAMPLE_RATE_3D_HOR == 0 && y % SAMPLE_RATE_3D_VERT == 0 && z % SAMPLE_RATE_3D_HOR == 0)) {
                        int offsetX = (x / SAMPLE_RATE_3D_HOR) * SAMPLE_RATE_3D_HOR;
                        int offsetY = (y / SAMPLE_RATE_3D_VERT) * SAMPLE_RATE_3D_VERT;
                        int offsetZ = (z / SAMPLE_RATE_3D_HOR) * SAMPLE_RATE_3D_HOR;
                        densityMap[x][y][z] = MathHelper.triLerp(x, y, z, densityMap[offsetX][offsetY][offsetZ], densityMap[offsetX][SAMPLE_RATE_3D_VERT + offsetY][offsetZ], densityMap[offsetX][offsetY][offsetZ + SAMPLE_RATE_3D_HOR], densityMap[offsetX][offsetY + SAMPLE_RATE_3D_VERT][offsetZ + SAMPLE_RATE_3D_HOR], densityMap[SAMPLE_RATE_3D_HOR + offsetX][offsetY][offsetZ], densityMap[SAMPLE_RATE_3D_HOR + offsetX][offsetY + SAMPLE_RATE_3D_VERT][offsetZ], densityMap[SAMPLE_RATE_3D_HOR + offsetX][offsetY][offsetZ + SAMPLE_RATE_3D_HOR], densityMap[SAMPLE_RATE_3D_HOR + offsetX][offsetY + SAMPLE_RATE_3D_VERT][offsetZ + SAMPLE_RATE_3D_HOR], offsetX, SAMPLE_RATE_3D_HOR + offsetX, offsetY, SAMPLE_RATE_3D_VERT + offsetY, offsetZ, offsetZ + SAMPLE_RATE_3D_HOR);
                    }
                }
            }
        }
    }

    /**
     * @param x
     * @param y
     * @param z
     * @param type
     * @return
     */
    public double calcDensity(double x, double y, double z, BIOME_TYPE type) {
        double height = calcBaseTerrain(x, z);
        double density = calcMountainDensity(x, y, z);

        double divHeight = (y + 1) * 1.2;

        if (y > 100)
            divHeight *= 2.0;

        if (type == BIOME_TYPE.DESERT) {
            divHeight *= 1.3;
        } else if (type == BIOME_TYPE.PLAINS) {
            divHeight *= 1.4;
        } else if (type == BIOME_TYPE.MOUNTAINS) {
            divHeight *= 1.0;
        } else if (type == BIOME_TYPE.SNOW) {
            divHeight *= 1.2;
        }

        return (height + density) / divHeight;
    }

    /**
     * Returns the roughness for the base terrain.
     *
     * @param x
     * @param z
     * @return
     */
    protected double calcBaseTerrain(double x, double z) {
        double result = 0.0;

        result += _pGen2.fBm(0.0009 * x, 0, 0.0009 * z, 3, 2.2341, 0.94321) + 0.4;

        return result;
    }

    /**
     * @param x
     * @param y
     * @param z
     * @return
     */
    protected double calcMountainDensity(double x, double y, double z) {
        double result = 0.0;

        double x1, y1, z1;

        x1 = x * 0.0006;
        y1 = y * 0.0008;
        z1 = z * 0.0006;

        double freq[] = {1.232, 8.4281, 16.371, 32, 64};
        double amp[] = {1.0, 1.4, 1.6, 1.8, 2.0};

        double ampSum = 0.0;
        for (int i = 0; i < freq.length; i++) {
            result += _pGen5.noise(x1 * freq[i], y1 * freq[i], z1 * freq[i]) * amp[i];
            ampSum += amp[i];
        }

        return (result / ampSum);
    }


    /**
     * @param x
     * @param z
     * @return
     */
    protected double calcLakeIntensity(double x, double z) {
        double result = 0.0;
        result += _pGen3.fBm(x * 0.01, 0.01, 0.01 * z, 3, 2.1836171, 0.9631);
        return Math.sqrt(Math.abs(result));
    }

    protected double calcTemperature(double x, double z) {
        double result = 0.0;
        result += _pGen4.fBm(x * 0.0008, 0, 0.0008 * z, 7, 2.1836171, 0.7631);

        result = 32.0 + (result) * 64.0;

        return result;
    }

    protected double calcCaveDensity(double x, double y, double z) {
        double result = 0.0;
        result += _pGen6.fBm(x * 0.06, y * 0.06, z * 0.06, 2, 2.1287129, 0.8531);
        return result;
    }
}
