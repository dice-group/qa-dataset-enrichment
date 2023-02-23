package org.dice_research.qa_dataset_enrichment.data;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class WD {
    public static final String uri = "http://www.wikidata.org/entity/";
    public static String getURI() { return uri; }
    public static final Resource resource(String local) { return ResourceFactory.createResource(uri + local); }
}
