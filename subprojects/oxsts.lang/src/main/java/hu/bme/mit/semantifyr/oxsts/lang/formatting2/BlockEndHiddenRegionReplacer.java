/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.formatting2;

import java.util.List;
import org.eclipse.xtext.formatting2.IHiddenRegionFormatting;
import org.eclipse.xtext.formatting2.ITextReplacer;
import org.eclipse.xtext.formatting2.internal.HiddenRegionReplacer;
import org.eclipse.xtext.formatting2.internal.WhitespaceReplacer;
import org.eclipse.xtext.formatting2.regionaccess.IHiddenRegion;

@SuppressWarnings("restriction")
public class BlockEndHiddenRegionReplacer extends HiddenRegionReplacer {

    public BlockEndHiddenRegionReplacer(IHiddenRegion region, IHiddenRegionFormatting formatting) {
        super(region, formatting);
    }

    // Xtext dedents at the first line break, which pulls a comment on the last line of a block out to the brace.
    @Override
    protected void applyIndentation(List<ITextReplacer> replacers, IHiddenRegionFormatting separator) {
        var increase = getFormatting().getIndentationIncrease();
        var decrease = getFormatting().getIndentationDecrease();
        if (increase == null && decrease != null && replacers.getLast() instanceof WhitespaceReplacer last) {
            last.getFormatting().setIndentationDecrease(decrease);
        } else {
            super.applyIndentation(replacers, separator);
        }
    }
}
