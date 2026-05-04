package utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP utilities for communicating with a remote git server using the Smart HTTP protocol.
 *
 * <p>Supports ref discovery via {@code GET /info/refs?service=git-upload-pack} and
 * packfile retrieval via {@code POST /git-upload-pack}.
 */
public class NetworkUtils {

  private NetworkUtils() {}

  /**
   * Discovers all refs advertised by a remote repository.
   *
   * <p>Sends a {@code GET} request to the given URL (which must already include the
   * {@code ?service=git-upload-pack} query parameter) and parses the pkt-line response
   * to build a map from ref name to SHA-1.
   *
   * @param url the info/refs URL of the remote repository
   * @return a map from ref name (e.g., {@code refs/heads/main}) to its 40-character hex SHA-1
   * @throws IOException if the HTTP request fails or the response cannot be read
   */
  public static Map<String, String> discoverRefs(String url) throws IOException {
    Map<String, String> refs = new HashMap<>();

    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent", "git/");

    try (InputStream in = conn.getInputStream()) {
      byte[] data = in.readAllBytes();
      String response = new String(data, StandardCharsets.UTF_8);

      String[] lines = response.split("\n");
      for (String line : lines) {
        if (line.length() < 4 || line.startsWith("# service=", 4) || line.substring(4).trim().isEmpty()) {
          continue;
        }

        String content = line.substring(4);

        String[] parts = content.split("\0")[0].trim().split("\\s+");
        if (parts.length >= 2) {
          String sha = parts[0];
          String ref = parts[1];
          refs.put(ref, sha);
        }
      }
    }

    return refs;
  }
  
  /**
   * Downloads a packfile for a single object from a remote repository.
   *
   * <p>Sends a {@code POST} request to the git-upload-pack endpoint, requesting the
   * given SHA-1 with a minimal {@code want/flush/done} negotiation. Parses any
   * sideband-64k framing from the response before returning the raw packfile bytes.
   *
   * @param url     the {@code /git-upload-pack} endpoint URL of the remote repository
   * @param wantSha the 40-character hex SHA-1 of the object to fetch
   * @return the raw bytes of the received packfile
   * @throws IOException if the HTTP request fails, the server returns a non-200 status,
   *                     or no packfile data can be located in the response
   */
  public static byte[] fetchPackfile(String url, String wantSha) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-git-upload-pack-request");
    conn.setRequestProperty("User-Agent", "git/");

    ByteArrayOutputStream requestBody = new ByteArrayOutputStream();

    String wantLine = "want " + wantSha + "\n";
    writePktLine(requestBody, wantLine);
    writePktLine(requestBody, null);

    writePktLine(requestBody, "done\n");

    try (OutputStream out = conn.getOutputStream()) {
      out.write(requestBody.toByteArray());
    }

    int responseCode = conn.getResponseCode();
    if (responseCode != 200) {
      throw new IOException("HTTP error: " + responseCode + " " + conn.getResponseMessage());
    }

    InputStream responseStream;
    try {
      responseStream = conn.getInputStream();
    } catch (IOException e) {
      responseStream = conn.getErrorStream();
      if (responseStream == null) throw e;
    }

    try (InputStream in = responseStream) {
      ByteArrayOutputStream packData = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;

      while ((read = in.read(buffer)) != -1) {
        packData.write(buffer, 0, read);
      }

      byte[] data = packData.toByteArray();
      return parseSideBandData(data);
    }
  }
  
  /**
   * Writes a single pkt-line record to an output stream.
   *
   * <p>If {@code line} is {@code null}, the flush-pkt ({@code 0000}) is written.
   * Otherwise the 4-character hex length prefix is prepended and the line is written.
   *
   * @param out  the output stream to write to
   * @param line the line to write, or {@code null} to write a flush-pkt
   * @throws IOException if the write fails
   */
  public static void writePktLine(OutputStream out, String line) throws IOException {
    if (line == null) {
      out.write("0000".getBytes(StandardCharsets.UTF_8));
    } else {
      int length = line.length() + 4;
      String lengthHex = String.format("%04x", length);
      out.write(lengthHex.getBytes(StandardCharsets.UTF_8));
      out.write(line.getBytes(StandardCharsets.UTF_8));
    }
  }
  
  /**
   * Strips sideband framing from a git-upload-pack response and returns the packfile bytes.
   *
   * <p>First attempts to parse the response as sideband-64k encoded data; if that yields
   * no data, falls back to scanning for a raw {@code PACK} header.
   *
   * @param data the raw response bytes from the server
   * @return the raw packfile bytes
   * @throws IOException if no packfile data can be found
   */
  private static byte[] parseSideBandData(byte[] data) throws IOException {
    byte[] result = extractPackfileFromSideband(data);
    if (result.length > 0) {
      return result;
    }
    return findRawPackData(data);
  }

  /**
   * Attempts to parse a 4-character hex string as a pkt-line length.
   *
   * @param lengthHex the 4-character hex string
   * @return the parsed non-negative integer, or {@code -1} if the string is not valid hex
   */
  private static int tryParsePktLength(String lengthHex) {
    try {
      return Integer.parseInt(lengthHex, 16);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * Extracts packfile bytes from a sideband-64k encoded response.
   *
   * <p>Iterates over pkt-line frames; frames whose first byte is {@code 0x01} (data
   * channel) are appended to the output, stripping the channel byte.
   *
   * @param data the raw sideband-encoded response bytes
   * @return the concatenated packfile bytes, or an empty array if no data frames were found
   */
  private static byte[] extractPackfileFromSideband(byte[] data) {
    ByteArrayOutputStream packfile = new ByteArrayOutputStream();
    int pos = 0;
    while (pos + 4 <= data.length) {
      String lengthHex = new String(data, pos, 4, StandardCharsets.UTF_8);
      int length = tryParsePktLength(lengthHex);
      if (length < 0 || (length >= 4 && pos + length > data.length)) break;
      if (length < 4) {
        pos += 4;
      } else {
        byte[] content = java.util.Arrays.copyOfRange(data, pos + 4, pos + length);
        if (content.length > 0 && (content[0] & 0xFF) == 1) {
          packfile.write(content, 1, content.length - 1);
        }
        pos += length;
      }
    }
    return packfile.toByteArray();
  }

  /**
   * Scans raw bytes for a {@code PACK} magic header and returns the packfile starting there.
   *
   * @param data the raw bytes to scan
   * @return the packfile bytes beginning at the {@code PACK} signature
   * @throws IOException if no {@code PACK} signature is found in {@code data}
   */
  private static byte[] findRawPackData(byte[] data) throws IOException {
    for (int i = 0; i < data.length - 3; i++) {
      if (data[i] == 'P' && data[i+1] == 'A' &&
          data[i+2] == 'C' && data[i+3] == 'K') {
        return java.util.Arrays.copyOfRange(data, i, data.length);
      }
    }
    String preview = new String(data, 0, Math.min(data.length, 100), StandardCharsets.UTF_8);
    throw new IOException("No packfile data found in response (" + data.length +
      " bytes). Preview: " + preview.replaceAll("[^\\x20-\\x7E]", "."));
  }
}
