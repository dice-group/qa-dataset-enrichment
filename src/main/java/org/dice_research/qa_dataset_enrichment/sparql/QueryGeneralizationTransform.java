package org.dice_research.qa_dataset_enrichment.sparql;

import java.util.List;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.TransformCopy;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.algebra.op.OpPath;
import org.apache.jena.sparql.algebra.op.OpSequence;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.TriplePath;
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

    @Override public Op transform(OpBGP op) { return contains(op, target) ? insert(op) : op; }

    @Override public Op transform(OpPath op) { return contains(op, target) ? insert(op) : op; }

    private boolean contains(OpBGP opBGP, Node node) {
        for (Triple t : opBGP.getPattern()) {
            if (t.getSubject().equals(node) || t.getPredicate().equals(node) || t.getObject().equals(node)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(OpPath opPath, Node node) {
        // TODO: handle properties in path as well
        TriplePath tp = opPath.getTriplePath();
        if (tp.getSubject().equals(node) || tp.getObject().equals(node)) {
            return true;
        }
        return false;
    }

    private Op insert(Op op) {
        BasicPattern pattern = new BasicPattern();
        for (Triple t : triples) {
            pattern.add(t);
        }
        return OpFilter.filterDirect(ExprList.create(filters), OpSequence.create(op, new OpBGP(pattern)));
    }
}
