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

var host = null;
var mapTagName = null;
var macroTagName = null;
var informationTagName = null;
var controlTagName = null;
var zeroLevelObjective = null;

/**
 * The variable openLayersMap contains current OpenLayers.Map, if we have some active pyramid,
 * or null in other case.
 */
var openLayersMap = null;

/**
 * The variable currentPyramidInfo contains current PlanePyramidInformation as JSON, if we have some active pyramid,
 * or null in other case.
 */
var currentPyramidInfo = null;

/**
 * Specifies the server and the names (id attribute) of HTML tags (usually <div...>)
 * used for working with OpenLayers pyramid.
 *
 * @param newHost               server host name (for example, "localhost")
 * @param newMapTagName         the name of OpenLayers main container (usually empty <div id=(this name))/>)
 * @param newMacroTagName       the name of container for placing there the macro image; may be null
 * @param newInformationTagName the name of container for showing full JSON pyramid information;
 *                              may be null (for debugging needs)
 * @param newControlTagName     the name of some control panel, the visibility of which will be turned off
 *                              while changing the current pyramid; may be null
 */
function inititialize(newHost, newMapTagName, newMacroTagName, newInformationTagName, newControlTagName) {
    host = newHost;
    mapTagName = newMapTagName;
    macroTagName = newMacroTagName;
    informationTagName = newInformationTagName;
    controlTagName = newControlTagName;
}

/**
 * Sends a request to the server to load the pyramid and get its parameters;
 * when the server answers, it initializes new pyramid.
 *
 * Note that in professional version the port should be detected automatically by pyramidId
 * in the server-side script (ASP, JSP, ...) by analysis of /pp-control folder and formatName from .pp.json
 *
 * @param newPyramidId pyramidId (name of subfolder in /pp-links directory)
 * @param newPort      port for opening this pyramid
 */
function setPyramid(newPyramidId, newPort) {
    if (openLayersMap != null) {
        // it is not the 1st call
        openLayersMap = null;
        currentPyramidInfo = null;
        document.getElementById(mapTagName).innerHTML = "";
        if (controlTagName != null) {
            document.getElementById(controlTagName).style.visibility = "hidden";
        }
        if (macroTagName != null) {
            document.getElementById(macroTagName).style.visibility = "hidden";
        }
        if (informationTagName != null) {
            document.getElementById(informationTagName).innerHTML = "Changing pyramid to " + newPyramidId + "...";
        }
    }
    setTimeout("requestPyramid('" + newPyramidId + "', " + newPort + ")", 100);
    // setTimeout is necessary to allow browser to really visually hide "control" element
}


/**
 * Changes the magnification of the current pyramid. 
 * @param newObjective new magnification (5, 10, 20, ...)
 */
function changeObjective(newObjective) {
    if (openLayersMap == null) {
        // openLayersMap is not ready yet
        return;
    }
    var objective = zeroLevelObjective;
    var level = openLayersMap.getNumZoomLevels() - 1;
    while (objective > newObjective && level > 0) {
        objective /= 2;
        level--;
    }
    openLayersMap.zoomTo(level);
}


// This function is private; it is called by setPyramid.
function requestPyramid(newPyramidId, newPort) {
    var xhr = new XMLHttpRequest();
    currentPyramidId = newPyramidId;
    currentPort = newPort;
    urlStart = 'http://' + host + ':' + newPort + '/pp-';
    xhr.open('GET', urlStart + 'information?pyramidId=' + newPyramidId, true);
    xhr.send();
    xhr.onreadystatechange = function () {
        if (xhr.readyState != 4) {
            return;
        }
        var result = "";
        if (xhr.status != 200) {
            result = "Error: cannot read pyramid information!</br>";
            currentPyramidInfo = null; // stays null
        } else {
            currentPyramidInfo = JSON.parse(xhr.responseText);
            result = "<p>Detected pyramid information:</p>";
            result += "<pre>" + JSON.stringify(currentPyramidInfo, null, "  ") + "</pre>";
            zeroLevelObjective = currentPyramidInfo.magnification == null ? 20 : currentPyramidInfo.magnification;
            // - current versions of server, based on LOCI and other free libraries,
            // does not support currentPyramidInfo.magnification, so we will use default value 20
            initOpenLayers();
        }
        if (informationTagName != null) {
            document.getElementById(informationTagName).innerHTML = result;
        }
    }
}

// This function is private; it is called when the server replies to XMLHttpRequest
// and returns the parameters of loaded pyramid.
function initOpenLayers() {
// The following line allows to emulate TMS intead of Zoomify, but not correct for boundary tiles:
//        OpenLayers.Layer.Zoomify.prototype.getURL = getMyTmsUrl;

    var imSize = new OpenLayers.Size(currentPyramidInfo.zeroLevelDimX, currentPyramidInfo.zeroLevelDimY);
    var zoomify = new OpenLayers.Layer.Zoomify("Layer 1",
        "http://localhost:" + currentPort + "/pp-zoomify/" + currentPyramidId + "/", imSize);
    /* Map with raster coordinates (pixels) from Zoomify image */
    var options = {
        maxExtent: new OpenLayers.Bounds(0, 0, currentPyramidInfo.zeroLevelDimX, currentPyramidInfo.zeroLevelDimY),
        maxResolution: Math.pow(2, zoomify.numberOfTiers - 1),
        numZoomLevels: zoomify.numberOfTiers,
        units: 'pixels'
    };

    openLayersMap = new OpenLayers.Map(mapTagName, options);
    openLayersMap.addLayer(zoomify);
    openLayersMap.setBaseLayer(zoomify);
    openLayersMap.addControl(new OpenLayers.Control.LayerSwitcher());
    openLayersMap.addControl(new OpenLayers.Control.OverviewMap({
        minRatio: 0,
        maxRatio: openLayersMap.maxResolution,
        mapOptions: options
    }));
    openLayersMap.zoomToMaxExtent();
    if (controlTagName != null) {
        document.getElementById(controlTagName).style.visibility = "visible";
    }
    if (macroTagName != null) {
        var macroTag = document.getElementById(macroTagName);
        macroTag.style.visibility = "visible";
        macroTag.innerHTML = '<img src="'
            + 'http://localhost:' + currentPort + '/pp-read-special-image?pyramidId=' + currentPyramidId
            + '&specialImageName=WHOLE_SLIDE&width=' + macroTag.offsetWidth + '"/>';
    }
}

// The function getMyTmsUrl allows to get more control over the syntax of requests to the server.
// Only for debugging needs. Usually you don't need to use it.
function getMyTmsUrl(bounds) {
    var res = this.map.getResolution();
    var x = Math.round((bounds.left - this.tileOrigin.lon) / (res * this.tileSize.w));
    var y = Math.round((this.tileOrigin.lat - bounds.top) / (res * this.tileSize.h));
    var z = this.map.getZoom();

    return urlStart + "tms/" + currentPyramidId + "/" + z + "/" + x + "/" + y + ".jpg";
}



