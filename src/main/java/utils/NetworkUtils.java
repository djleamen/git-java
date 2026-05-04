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

public class NetworkUtils {

  private NetworkUtils() {}

  /**
   * Discover refs from remote repository
   */
  public static Map<String, String> discoverRefs(String url) throws IOException {
    Map<String, String> refs = new HashMap<>();
    
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent", "git/");
    
    try (InputStream in = conn.getInputStream()) {
      byte[] data = in.readAllBytes();
      String response = new String(data, StandardCharsets.UTF_8);
      
      // Parse pkt-line format
      String[] lines = response.split("\n");
      for (String line : lines) {
        if (line.length() < 4 || line.substring(4).startsWith("# service=") || line.substring(4).trim().isEmpty()) {
          continue;
        }
        
        // Skip the length prefix (4 hex digits)
        String content = line.substring(4);
        
        // Parse ref line: <sha> <ref>\0<capabilities> or <sha> <ref>
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
   * Fetch packfile from remote
   */
  public static byte[] fetchPackfile(String url, String wantSha) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-git-upload-pack-request");
    conn.setRequestProperty("User-Agent", "git/");
    
    // Build request
    ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
    
    // Want line - use simpler capabilities
    String wantLine = "want " + wantSha + "\n";
    writePktLine(requestBody, wantLine);
    writePktLine(requestBody, null); // flush-pkt
    
    writePktLine(requestBody, "done\n");
    
    try (OutputStream out = conn.getOutputStream()) {
      out.write(requestBody.toByteArray());
    }
    
    // Check response code
    int responseCode = conn.getResponseCode();
    if (responseCode != 200) {
      throw new IOException("HTTP error: " + responseCode + " " + conn.getResponseMessage());
    }
    
    InputStream responseStream;
    try {
      responseStream = conn.getInputStream();
    } catch (IOException e) {
      // Try error stream
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
   * Write a pkt-line
   */
  public static void writePktLine(OutputStream out, String line) throws IOException {
    if (line == null) {
      // flush-pkt
      out.write("0000".getBytes(StandardCharsets.UTF_8));
    } else {
      int length = line.length() + 4;
      String lengthHex = String.format("%04x", length);
      out.write(lengthHex.getBytes(StandardCharsets.UTF_8));
      out.write(line.getBytes(StandardCharsets.UTF_8));
    }
  }
  
  /**
   * Parse side-band data
   */
  private static byte[] parseSideBandData(byte[] data) throws IOException {
    byte[] result = extractPackfileFromSideband(data);
    if (result.length > 0) {
      return result;
    }
    return findRawPackData(data);
  }

  /** 
   * @param lengthHex
   * @return int
   */
  private static int tryParsePktLength(String lengthHex) {
    try {
      return Integer.parseInt(lengthHex, 16);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** 
   * @param data
   * @return byte[]
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
   * @param data
   * @return byte[]
   * @throws IOException
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
