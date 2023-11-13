package ch.ejpd.lgs.commons.sedex;

import ch.ejpd.lgs.commons.sedex.model.SedexReceipt;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SedexReceiptReader {
  private final XmlMapper mapper;

  /**
   * Constructor for SedexReceiptReader.
   * Initializes the XML mapper with deserialization features.
   */
  public SedexReceiptReader() {
    mapper = new XmlMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  /**
   * Reads a SedexReceipt object from a file.
   *
   * @param path The path to the file containing the SedexReceipt data.
   * @return Optional containing the SedexReceipt if reading is successful; empty otherwise.
   */
  public Optional<SedexReceipt> readFromFile(Path path) {
    try {
      return Optional.ofNullable(mapper.readValue(path.toFile(), SedexReceipt.class));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /**
   * Reads a SedexReceipt object from a string.
   *
   * @param input The input string containing the SedexReceipt data.
   * @return Optional containing the SedexReceipt if reading is successful; empty otherwise.
   */
  public Optional<SedexReceipt> readFromString(String input) {
    try {
      return Optional.ofNullable(mapper.readValue(input, SedexReceipt.class));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
