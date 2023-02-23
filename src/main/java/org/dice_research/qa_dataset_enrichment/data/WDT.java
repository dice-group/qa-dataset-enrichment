package org.dice_research.qa_dataset_enrichment.data;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

public class WDT {
    public static final String uri = "http://www.wikidata.org/prop/direct/";
    public static String getURI() { return uri; }
    public static final Property property(String local) { return ResourceFactory.createProperty(uri, local); }
    public static final Property instanceOf = property("P31");
}
