package org.dice_research.qa_dataset_enrichment.sparql;

import java.util.List;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.TransformCopy;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprList;

public class QueryGeneralizationTransform extends TransformCopy {
    private Node target;
    private List<Triple> triples;
    private List<Expr> filters;

    public QueryGeneralizationTransform(Node target, List<Triple> triples, List<Expr> filters) {
        this.target = target;
        this.triples = triples;
        this.filters = filters;
    }

    @Override public Op transform(OpBGP opBGP) {
        if (contains(opBGP, target)) {
            BasicPattern pattern = new BasicPattern(opBGP.getPattern());
            for (Triple t : triples) {
                pattern.add(t);
            }
            return OpFilter.filterDirect(ExprList.create(filters), new OpBGP(pattern));
        } else {
            return opBGP;
        }
    }

    private boolean contains(OpBGP opBGP, Node node) {
        for (Triple t : opBGP.getPattern()) {
            if (t.getSubject().equals(node) || t.getPredicate().equals(node) || t.getObject().equals(node)) {
                return true;
            }
        }
        return false;
    }
}
