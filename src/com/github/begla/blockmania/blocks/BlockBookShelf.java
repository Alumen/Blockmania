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

package com.github.begla.blockmania.blocks;

import com.github.begla.blockmania.utilities.Helper;
import org.lwjgl.util.vector.Vector2f;

/**
 * A bookshelf block.
 *
 * @author Benjamin Glatzel <benjamin.glatzel@me.com>
 */
public class BlockBookShelf extends Block {

    @Override
    public Vector2f getTextureOffsetFor(SIDE side) {
        if (side == SIDE.TOP || side == SIDE.BOTTOM) {
            return Helper.calcOffsetForTextureAt(6, 5);
        }

        return Helper.calcOffsetForTextureAt(3, 2);
    }
}
