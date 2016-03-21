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

public interface PlanePyramidFactory {
    /**
     * <p>Customize behaviour the factory according the passed object.
     * The argument <tt>configuration</tt> may be, for example, an instance
     * of some sub-factory class, clarifying the method of creating pyramids,
     * or some JSON object with additional settings, describing some common
     * features of all created pyramids, etc.</p>
     *
     * <p>This method may do nothing if this factory always creates only one kind of pyramids.</p>
     *
     * <p>This method should be called immediately after creating this object.</p>
     *
     * @param configuration some object helping to create pyramids.
     */
    void initializeConfiguration(Object configuration);
    // Note: generics for the type of the argument is not convenient here.
    // At the point, where we are configuring this factory, we usually do not know the class of this argument;
    // it can be create via reflection, for example, from some properties or configuration files.


    /**
     * <p>Creates new remote pyramid. Typical example of <tt>configJson</tt>:</p>
     *
     * <pre>
     *     {
     *         "path": "\pathogensRoot\Users\root\XXXXX<i>(project_id)</i></project_id>\image01\p-XXXX<i>(image-id)</i>",
     *         "renderer": {"format": "jpeg"}
     *     }
     * </pre>
     *
     * <p>The argument <tt>configJson</tt> must be identical for pyramids with identical
     * {@link PlanePyramid#uniqueId()}</p>.
     *
     * @param configJson all information about the data source and parameters.
     * @return new remote pyramid.
     */
    PlanePyramid newPyramid(String configJson) throws Exception;
}
