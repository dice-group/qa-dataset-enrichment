package org.dice_research.qa_dataset_enrichment.sparql;

import java.util.Collection;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.OpVisitorBase;
import org.apache.jena.sparql.algebra.op.OpBGP;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Visitor which counts occurrences of URIs in the query
 */
public class URICountingVisitor extends OpVisitorBase {
    private Multiset<String> counts = HashMultiset.create();

    @Override public void visit(OpBGP opBGP) {
        for (Triple triple : opBGP.getPattern()) {
            visit(triple.getSubject());
            visit(triple.getPredicate());
            visit(triple.getObject());
        }
    }
    private void visit(Node node) {
        if (node.isURI()) {
            counts.add(node.getURI());
        }
    }

    public Collection<String> getCounts() {
        return counts;
    }
}
