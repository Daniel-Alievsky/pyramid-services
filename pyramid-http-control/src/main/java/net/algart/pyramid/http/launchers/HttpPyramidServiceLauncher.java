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

package net.algart.pyramid.http.launchers;

import net.algart.pyramid.http.api.HttpPyramidConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.*;

public class HttpPyramidServiceLauncher {
    private final HttpPyramidConfiguration configuration;
    private final Map<String, Process> runningProcesses;

    public HttpPyramidServiceLauncher(HttpPyramidConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
        this.runningProcesses = new LinkedHashMap<>();
        for (String groupId : configuration.getProcesses().keySet()) {
            runningProcesses.put(groupId, null);
        }
    }

    public synchronized void startServices() {

        //TODO!! don't check that processes are alive; good for starting Windows service
    }

    public synchronized void restartServices(boolean restartAliveServices) {
        //TODO!! check if a service is alive (if not, repeat 3 times)
        //TODO!! if alive and restartAliveServices, call restartProcess, else startProcess
        //TODO!! start each service by a separate thread (don't delay others due to one bad services)
    }

    public synchronized void finishServices() {
        //TODO!! try to finish normally; if we have references to processes, also kill processes
    }

    public synchronized void restartProcess(String groupId) {
        finishProcess(groupId);
        startProcess(groupId);
    }

    public synchronized void finishProcess(String groupId) {
        //TODO!! finish it via HTTP command, litle wait
        //TODO!! if we have reference to the process in Map<String groupId, java.lang.Process), kill it and little wait
    }

    public synchronized void startProcess(String groupId) {
        //TODO!! try to start process 3 times (in a case of non-zero exit code) with delays
    }

    public synchronized void startProcess(HttpPyramidConfiguration.Process process) throws FileNotFoundException {
        if (runningProcesses.containsKey(process.getGroupId())) {
            throw new IllegalStateException("The process with groupId="
                + process.getGroupId() + " is already registered");
        }
        final Path javaPath = HttpPyramidConfiguration.getJavaExecutable(HttpPyramidConfiguration.getCurrentJREHome());
        List<String> command = new ArrayList<>();
        command.add(javaPath.toAbsolutePath().toString());
        StringBuilder cp = new StringBuilder();
        for (String p : process.classPath(process.hasWorkingDirectory())) {
            if (cp.length() > 0) {
                cp.append(File.pathSeparatorChar);
            }
            cp.append(p);
        }
        //TODO!! use process.xmx etc.
    }
}
