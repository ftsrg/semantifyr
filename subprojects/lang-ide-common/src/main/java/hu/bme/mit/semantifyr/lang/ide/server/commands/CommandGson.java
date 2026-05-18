/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hu.bme.mit.semantifyr.lang.ide.wire.DurationTypeAdapter;
import java.time.Duration;

public final class CommandGson {

    public static final Gson INSTANCE = configure(new GsonBuilder()).create();

    public static GsonBuilder configure(GsonBuilder builder) {
        return builder.registerTypeAdapter(Duration.class, new DurationTypeAdapter());
    }

    private CommandGson() {}
}
