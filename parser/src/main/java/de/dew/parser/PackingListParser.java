package de.dew.parser;

import java.io.InputStream;

public interface PackingListParser {

    PackingListResult parse(InputStream in, String originalFilename) throws PackingListParseException;
}
