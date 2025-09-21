/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import java.nio.file.Path;
import java.util.List;

public class AdditionalLibrary extends PathLibrary {

    public AdditionalLibrary() {
        super(Path.of(""));
    }

    public void setLibraryPaths(List<Path> libraryPaths) {
//        this.libraryPaths = libraryPaths;
    }

}
