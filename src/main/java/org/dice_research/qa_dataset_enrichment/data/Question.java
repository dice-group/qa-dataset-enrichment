package org.dice_research.qa_dataset_enrichment.data;

import java.util.List;

public class Question {
    public long id;
    public String question;
    public String query;
    public List<Mention> query_ent_mentions;

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Question [id=" + id + ", question=" + question + ", query=" + query + "]";
    }
}
