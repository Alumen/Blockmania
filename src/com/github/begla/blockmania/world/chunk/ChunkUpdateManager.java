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
package com.github.begla.blockmania.world.chunk;

import com.github.begla.blockmania.world.World;
import javolution.util.FastList;

import java.util.Collections;

/**
 * Provides support for updating and generating chunks.
 *
 * @author Benjamin Glatzel <benjamin.glatzel@me.com>
 */
public final class ChunkUpdateManager {

    private final FastList<Chunk> _vboUpdates = new FastList<Chunk>(128);

    private double _meanUpdateDuration = 0.0;
    private final World _parent;

    private int _chunkUpdateAmount;

    /**
     * @param _parent
     */
    public ChunkUpdateManager(World _parent) {
        this._parent = _parent;
    }

    public void processChunkUpdates() {
        long timeStart = System.currentTimeMillis();

        FastList<Chunk> dirtyChunks = new FastList<Chunk>(_parent.getVisibleChunks());

        for (int i = dirtyChunks.size() - 1; i >= 0; i--) {
            Chunk c = dirtyChunks.get(i);

            if (c == null) {
                dirtyChunks.remove(i);
                continue;
            }

            if (!(c.isDirty() || c.isFresh() || c.isLightDirty())) {
                dirtyChunks.remove(i);
            }
        }

        if (!dirtyChunks.isEmpty()) {
            Collections.sort(dirtyChunks);
            Chunk closestChunk = dirtyChunks.getFirst();
            processChunkUpdate(closestChunk);
        }

        _chunkUpdateAmount = dirtyChunks.size();

        _meanUpdateDuration += System.currentTimeMillis() - timeStart;
        _meanUpdateDuration /= 2;
    }

    private void processChunkUpdate(Chunk c) {
        if (c != null) {
            /*
             * Generate the chunk...
             */
            c.generate();

            /*
             * ... and fetch its neighbors...
             */
            Chunk[] neighbors = c.loadOrCreateNeighbors();

            /*
             * Before starting the illumination process, make sure that the neighbor chunks
             * are present and generated.
             */
            for (int i = 0; i < neighbors.length; i++) {
                if (neighbors[i] != null) {
                    neighbors[i].generate();
                }
            }

            /*
             * If the light of this chunk is marked as dirty...
             */
            if (c.isLightDirty()) {
                /*
                 * ... propagate light into adjacent chunks...
                 */
                c.updateLight();
            }

            /*
             * Check if this chunk was changed...
             */
            if (c.isDirty() && !c.isLightDirty() && !c.isFresh()) {
                /*
                 * ... if yes, regenerate the vertex arrays
                 */
                c.generateMesh();
                _vboUpdates.add(c);
            }
        }
    }

    public void updateVBOs() {
        while (!_vboUpdates.isEmpty()) {
            Chunk c = _vboUpdates.removeFirst();
            c.generateVBOs();
        }
    }

    public int getUpdatesSize() {
        return _chunkUpdateAmount;
    }

    public int getVboUpdatesSize() {
        return _vboUpdates.size();
    }

    public double getMeanUpdateDuration() {
        return _meanUpdateDuration;
    }
}
