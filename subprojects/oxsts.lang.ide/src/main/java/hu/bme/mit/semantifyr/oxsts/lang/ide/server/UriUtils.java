/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import org.eclipse.emf.common.util.URI;

public class UriUtils {

    public static boolean startsWith(URI uri, URI prefix) {
        if (!prefix.isPrefix()) {
            throw new IllegalArgumentException("Prefix is a non-prefix uri");
        }

        //noinspection StringEquality   XText URI strings are 'interned'; same strings will have the same identity
        if (uri.scheme() != prefix.scheme() || uri.authority() != prefix.authority() || uri.device() != prefix.device() || uri.hasAbsolutePath() != prefix.hasAbsolutePath()) {
            // Totally different uris
            return false;
        }

        if (uri.segmentCount() < prefix.segmentCount()) {
            // since prefix ends with an empty, for uri to start with it, it must have at least as many segments
            return false;
        }

        // segments must be equal, except the last, which is the EMPTY_SEGMENT, since it is prefix
        for (int i = 0; i < prefix.segmentCount() - 1; i++) {
            //noinspection StringEquality   XText URI strings are 'interned'; same strings will have the same identity
            if (uri.segment(i) != prefix.segment(i)) {
                return false;
            }
        }

        return true;
    }

}
