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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlanePyramidPool {
    private static final boolean POOL_ENABLED = true;
    // - for debugging needs; should be true
    private static final Logger LOG = Logger.getLogger(PlanePyramidPool.class.getName());

    private final int poolSize;
    private final PlanePyramidFactory factory;
    private final PyramidPoolHashMap pool = new PyramidPoolHashMap();

    public PlanePyramidPool(PlanePyramidFactory factory, int poolSize) {
        this.factory = Objects.requireNonNull(factory);
        if (poolSize <= 0) {
            throw new IllegalArgumentException("Zero or negative pool size");
        }
        this.poolSize = poolSize;
        final Thread cleaningThread = new CleaningPyramidsThread();
        cleaningThread.setDaemon(true);
        cleaningThread.start();
    }

    public PlanePyramidFactory getFactory() {
        return factory;
    }

    public PlanePyramid getHttpPlanePyramid(String pyramidConfiguration) throws Exception {
        return getHttpPlanePyramid(pyramidConfiguration, false);
    }

    public PlanePyramid getHttpPlanePyramid(String pyramidConfiguration, boolean savingMemoryMode)
        throws Exception
    {
        Objects.requireNonNull(pyramidConfiguration, "Null pyramidConfiguration argument");
        if (!POOL_ENABLED) {
            return factory.newPyramid(pyramidConfiguration);
        }
        synchronized (pool) {
            PlanePyramid pyramid = pool.get(pyramidConfiguration);
            if (pyramid != null) {
                LOG.info("The pyramid has loaded from pool: " + pyramid);
                return pyramid;
            }
            pyramid = factory.newPyramid(pyramidConfiguration);
            if (savingMemoryMode) {
                // So, this request will not lead to allocating memory in pool;
                // but, maybe, it will do some parallel non-saving-memory request
                LOG.info("New pyramid has been created, but NOT saved in pool: " + pyramid);
            } else {
                LOG.info("New pyramid has been created and saved in pool: " + pyramid);
                pool.put(pyramidConfiguration, pyramid);
            }
            return pyramid;
        }
    }

    public boolean removeHttpPlanePyramid(String pyramidConfiguration) {
        if (!POOL_ENABLED) {
            return false;
        }
        synchronized (pool) {
            final PlanePyramid pyramid = pool.remove(pyramidConfiguration);
            pyramid.freeResources();
            LOG.info("Saving memory: freeing and removing " + pyramid);
            return pyramid != null;
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
