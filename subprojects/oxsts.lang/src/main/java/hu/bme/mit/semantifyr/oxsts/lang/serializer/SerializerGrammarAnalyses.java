/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.serializer;

import com.google.inject.Inject;
import org.eclipse.xtext.Grammar;
import org.eclipse.xtext.IGrammarAccess;
import org.eclipse.xtext.serializer.analysis.IGrammarConstraintProvider;
import org.eclipse.xtext.serializer.analysis.IGrammarConstraintProvider.IConstraint;
import org.eclipse.xtext.serializer.analysis.IGrammarConstraintProvider.IFeatureInfo;
import org.eclipse.xtext.serializer.analysis.ISyntacticSequencerPDAProvider;
import org.eclipse.xtext.serializer.analysis.ISyntacticSequencerPDAProvider.ISynAbsorberState;
import org.eclipse.xtext.serializer.analysis.ISyntacticSequencerPDAProvider.ISynTransition;
import org.eclipse.xtext.serializer.analysis.SerializationContextMap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

// Xtext builds these on first use into shared singletons and grammar adapters without synchronization, and publishes some before filling them
public class SerializerGrammarAnalyses {

    @Inject
    public SerializerGrammarAnalyses(
            IGrammarAccess grammarAccess,
            IGrammarConstraintProvider constraintProvider,
            ISyntacticSequencerPDAProvider syntacticSequencerProvider
    ) {
        Grammar grammar = grammarAccess.getGrammar();
        buildConstraints(constraintProvider.getConstraints(grammar));
        buildSyntacticSequencerPDAs(syntacticSequencerProvider.getSyntacticSequencerPDAs(grammar));
    }

    private static void buildConstraints(SerializationContextMap<IConstraint> constraints) {
        constraints.get(null);
        for (var entry : constraints.values()) {
            IConstraint constraint = entry.getValue();
            constraint.hashCode();
            for (IFeatureInfo feature : constraint.getFeatures()) {
                if (feature != null) {
                    feature.getAssignments();
                }
            }
        }
    }

    private static void buildSyntacticSequencerPDAs(SerializationContextMap<ISynAbsorberState> syntacticSequencerPDAs) {
        syntacticSequencerPDAs.get(null);
        Set<ISynAbsorberState> visited = new HashSet<>();
        Deque<ISynAbsorberState> pending = new ArrayDeque<>();
        for (var entry : syntacticSequencerPDAs.values()) {
            pending.push(entry.getValue());
        }
        while (!pending.isEmpty()) {
            ISynAbsorberState state = pending.pop();
            if (!visited.add(state)) {
                continue;
            }
            for (ISynTransition transition : state.getOutTransitions()) {
                transition.getAmbiguousSyntaxes();
                transition.involvesUnassignedTokenRuleCalls();
                pending.push(transition.getTarget());
            }
        }
    }

}
