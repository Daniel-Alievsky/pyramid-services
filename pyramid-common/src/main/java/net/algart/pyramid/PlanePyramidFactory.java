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

import java.util.Collections;
import java.util.List;

public interface PlanePyramidFactory {
    /**
     * <p>Customize behaviour the factory according the passed object.
     * The argument <tt>factoryConfiguration</tt> may be, for example, the name of class
     * of some sub-factory class, clarifying the method of creating pyramids,
     * or some JSON object with additional settings, describing some common
     * features of all created pyramids, etc.</p>
     *
     * <p>This method may do nothing if this factory always creates only one kind of pyramids.</p>
     *
     * <p>This method should be called immediately after creating this object.</p>
     *
     * @param factoryConfiguration some object helping to create pyramids.
     */
    void initializeConfiguration(Object factoryConfiguration) throws Exception;
    // Note: generics for the type of the argument is not convenient here.
    // At the point, where we are configuring this factory, we usually do not know the class of this argument;
    // it can be create via reflection, for example, from some properties or configuration files.


    /**
     * <p>Creates new remote pyramid.</p>
     *
     * <p>The <tt>pyramidConfiguration</tt> argument is usually JSON. Typical example:</p>
     *
     * <pre>
     *     {
     *         "path": "/pathogensRoot/Users/root/XXXXX<i>(project_id)</i>/image01/p-XXXX<i>(image-id)</i>",
     *         "renderer": {"format": "jpeg"}
     *     }
     * </pre>
     *
     * <p>Note: the <tt>pyramidConfiguration</tt> string must be an unique pyramid digest,
     * that must be different for different pyramids.
     * We recommend to use the same value for identical actual pyramids with identical data and behaviour
     * (results of other methods), for example, when the same pyramid on the disk is accessed
     * via different instances of this class.</p>
     *
     * @param pyramidConfiguration all information about the data source and parameters.
     * @return new remote pyramid.
     * @see PlanePyramid#pyramidConfiguration()
     */
    PlanePyramid newPyramid(String pyramidConfiguration) throws Exception;

    /**
     * <p>Returns the list of files, folders or other resources, containing the pyramid data, if the "main"
     * file pyramid file or other resource is located at <tt>pyramidDataPath</tt>.
     * (Usually it is some file in the folder, specified by "path" value in <tt>pyramidConfiguration</tt>
     * JSON, passed to {@link #newPyramid(String)} method.)</p>
     *
     * <p>For single-file formats, this method should returns an empty list (default behaviour).
     * For many formats, returns one folder with the same name without extension or something similar.</p>
     *
     * <p>Note: this method must be called <i>after</i> {@link #initializeConfiguration(Object)},
     * in other case <tt>IllegalStateException</tt> can be thrown.</p>
     *
     * @param pyramidDataPath some file or other resource, indicating the pyramid.
     * @return the list of additional files, folders or other resources, containing the data of this pyramid.
     */
    default List<String> accompanyingResources(String pyramidDataPath) {
        return Collections.emptyList();
    }
}
