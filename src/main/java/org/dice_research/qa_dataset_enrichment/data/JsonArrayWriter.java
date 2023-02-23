package org.dice_research.qa_dataset_enrichment.data;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonArrayWriter<T> implements Closeable {
    private static Logger logger = LoggerFactory.getLogger(JsonArrayWriter.class);
    private static ObjectMapper mapper = new ObjectMapper();

    private JsonGenerator generator;
    private long size = 0;

    public JsonArrayWriter(OutputStream os) throws JsonParseException, IOException {
        generator = mapper.getFactory().createGenerator(os);
        generator.setPrettyPrinter(new DefaultPrettyPrinter());
        generator.writeStartArray();
    }

    public long size() {
        return size;
    }

    public void write(T value) throws StreamWriteException, DatabindException, IOException {
        mapper.writeValue(generator, value);
        ++size;
    }

    @Override
    public void close() throws IOException {
        generator.writeEndArray();
        generator.close();
    }
}
