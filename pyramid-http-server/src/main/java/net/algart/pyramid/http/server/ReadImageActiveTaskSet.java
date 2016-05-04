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

package net.algart.pyramid.http.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

final class ReadImageActiveTaskSet {
    private final Set<ReadImageTask> activeTasks = new LinkedHashSet<>();

    void addTask(ReadImageTask task) {
        synchronized (activeTasks) {
            activeTasks.add(task);
        }
    }

    void removeTask(ReadImageTask task) {
        synchronized (activeTasks) {
            activeTasks.remove(task);
        }
    }

    void cleanObsoleteTasks() {
        final Collection<ReadImageTask> tasks;
        synchronized (activeTasks) {
            tasks = new ArrayList<>(activeTasks);
        }
//        System.out.printf("Checking %d tasks to cancel...%n", tasks.size());
        for (ReadImageTask task : tasks) {
            task.cancelIfObsolete();
        }
    }
}
