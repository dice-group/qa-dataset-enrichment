package org.dice_research.qa_dataset_enrichment.data;

public class Mention {
    public String link;

    public Mention(String link) {
        this.link = link;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Mention [link=" + link + "]";
    }
}
