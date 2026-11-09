/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.Position;

public record MarkedText(String text, List<Position> positions) {

    private static final String MARKER = "<|>";

    public static MarkedText of(String markedText) {
        var text = new StringBuilder();
        var positions = new ArrayList<Position>();
        var line = 0;
        var character = 0;
        var index = 0;
        while (index < markedText.length()) {
            if (markedText.startsWith(MARKER, index)) {
                positions.add(new Position(line, character));
                index += MARKER.length();
            } else {
                var current = markedText.charAt(index);
                text.append(current);
                if (current == '\n') {
                    line++;
                    character = 0;
                } else {
                    character++;
                }
                index++;
            }
        }
        return new MarkedText(text.toString(), List.copyOf(positions));
    }

    public Position position() {
        if (positions.size() != 1) {
            throw new IllegalStateException("Expected one marker but found " + positions.size());
        }
        return positions.getFirst();
    }
}
