package org.dice_research.qa_dataset_enrichment.ner;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aksw.fox.data.Voc;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.dice_research.qa_dataset_enrichment.data.WD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Neamt implements Function<String,Model> {
    private static Logger logger = LoggerFactory.getLogger(Neamt.class);

    private static ObjectMapper mapper = new ObjectMapper();
    private HttpClient http = HttpClient.newHttpClient();
    private HttpRequest.Builder requestBuilder;

    public Neamt(String apiURL) throws URISyntaxException {
        requestBuilder = HttpRequest.newBuilder(new URI(apiURL)).header("Content-Type", "application/x-www-form-urlencoded");
    }

    @Override
    public Model apply(String input) {
        try {
            Map<String,String> items = Map.of(
                "components", "babelscape_ner, mgenre_el",
                "mg_num_return_sequences", "5",
                "query", input
            );
            String text = http.send(requestBuilder.POST(HttpRequest.BodyPublishers.ofString(encodeForm(items))).build(), HttpResponse.BodyHandlers.ofString()).body().toString();
            logger.trace("Neamt response: {}", text);
            ResponseData data = mapper.readValue(mapper.getFactory().createParser(text), ResponseData.class);
            Model model = ModelFactory.createDefaultModel();
            for (Mention mention : data.ent_mentions) {
                Resource r = model.createResource();
                for (String[] link_candidate : mention.link_candidates) {
                    String id = link_candidate[2];
                    if (!id.equals("")) {
                        r.addProperty(Voc.pItsrdfTaIdentRef, WD.resource(id));
                    }
                }
                if (model.contains(r, Voc.pItsrdfTaIdentRef)) {
                    r.addProperty(RDF.type, Voc.pNifPhrase);
                    r.addLiteral(Voc.pNifBegin, mention.start);
                    r.addLiteral(Voc.pNifEnd, mention.end);
                    r.addProperty(Voc.pNifAnchorOf, input.substring(mention.start, mention.end));
                }
            }
            return model;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String encodeForm(Map<String,String> items) {
        return items.entrySet().stream().map(e -> encodeURI(e.getKey()) + "=" + encodeURI(e.getValue())).collect(Collectors.joining("&"));
    }

    private String encodeURI(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties({
        "components",
        "lang",
        "mg_num_return_sequences",
        "placeholder",
        "replace_before",
        "text",
    })

    private static class ResponseData {
        public String kb;
        public Mention[] ent_mentions;

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "ResponseData [ent_mentions=" + Arrays.toString(ent_mentions) + "]";
        }
    }

    @JsonIgnoreProperties({
        "link",
        "surface_form",
    })
    private static class Mention {
        public int start;
        public int end;
        public String[][] link_candidates;

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "Mention [link_candidates=" + Arrays.toString(link_candidates) + "]";
        }
    }
}
