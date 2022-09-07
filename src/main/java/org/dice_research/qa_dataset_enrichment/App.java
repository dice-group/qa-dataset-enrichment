package org.dice_research.qa_dataset_enrichment;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Function;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnection;
import org.dice_research.qa_dataset_enrichment.data.Question;
import org.dice_research.qa_dataset_enrichment.ner.Fox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generate learning examples for question answering based on the existing ones.
 */
public class App {
    private static Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws IOException, JsonParseException, MalformedURLException, ParseException {
        Options options = new Options();
        options.addOption("i", "input-file", true, "Input dataset file in JSON");
        options.addOption("f", "fox", true, "FOX API URL");
        CommandLineParser cmdParser = new DefaultParser();
        CommandLine cmd = cmdParser.parse(options, args);

        Function<String,Model> ner = new Fox(new URL(cmd.getOptionValue("f", "https://fox.demos.dice-research.org/fox")));

        String sparqlEndpoint = "https://dbpedia.org/sparql";
        logger.info("Connecting to SPARQL endpoint: {}", sparqlEndpoint);
        try (RDFConnection con = RDFConnection.connect(sparqlEndpoint)) {
            String inputFile = cmd.getOptionValue("i");
            logger.info("Reading QA dataset: {}", inputFile);
            try (InputStream is = new FileInputStream(inputFile)) {
                ObjectMapper mapper = new ObjectMapper();
                try (JsonParser parser = mapper.getFactory().createParser(is)) {
                    if (parser.nextToken() != JsonToken.START_ARRAY) {
                        throw new IllegalStateException("Expected a JSON array");
                    }
                    long i = 0;
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        Question q = mapper.readValue(parser, Question.class);
                        logger.info("Input question: {} ({})", q.question, q.query);
                        new QuestionMutator(q.question, q.query, ner, con);
                        i++;
                    }
                    logger.info("Total input questions: {}", i);
                }
            }
        }
        logger.info("Done");
    }
}
