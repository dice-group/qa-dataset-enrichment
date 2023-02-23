package org.dice_research.qa_dataset_enrichment.sparql;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.TransformCopy;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpPath;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.TriplePath;

/**
 * Replace all occurrences of one node in the query with another
 */
public class NodeReplaceTransform extends TransformCopy {
    private Node target;
    private Node replacement;

    public NodeReplaceTransform(Node target, Node replacement) {
        this.target = target;
        this.replacement = replacement;
    }

    @Override public Op transform(OpBGP opBGP) {
        BasicPattern pattern = new BasicPattern();
        for (Triple t : opBGP.getPattern()) {
            pattern.add(Triple.create(replace(t.getSubject()), replace(t.getPredicate()), replace(t.getObject())));
        }
        return new OpBGP(pattern);
    }
    @Override public Op transform(OpPath opPath) {
        // TODO: handle properties in path as well
        TriplePath tp = opPath.getTriplePath();
        return new OpPath(new TriplePath(replace(tp.getSubject()), tp.getPath(), replace(tp.getObject())));
    }

    private Node replace(Node node) {
        return node.equals(target) ? replacement : node;
    }
}
