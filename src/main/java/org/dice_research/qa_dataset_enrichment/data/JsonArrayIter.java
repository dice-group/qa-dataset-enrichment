package org.dice_research.qa_dataset_enrichment.data;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonArrayIter<T> implements Closeable, Iterable<T>, Iterator<T> {
    private static Logger logger = LoggerFactory.getLogger(JsonArrayIter.class);
    private static ObjectMapper mapper = new ObjectMapper();

    private Class<T> cls;
    private JsonParser parser;
    private long size = 0;

    public JsonArrayIter(InputStream is, Class<T> cls) throws JsonParseException, IOException {
        parser = mapper.getFactory().createParser(is); // close
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw new IllegalStateException("Expected a JSON array");
        }
        this.cls = cls;
    }

    public long size() {
        return size;
    }

    @Override public Iterator<T> iterator() {
        return this;
    }

    @Override public boolean hasNext() {
        try {
            return parser.nextToken() != JsonToken.END_ARRAY;
        } catch (IOException e) {
            logger.error("Exception while getting the next token: {}", e);
            return false;
        }
    }

    @Override public T next() {
        try {
            T value = mapper.readValue(parser, cls);
            size++;
            return value;
        } catch (IOException e) {
            logger.error("Exception while getting the next object: {}", e);
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }
}
