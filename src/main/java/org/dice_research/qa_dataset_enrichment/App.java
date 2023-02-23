package org.dice_research.qa_dataset_enrichment;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.Function;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnection;
import org.dice_research.qa_dataset_enrichment.data.JsonArrayIter;
import org.dice_research.qa_dataset_enrichment.data.JsonArrayWriter;
import org.dice_research.qa_dataset_enrichment.data.Question;
import org.dice_research.qa_dataset_enrichment.ner.Fox;
import org.dice_research.qa_dataset_enrichment.ner.Neamt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;

/**
 * Generate learning examples for question answering based on the existing ones.
 */
public class App {
    private static Logger logger = LoggerFactory.getLogger(App.class);

    private static Option inputFileOption = new Option("i", "input-file", true, "Input dataset file (JSON)");
    private static Option outputFileOption = new Option("o", "output-file", true, "Output dataset file (JSON)");
    private static Option reportFileOption = new Option("r", "report-file", true, "Output report file (CSV)");
    private static Option sparqlEndpointOption = new Option("s", "sparql-endpoint", true, "Sparql endpoint");
    private static Option foxURLOption = new Option("fox", "fox", true, "FOX API URL");
    private static Option filterOption = new Option("f", "filter", true, "Only process questions which contain this");
    private static Option limitOption = new Option("l", "limit", true, "Stop after processing the specified number of questions");

    public static void main(String[] args) throws IOException, JsonParseException, MalformedURLException, ParseException, URISyntaxException {
        Options options = new Options();
        options.addOption(inputFileOption);
        options.addOption(outputFileOption);
        options.addOption(reportFileOption);
        options.addOption(sparqlEndpointOption);
        options.addOption(foxURLOption);
        options.addOption(filterOption);
        options.addOption(limitOption);
        CommandLine cmd = new DefaultParser().parse(options, args);

        Function<String,Model> ner = new Fox(new URL(cmd.getOptionValue(foxURLOption, "https://fox.demos.dice-research.org/fox")));
        ner = new Neamt(cmd.getOptionValue(foxURLOption, "http://neamt.cs.upb.de:6100/custom-pipeline"));

        String sparqlEndpoint = cmd.getOptionValue(sparqlEndpointOption, "https://query.wikidata.org/bigdata/namespace/wdq/sparql");
        String inputFile = cmd.getOptionValue(inputFileOption);
        String outputFile = cmd.getOptionValue(outputFileOption);
        logger.debug("SPARQL endpoint: {}", sparqlEndpoint);
        logger.debug("Input dataset: {}", inputFile);
        long totalGenerated = 0;
        try (
            RDFConnection con = RDFConnection.connect(sparqlEndpoint);
            InputStream is = new FileInputStream(inputFile);
            JsonArrayIter<Question> questions = new JsonArrayIter<>(is, Question.class);
            OutputStream os = new FileOutputStream(outputFile);
            JsonArrayWriter<Question> writer = new JsonArrayWriter<>(os);
            CSVPrinter report = new CSVPrinter(new FileWriter(cmd.getOptionValue("r")), CSVFormat.RFC4180)
        ) {
            int q_i = 0;
            int limit = Integer.parseInt(cmd.getOptionValue("l", "-1"));
            for (Question question : questions) {
                logger.debug("Input question: {}", question.question);
                QuestionMutator m = new QuestionMutator();
                try {
                    m.mutate(question.question, question.query, ner, con);
                    if (m.size() != 0) {
                        for (Question q : m.questions) {
                            writer.write(q);
                            ++totalGenerated;
                        }
                        report.printRecord(question.question, m.foundEntities, m.entities, m.size(), m.questions.get(0).question);
                    } else {
                        report.printRecord(question.question, m.foundEntities, m.entities, m.size());
                    }
                } catch (Exception e) {
                    logger.error("Exception while processing the question {}: {}", question.question, e);
                    report.printRecord(question.question, m.foundEntities, m.entities, 0, e.getMessage());
                }
                report.flush();
                ++q_i;
                if (limit != -1 && q_i >= limit) break;
            }
            logger.info("Total input questions: {}", questions.size());
        }
        logger.info("Total questions generated: {}", totalGenerated);
    }
}
