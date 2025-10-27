/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import com.google.inject.Inject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.util.UriExtensions;

import java.nio.file.Path;

public class ResourceUriProvider {

    @Inject
    protected UriExtensions uriExtensions;

    // To be consistent with the LSP server implementation we need to ensure our URIs have an empty authority
    public URI createFileUri(Path path) {
        var fileUri = URI.createFileURI(path.toString());
        return uriExtensions.withEmptyAuthority(fileUri);
    }

}
