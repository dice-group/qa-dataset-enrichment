package org.dice_research.qa_dataset_enrichment.sparql;

import java.util.List;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.TransformCopy;
import org.apache.jena.sparql.algebra.op.OpProject;
import org.apache.jena.sparql.core.Var;

/**
 * Replace all SPARQL projections variables with the specified variables
 */
public class ProjectVariableTransform extends TransformCopy {
    private List<Var> vars;

    public ProjectVariableTransform(List<Var> vars) {
        this.vars = vars;
    }

    @Override public Op transform(OpProject opProject, Op subOp) {
        return new OpProject(subOp, vars);
    }
}
