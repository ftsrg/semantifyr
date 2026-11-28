/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import java.util.Arrays;
import java.util.Objects;
import org.eclipse.emf.common.util.URI;

public class UriUtils {

    public static boolean startsWith(URI uri, URI prefix) {
        if (!prefix.isPrefix()) {
            throw new IllegalArgumentException("Prefix is a non-prefix uri");
        }

        //noinspection StringEquality
        if (uri.scheme() != prefix.scheme()
                || uri.authority() != prefix.authority()
                || uri.device() != prefix.device()
                || uri.hasAbsolutePath() != prefix.hasAbsolutePath()) {
            return false;
        }

        if (uri.segmentCount() < prefix.segmentCount()) {
            return false;
        }

        // segments must be equal, except the last, which is the EMPTY_SEGMENT, since it is prefix
        for (int i = 0; i < prefix.segmentCount() - 1; i++) {
            //noinspection StringEquality
            if (uri.segment(i) != prefix.segment(i)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isSameFile(URI uri, URI other) {
        return uri.isFile()
                && other.isFile()
                && Objects.equals(uri.device(), other.device())
                && Arrays.equals(uri.segments(), other.segments());
    }
}
