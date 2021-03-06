/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 Daniel Alievsky, AlgART Laboratory (http://algart.net)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.algart.pyramid;

import net.algart.pyramid.requests.PlanePyramidRequest;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlanePyramidDataCache {
    //In future: maybe it makes sense to use https://cloud.google.com/appengine/docs/java/memcache/

    private final long maxMemory;
    private volatile long memory = 0;
    private final Map<PlanePyramidRequest, PlanePyramidData> map = new LinkedHashMap<>(16, 0.75f, true);
    // - accessOrder = true

    public PlanePyramidDataCache(long maxMemory) {
        if (maxMemory < 0) {
            throw new IllegalArgumentException("Negative maxMemory");
        }
        this.maxMemory = maxMemory;
    }

    public synchronized PlanePyramidData get(PlanePyramidRequest request) {
        final PlanePyramidData result = map.get(request);
        if (result == null) {
            return null;
        }
//        System.out.println("Getting " + request + " from cache");
        return result;
    }

    public synchronized void put(PlanePyramidRequest request, PlanePyramidData data) {
        map.put(request, data);
        increaseMemory(data);
        clean();
    }

    private void increaseMemory(PlanePyramidData data) {
        memory += data.estimatedMemoryInBytes();
    }

    private void decreaseMemory(PlanePyramidData removed) {
        memory -= removed.estimatedMemoryInBytes();
        if (memory < 0) {
            throw new AssertionError("Non-balanced adding and removing: " + memory);
        }
    }

    private void clean() {
        for (Iterator<Map.Entry<PlanePyramidRequest, PlanePyramidData>> iterator = map.entrySet().iterator();
             iterator.hasNext() && memory > maxMemory; )
        {
            Map.Entry<PlanePyramidRequest, PlanePyramidData> entry = iterator.next();
            iterator.remove();
            decreaseMemory(entry.getValue());
//            System.out.println("Removing " + entry.getKey() + " from cache");
        }
    }
}
