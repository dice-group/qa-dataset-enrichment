package org.dice_research.qa_dataset_enrichment;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpWalker;
import org.dice_research.qa_dataset_enrichment.data.Mention;
import org.dice_research.qa_dataset_enrichment.data.Question;
import org.dice_research.qa_dataset_enrichment.sparql.URICountingVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryEntityParser implements Function<Question, Question> {
    private static Logger logger = LoggerFactory.getLogger(QueryEntityParser.class);
    public ArrayList<Question> questions = new ArrayList<>();
    public long foundEntities = 0;
    public ArrayList<Resource> entities = new ArrayList<>();

    @Override
    public Question apply(Question question) {
        Query query = QueryFactory.create(question.query);
        Op op = Algebra.compile(query);
        URICountingVisitor counter = new URICountingVisitor();
        OpWalker.walk(op, counter);
        // FIXME create new Question object
        question.query_ent_mentions = counter.getCounts().stream().map(uri -> new Mention(uri)).collect(Collectors.toList());
        return question;
    }
}
