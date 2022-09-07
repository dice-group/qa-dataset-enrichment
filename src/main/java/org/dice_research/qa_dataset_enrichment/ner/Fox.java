package org.dice_research.qa_dataset_enrichment.ner;

import java.io.StringReader;
import java.net.URL;
import java.util.function.Function;

import org.aksw.fox.binding.FoxApi;
import org.aksw.fox.binding.FoxParameter;
import org.aksw.fox.binding.IFoxApi;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

public class Fox implements Function<String,Model> {

    private static FoxParameter.OUTPUT output = FoxParameter.OUTPUT.TURTLE;
    private IFoxApi fox;

    public Fox(URL apiURL) {
        fox = new FoxApi()
        .setApiURL(apiURL)
        .setTask(FoxParameter.TASK.NER)
        .setOutputFormat(output)
        .setLang(FoxParameter.LANG.EN);
    }

    @Override
    public Model apply(String input) {
        fox.setInput(input).send();
        Model model = ModelFactory.createDefaultModel();
        model.read(new StringReader(fox.responseAsFile()), null, FoxParameter.outputs.get(output));
        return model;
    }
    
}
