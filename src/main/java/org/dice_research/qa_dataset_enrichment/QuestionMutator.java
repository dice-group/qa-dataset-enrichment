package org.dice_research.qa_dataset_enrichment;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aksw.fox.data.Voc;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.algebra.OpWalker;
import org.apache.jena.sparql.algebra.Transformer;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.E_Lang;
import org.apache.jena.sparql.expr.E_LangMatches;
import org.apache.jena.sparql.expr.E_OneOf;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.nodevalue.NodeValueString;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.dice_research.qa_dataset_enrichment.sparql.NodeReplaceTransform;
import org.dice_research.qa_dataset_enrichment.sparql.ProjectVariableTransform;
import org.dice_research.qa_dataset_enrichment.sparql.QueryGeneralizationTransform;
import org.dice_research.qa_dataset_enrichment.sparql.URICountingVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuestionMutator {
    private static Logger logger = LoggerFactory.getLogger(App.class);

    public QuestionMutator(String question, String sparql, Function<String,Model> ner, RDFConnection con) {
        Query query = QueryFactory.create(sparql);
        Op op = Algebra.compile(query);

        URICountingVisitor counter = new URICountingVisitor();
        OpWalker.walk(op, counter);
        Collection<String> urisInQuery = counter.getCounts();

        final Model nerResults = ner.apply(question);
        final ResIterator entityIter = nerResults.listResourcesWithProperty(RDF.type, Voc.pNifPhrase);
        if (!entityIter.hasNext()) {
            logger.warn("No entities recognized");
        }
        while (entityIter.hasNext()) {
            final Resource r = entityIter.next();
            final RDFNode entity = r.getProperty(Voc.pItsrdfTaIdentRef).getObject();
            final String entityURI = entity.asResource().getURI();
            if (urisInQuery.contains(entityURI)) {
                List<RDFNode> types = nerResults.listObjectsOfProperty(r, Voc.pItsrdfTaClassRef).toList();
                if (types.size() != 0) {
                    logger.info("Mutating question with regards to: {} ({})", entityURI, types);
                    Query newQuery = OpAsQuery.asQuery(generalizeQuery(op, entity, types));
                    try (QueryExecution qe = con.query(newQuery)) {
                        ResultSet rs = qe.execSelect(); // fixme
                        final int beginIndex = r.getProperty(Voc.pNifBegin).getObject().asLiteral().getInt();
                        final int endIndex = r.getProperty(Voc.pNifEnd).getObject().asLiteral().getInt();
                        int i = 0;
                        while (rs.hasNext()) {
                            QuerySolution qs = rs.next();
                            Resource res_s = qs.getResource("s");
                            if (!res_s.equals(entity.asResource())) {
                                String genQuestion = question.substring(0, beginIndex) + qs.getLiteral("label").getString() + question.substring(endIndex);
                                Query genQuery = OpAsQuery.asQuery(Transformer.transform(new NodeReplaceTransform(entity.asNode(), res_s.asNode()), op));
                                i++;
                            }
                        }
                        if (i != 0) {
                            logger.info("New questions generated: {}", i);
                        } else {
                            logger.warn("No questions generated");
                        }
                    }
                } else {
                    logger.warn("NER didn't provide type information for {}: {}", entityURI, nerResults);
                }
            } else {
                final String text = r.getProperty(Voc.pNifAnchorOf).getObject().asLiteral().getString();
                logger.warn("Entity recognized in text ({}) but missing in query: {}", text, entityURI);
            }
        }
    }

    /**
     * Modify the SPARQL query to find similar examples in KG
     *
     * @param op the original query
     * @param entity entity to replace
     * @param types list of allowed RDF:type values for entities
     * @return the new query
     */
    private Op generalizeQuery(Op op, RDFNode entity, List<RDFNode> types) {
        Var var_s = Var.alloc("s");
        Var var_t = Var.alloc("t");
        Var var_label = Var.alloc("label");
        // replace the recognized entity's URI with a variable (?s)
        Op newOp = Transformer.transform(new NodeReplaceTransform(entity.asNode(), var_s), op);
        // add that variable to the projection
        newOp = Transformer.transform(new ProjectVariableTransform(List.of(var_s, var_label)), newOp);
        newOp = Transformer.transform(new QueryGeneralizationTransform(
            // in all BGPs which contain ?s
            var_s,
            List.of(
                // add "?s a ?t."
                Triple.create(var_s.asNode(), RDF.type.asNode(), var_t.asNode()),
                // add "?s rdfs:label ?label."
                Triple.create(var_s.asNode(), RDFS.label.asNode(), var_label.asNode())
            ),
            List.of(
                // add "FILTER ( ?t IN (<type>, <type>, ...) )"
                new E_OneOf(new ExprVar(var_t), ExprList.create(types.stream().map(this::toExpr).collect(Collectors.toList()))),
                // add "langmatches ( ?label, ... )"
                new E_LangMatches(new E_Lang(new ExprVar(var_label)), new NodeValueString("en"))
            )),
            newOp);
        return newOp;
    }

    private Expr toExpr(RDFNode node) {
        return NodeValue.makeNode(node.asNode());
    }
}
