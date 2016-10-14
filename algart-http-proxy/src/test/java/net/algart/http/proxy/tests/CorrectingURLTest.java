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

package net.algart.http.proxy.tests;

import net.algart.http.proxy.HttpProxy;
import net.algart.http.proxy.HttpServerAddress;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class CorrectingURLTest {
    public static void main(String[] args) throws MalformedURLException, URISyntaxException {

        URL url1 = new URL("https://%D1%82%D0%B5%D1%81%D1%82@localhost/asd/asd?123%20456#re%20ff");
        showURL(url1);
        System.out.println();

        final URL url2 = new URL("http", "mydomain.COM", -1, "/asd/?query");
        showURL(url2);
        System.out.println();

        final URL url3 = new URL("HTtp://даниэль:password@mydomain.com:123/asd/?query#reff");
        showURL(url3);
        System.out.println();

        final URI uri4 = new URI("https://тест@localhost/путь/?запрос#якорь");
        showURI(uri4);
        System.out.println();

        final URI uri5 = new URI("/asd/?query");
        showURI(uri5);
        System.out.println();
    }

    private static void showURL(URL url) throws URISyntaxException {
        System.out.println("Full URL: \"" + url + "\"");
        System.out.println("Protocol: \"" + url.getProtocol() + "\"");
        System.out.println("UserInfo: \"" + url.getUserInfo() + "\"");
        System.out.println("Host:     \"" + url.getHost() + "\"");
        System.out.println("Port:     \"" + url.getPort() + "\"");
        System.out.println("File:     \"" + url.getFile() + "\"");
        System.out.println("Ref:      \"" + url.getRef() + "\"");
        final URI uri = url.toURI();
        System.out.println("URI:");
        showURI(uri);
        final URI uriCopy = new URI(
            uri.getScheme(),
            uri.getUserInfo(),
            // - if user info contains escaped characters, the result will not be a copy;
            // but toASCIIString will be correct
            uri.getHost(),
            uri.getPort(),
            uri.getPath(),
            uri.getQuery(),
            uri.getFragment());
        System.out.println("Copy of this URI:");
        showURI(uriCopy);
        showCorrectionByProxy(uri.toString(), "http", "localhost", 80, "localhost", 123);
        showCorrectionByProxy(uri.toString(), "https", "mydomain.com", 82, "mylocalhost.com", 90);
    }

    private static void showURI(URI uri) {
        System.out.println("  Full URI:    \"" + uri + "\"");
        System.out.println("  Ascii URI:   \"" + uri.toASCIIString() + "\"");
        System.out.println("  Schema:      \"" + uri.getScheme() + "\"");
        System.out.println("  RawUserInfo: \"" + uri.getRawUserInfo() + "\"");
        System.out.println("  UserInfo:    \"" + uri.getUserInfo() + "\"");
        System.out.println("  Host:        \"" + uri.getHost() + "\"");
        System.out.println("  Port:        \"" + uri.getPort() + "\"");
        System.out.println("  RawPath:     \"" + uri.getRawPath() + "\"");
        System.out.println("  Path:        \"" + uri.getPath() + "\"");
        System.out.println("  RawQuery:    \"" + uri.getRawQuery() + "\"");
        System.out.println("  Query:       \"" + uri.getQuery() + "\"");
        System.out.println("  Fragment:    \"" + uri.getFragment() + "\"");
        System.out.println("  RawFragment: \"" + uri.getRawFragment() + "\"");
    }

    private static void showCorrectionByProxy(
        String location,
        String requestScheme,
        String requestHost,
        int requestPort,
        String serverHost,
        int serverPort)
    {
        String corrected = HttpProxy.correctLocationFor3XXResponse(
            location, requestScheme, requestHost, requestPort,
            new HttpServerAddress(serverHost, serverPort));
        System.out.printf("Correction of %s, request %s://%s:%d, real server address %s:%d:%n  %s%n",
            location, requestScheme, requestHost, requestPort, serverHost, serverPort,
            corrected.equals(location) ? "NONE" : corrected);
    }
}
