package org.dice_research.qa_dataset_enrichment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
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
import org.apache.jena.rdf.model.Literal;
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
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpDistinct;
import org.apache.jena.sparql.algebra.op.OpExtend;
import org.apache.jena.sparql.algebra.op.OpProject;
import org.apache.jena.sparql.algebra.op.OpUnion;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.E_Lang;
import org.apache.jena.sparql.expr.E_LangMatches;
import org.apache.jena.sparql.expr.E_OneOf;
import org.apache.jena.sparql.expr.E_Str;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.nodevalue.NodeValueString;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.dice_research.qa_dataset_enrichment.data.Question;
import org.dice_research.qa_dataset_enrichment.data.WDT;
import org.dice_research.qa_dataset_enrichment.sparql.NodeReplaceTransform;
import org.dice_research.qa_dataset_enrichment.sparql.ProjectVariableTransform;
import org.dice_research.qa_dataset_enrichment.sparql.QueryGeneralizationTransform;
import org.dice_research.qa_dataset_enrichment.sparql.URICountingVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuestionMutator {
    private static Logger logger = LoggerFactory.getLogger(QuestionMutator.class);
    private long size = 0;
    public ArrayList<Question> questions = new ArrayList<>();
    public long foundEntities = 0;
    public ArrayList<Resource> entities = new ArrayList<>();

    public void mutate(String question, String sparql, Function<String,Model> ner, RDFConnection con) {
        logger.debug("Input query: {}", sparql.replaceFirst(".*\\b(?=SELECT\\b|ASK\\b)", ""));
        Query query = QueryFactory.create(sparql);
        Op op = Algebra.compile(query);

        // FIXME: for some queries there is no OpProject
        if (!(op instanceof OpDistinct || op instanceof OpProject)) {
            // list of variables would be replaced later
            op = new OpProject(op, List.of());
        }
        if (!(op instanceof OpDistinct)) {
            op = new OpDistinct(op);
        }

        logger.debug("Input query: {}", op);

        URICountingVisitor counter = new URICountingVisitor();
        OpWalker.walk(op, counter);
        Collection<String> urisInQuery = counter.getCounts();

        final Model nerResults = ner.apply(question);
        if (nerResults == null) {
            logger.warn("No results from NER: {}", question);
            return;
        }

        final ResIterator entityIter = nerResults.listResourcesWithProperty(RDF.type, Voc.pNifPhrase);
        if (!entityIter.hasNext()) {
            logger.warn("No entities recognized: {}", question);
            return;
        }
        while (entityIter.hasNext()) {
            final Resource r = entityIter.next();
            final int beginIndex = r.getProperty(Voc.pNifBegin).getObject().asLiteral().getInt();
            final int endIndex = r.getProperty(Voc.pNifEnd).getObject().asLiteral().getInt();
            for (Iterator<RDFNode> objectIt = nerResults.listObjectsOfProperty(r, Voc.pItsrdfTaIdentRef); objectIt.hasNext(); ) {
                final RDFNode entity = objectIt.next();
                final String entityURI = entity.asResource().getURI();
                foundEntities++;
                if (urisInQuery.contains(entityURI)) {
                    entities.add(entity.asResource());
                    List<RDFNode> types = nerResults.listObjectsOfProperty(r, Voc.pItsrdfTaClassRef).toList();
                    if (types.size() == 0) {
                        types = fetchResourceTypes(entity, con);
                        if (types.size() == 0) {
                            logger.warn("No type information from NER and KG: {}", entityURI);
                            System.exit(0);
                        }
                    }
                    if (types.size() != 0) {
                        logger.debug("Mutation source: {} (a {})", entity, types);
                        Op newOp = generalizeQuery(op, entity, types);
                        Query newQuery = OpAsQuery.asQuery(newOp);
                        logger.debug("Generalized query: {}", newQuery);
                        try (QueryExecution qe = con.query(newQuery)) {
                            ResultSet rs = qe.execSelect(); // fixme
                            while (rs.hasNext()) {
                                QuerySolution qs = rs.next();
                                assert(qs.contains("s"));
                                assert(qs.contains("label"));
                                Resource res_s = qs.getResource("s");
                                if (!res_s.equals(entity.asResource())) {
                                    Literal lit_label = qs.getLiteral("label");
                                    logger.debug("Mutation target: {} {}", res_s, lit_label);
                                    Question q = new Question();
                                    q.question = question.substring(0, beginIndex) + lit_label.getString() + question.substring(endIndex);
                                    q.query = OpAsQuery.asQuery(Transformer.transform(new NodeReplaceTransform(entity.asNode(), res_s.asNode()), op)).toString();

                                    // FIXME: this part seems to be not parsed properly
                                    if (sparql.matches(".*\\bASK\\b.*")) {
                                        q.query = q.query.replaceFirst("\\bSELECT\\b[^{]+", "ASK ");
                                    }

                                    questions.add(q);
                                    ++size;
                                }
                            }
                            if (size != 0) {
                                logger.debug("New questions generated: {}", size);
                            } else {
                                logger.warn("No questions generated: {}", question);
                            }
                        }
                    }
                    /*
                } else {
                    final String text = r.getProperty(Voc.pNifAnchorOf).getObject().asLiteral().getString();
                    logger.warn("Entity recognized in text ({}) but missing in query: {}", text, entityURI);
                    */
                }
            }
        }
    }

    public long size() {
        return size;
    }

    /**
     * Fetch resource's types from a SPARQL endpoint
     *
     * @param resource the resource
     * @param con RDFConnection
     * @return list of resources
     */
    private List<RDFNode> fetchResourceTypes(RDFNode resource, RDFConnection con) {
        List<RDFNode> types = new ArrayList<>();
        Var var_t = Var.alloc("type");
        BasicPattern patternRDF = new BasicPattern();
        patternRDF.add(Triple.create(resource.asNode(), RDF.type.asNode(), var_t));
        BasicPattern patternWD = new BasicPattern();
        patternWD.add(Triple.create(resource.asNode(), WDT.instanceOf.asNode(), var_t));
        Query typeQuery = OpAsQuery.asQuery(new OpProject(new OpUnion(new OpBGP(patternRDF), new OpBGP(patternWD)), List.of(var_t)));
        try (QueryExecution qe = con.query(typeQuery)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                Resource t = rs.next().getResource("type");
                types.add(t);
            }
        }
        return types;
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
        Var var_tp = Var.alloc("tp");
        Var var_t = Var.alloc("t");
        Var var_labelLang = Var.alloc("labelLang");
        Var var_label = Var.alloc("label");
        // replace the recognized entity's URI with a variable (?s)
        Op newOp = Transformer.transform(new NodeReplaceTransform(entity.asNode(), var_s), op);
        // add that variable to the projection
        newOp = Transformer.transform(new ProjectVariableTransform(List.of(var_s, var_label)), newOp);
        newOp = Transformer.transform(new QueryGeneralizationTransform(
            // in all BGPs which contain ?s
            var_s,
            List.of(
                // add "?s ?tp ?t."
                Triple.create(var_s.asNode(), var_tp, var_t),
                // add "?s rdfs:label ?label."
                Triple.create(var_s.asNode(), RDFS.label.asNode(), var_labelLang)
            ),
            List.of(
                // add "FILTER ( ?tp IN (rdf:type, wdt:P31) )"
                new E_OneOf(new ExprVar(var_tp), ExprList.create(List.of(RDF.type, WDT.instanceOf).stream().map(this::toExpr).collect(Collectors.toList()))),
                // add "FILTER ( ?t IN (<type>, <type>, ...) )"
                new E_OneOf(new ExprVar(var_t), ExprList.create(types.stream().map(this::toExpr).collect(Collectors.toList()))),
                // add "langmatches ( ?label, ... )"
                new E_LangMatches(new E_Lang(new ExprVar(var_labelLang)), new NodeValueString("en"))
            )),
            newOp);
        OpProject project = (OpProject)((OpDistinct)newOp).getSubOp();
        newOp = new OpDistinct(new OpProject(OpExtend.extend(project.getSubOp(), var_label, new E_Str(new ExprVar(var_labelLang))), project.getVars()));
        return newOp;
    }

    private Expr toExpr(RDFNode node) {
        return NodeValue.makeNode(node.asNode());
    }
}
