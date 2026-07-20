/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import com.google.gson.annotations.SerializedName;

public enum ArtifactsLocation {
    @SerializedName("temporary")
    TEMPORARY,
    @SerializedName("workspace")
    WORKSPACE
}
