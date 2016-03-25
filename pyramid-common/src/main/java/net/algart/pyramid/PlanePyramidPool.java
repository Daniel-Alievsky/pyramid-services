/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlanePyramidPool {
    private static final boolean POOL_ENABLED = true;
    // - for debugging needs; should be true
    private static final Logger LOG = Logger.getLogger(PlanePyramidPool.class.getName());

    private final int poolSize;
    private final PlanePyramidFactory factory;
    private final PyramidPoolHashMap pool = new PyramidPoolHashMap();
    private final Thread cleaningThread;

    public PlanePyramidPool(PlanePyramidFactory factory, int poolSize) {
        this.factory = Objects.requireNonNull(factory);
        if (poolSize <= 0) {
            throw new IllegalArgumentException("Zero or negative pool size");
        }
        this.poolSize = poolSize;
        this.cleaningThread = new CleaningPyramidsThread();
        this.cleaningThread.setDaemon(true);
        this.cleaningThread.start();
    }

    public PlanePyramidFactory getFactory() {
        return factory;
    }

    public PlanePyramid getHttpPlanePyramid(String configJson) throws Exception {
        Objects.requireNonNull(configJson, "Null configJson");
        if (!POOL_ENABLED) {
            return factory.newPyramid(configJson);
        }
        synchronized (pool) {
            PlanePyramid pyramid = pool.get(configJson);
            if (pyramid != null) {
                return pyramid;
            }
            pyramid = factory.newPyramid(configJson);
            LOG.info("New pyramid has been created: " + pyramid);
            pool.put(configJson, pyramid);
            return pyramid;
        }
    }

    private void cleanOldPyramids() {
        synchronized (pool) {
//            System.out.printf("%d active pyramids%n", pool.size());
            for (Iterator<Map.Entry<String, PlanePyramid>> iterator = pool.entrySet().iterator();
                 iterator.hasNext(); )
            {
                Map.Entry<String, PlanePyramid> entry = iterator.next();
                if (entry.getValue().isTimeout()) {
                    entry.getValue().freeResources();
                    iterator.remove();
                    LOG.info("Pyramid obsolete; freeing and removing " + entry.getValue());
                }
            }
        }
    }

    private class PyramidPoolHashMap extends LinkedHashMap<String, PlanePyramid> {
        public PyramidPoolHashMap() {
            super(16, 0.75f, true);
            // necessary to set access order to true
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PlanePyramid> eldest) {
            if (size() > poolSize) {
                LOG.info("Pyramid pool overflow; freeing and removing " + eldest.getValue());
                eldest.getValue().freeResources();
                return true;
            } else {
                return false;
            }
        }
    }

    private class CleaningPyramidsThread extends Thread {
        @Override
        public void run() {
            for (; ;) {
                try {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // nothing to do
                    }
                    cleanOldPyramids();
                } catch (Throwable e) {
                    // In this very improbable case we keep this thread
                    LOG.log(Level.SEVERE, "Unexpected error in logic of cleaning obsolete pyramids!", e);
                }
            }
        }
    }
}
