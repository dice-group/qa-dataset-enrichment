package org.dice_research.qa_dataset_enrichment.cli;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dice_research.qa_dataset_enrichment.data.JsonArrayIter;
import org.dice_research.qa_dataset_enrichment.data.JsonArrayWriter;
import org.dice_research.qa_dataset_enrichment.data.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate learning examples for question answering based on the existing ones.
 */
public class QueryEntityParser {
    private static Logger logger = LoggerFactory.getLogger(QueryEntityParser.class);

    private static Option inputOption = Option.builder().longOpt("input").hasArg(true).required().desc("Input dataset (JSON)").build();
    private static Option outputOption = Option.builder().longOpt("output").hasArg(true).required().desc("Output dataset (JSON)").build();

    public static void main(String[] args) throws IOException {
        Options options = new Options();
        options.addOption(inputOption);
        options.addOption(outputOption);
        try {
            CommandLine cmd = new DefaultParser().parse(options, args);
            String inputFile = cmd.getOptionValue(inputOption);
            String outputFile = cmd.getOptionValue(outputOption);
            main(inputFile, outputFile);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("QueryEntityParser", options);
            System.exit(1);
        }
    }

    public static void main(String inputFile, String outputFile) throws IOException {
        try (
            InputStream is = new FileInputStream(inputFile);
            JsonArrayIter<Question> questions = new JsonArrayIter<>(is, Question.class);
            OutputStream os = new FileOutputStream(outputFile);
            JsonArrayWriter<Question> writer = new JsonArrayWriter<>(os);
        ) {
            var fun = new org.dice_research.qa_dataset_enrichment.QueryEntityParser();
            for (Question question : questions) {
                logger.debug("Input: {}", question);
                writer.write(fun.apply(question));
            }
        }
    }
}
