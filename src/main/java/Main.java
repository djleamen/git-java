/**
 *  Git - A simple Git implementation in Java
 *  From CodeCrafters.io build-your-own-git (Java)
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class Main {
  public static void main(String[] args){    
    final String command = args[0];
    
    switch (command) {
      // git init
      case "init" -> {
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs").mkdirs();
        final File head = new File(root, "HEAD");
    
        try {
          head.createNewFile();
          Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
          System.out.println("Initialized git directory");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      // cat-file -p <hash>
      case "cat-file" -> {
        if (args.length < 3 || !args[1].equals("-p")) {
          System.out.println("Usage: cat-file -p <hash>");
          return;
        }
        
        String hash = args[2];
        String dirName = hash.substring(0, 2);
        String fileName = hash.substring(2);
        File objectFile = new File(".git/objects/" + dirName + "/" + fileName);
        
        try (FileInputStream fis = new FileInputStream(objectFile);
             InflaterInputStream iis = new InflaterInputStream(fis)) {
          
          byte[] decompressed = iis.readAllBytes();
          
          // Find null byte that separates header from content
          int nullIndex = -1;
          for (int i = 0; i < decompressed.length; i++) {
            if (decompressed[i] == 0) {
              nullIndex = i;
              break;
            }
          }
          
          if (nullIndex == -1) {
            throw new RuntimeException("Invalid object file format");
          }
          
          // Extract everything after the null byte
          byte[] content = Arrays.copyOfRange(decompressed, nullIndex + 1, decompressed.length);
          
          System.out.print(new String(content));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      // hash-object -w <file>
      case "hash-object" -> {
        if (args.length < 3 || !args[1].equals("-w")) {
          System.out.println("Usage: hash-object -w <file>");
          return;
        }
        
        String filename = args[2];
        File file = new File(filename);
        
        try {
          // Read file
          byte[] fileContent = Files.readAllBytes(file.toPath());
          
          // Create blob header
          String header = "blob " + fileContent.length + "\0";
          byte[] headerBytes = header.getBytes();
          
          // Combine header and content
          byte[] blobData = new byte[headerBytes.length + fileContent.length];
          System.arraycopy(headerBytes, 0, blobData, 0, headerBytes.length);
          System.arraycopy(fileContent, 0, blobData, headerBytes.length, fileContent.length);
          
          // Compute SHA-1 hash
          MessageDigest digest = MessageDigest.getInstance("SHA-1");
          byte[] hashBytes = digest.digest(blobData);
          
          // Convert hash to hex string
          StringBuilder hashHex = new StringBuilder();
          for (byte b : hashBytes) {
            hashHex.append(String.format("%02x", b));
          }
          String hash = hashHex.toString();
          
          // Create directory structure
          String dirName = hash.substring(0, 2);
          String fileName = hash.substring(2);
          File objectDir = new File(".git/objects/" + dirName);
          objectDir.mkdirs();
          
          // Write compressed blob to file
          File objectFile = new File(objectDir, fileName);
          try (FileOutputStream fos = new FileOutputStream(objectFile);
               DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
            dos.write(blobData);
          }
          
          System.out.println(hash);
          
        } catch (IOException | NoSuchAlgorithmException e) {
          throw new RuntimeException(e);
        }
      }
      // ls-tree --name-only <tree_sha>
      case "ls-tree" -> {
        if (args.length < 3 || !args[1].equals("--name-only")) {
          System.out.println("Usage: ls-tree --name-only <tree_sha>");
          return;
        }
        
        String hash = args[2];
        String dirName = hash.substring(0, 2);
        String fileName = hash.substring(2);
        File objectFile = new File(".git/objects/" + dirName + "/" + fileName);
        
        try (FileInputStream fis = new FileInputStream(objectFile);
             InflaterInputStream iis = new InflaterInputStream(fis)) {
          
          byte[] decompressed = iis.readAllBytes();
          
          // Find null byte that separates header from content
          int nullIndex = -1;
          for (int i = 0; i < decompressed.length; i++) {
            if (decompressed[i] == 0) {
              nullIndex = i;
              break;
            }
          }
          
          if (nullIndex == -1) {
            throw new RuntimeException("Invalid tree object format");
          }
          
          // Parse tree entries
          int pos = nullIndex + 1;
          while (pos < decompressed.length) {
            int nameStart = pos;
            while (nameStart < decompressed.length && decompressed[nameStart] != ' ') {
              nameStart++;
            }
            nameStart++;
            
            int nameEnd = nameStart;
            while (nameEnd < decompressed.length && decompressed[nameEnd] != 0) {
              nameEnd++;
            }
            
            String name = new String(Arrays.copyOfRange(decompressed, nameStart, nameEnd));
            System.out.println(name);
            
            // Skip the 20-byte SHA-1 hash
            pos = nameEnd + 1 + 20;
          }
          
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      // write-tree
      case "write-tree" -> {
        try {
          String hash = writeTree(new File("."));
          System.out.println(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
          throw new RuntimeException(e);
        }
      }
      // commit-tree <tree_sha> -p <commit_sha> -m <message>
      case "commit-tree" -> {
        if (args.length < 6 || !args[2].equals("-p") || !args[4].equals("-m")) {
          System.out.println("Usage: commit-tree <tree_sha> -p <commit_sha> -m <message>");
          return;
        }
        
        String treeSha = args[1];
        String parentSha = args[3];
        String message = args[5];
        
        try {
          // Build commit content
          StringBuilder content = new StringBuilder();
          content.append("tree ").append(treeSha).append("\n");
          content.append("parent ").append(parentSha).append("\n");
          
          // Hardcoded author and committer (for simplicity)
          String authorLine = "author John Doe <john@example.com> 1234567890 +0000\n";
          String committerLine = "committer John Doe <john@example.com> 1234567890 +0000\n";
          content.append(authorLine);
          content.append(committerLine);
          
          content.append("\n");
          content.append(message).append("\n");
          
          byte[] contentBytes = content.toString().getBytes();
          
          String header = "commit " + contentBytes.length + "\0";
          byte[] headerBytes = header.getBytes();
          
          byte[] commitData = new byte[headerBytes.length + contentBytes.length];
          System.arraycopy(headerBytes, 0, commitData, 0, headerBytes.length);
          System.arraycopy(contentBytes, 0, commitData, headerBytes.length, contentBytes.length);
          
          MessageDigest digest = MessageDigest.getInstance("SHA-1");
          byte[] hashBytes = digest.digest(commitData);
          
          StringBuilder hashHex = new StringBuilder();
          for (byte b : hashBytes) {
            hashHex.append(String.format("%02x", b));
          }
          String hash = hashHex.toString();
          
          String dirName = hash.substring(0, 2);
          String fileName = hash.substring(2);
          File objectDir = new File(".git/objects/" + dirName);
          objectDir.mkdirs();
          
          // Write compressed commit to file
          File objectFile = new File(objectDir, fileName);
          try (FileOutputStream fos = new FileOutputStream(objectFile);
               DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
            dos.write(commitData);
          }
          
          System.out.println(hash);
          
        } catch (IOException | NoSuchAlgorithmException e) {
          throw new RuntimeException(e);
        }
      }
      // clone <url> <directory>
      case "clone" -> {
        if (args.length < 3) {
          System.out.println("Usage: clone <url> <directory>");
          return;
        }
        
        String repoUrl = args[1];
        String targetDir = args[2];
        
        try {
          cloneRepository(repoUrl, targetDir);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }
  
  // Helper class - tree entry
  static class TreeEntry implements Comparable<TreeEntry> {
    String mode;
    String name;
    String hash;
    
    TreeEntry(String mode, String name, String hash) {
      this.mode = mode;
      this.name = name;
      this.hash = hash;
    }
    
    @Override
    public int compareTo(TreeEntry other) {
      return this.name.compareTo(other.name);
    }
  }
  
  // Create a blob object from a file and return its hash
  static String createBlob(File file) throws IOException, NoSuchAlgorithmException {
    byte[] fileContent = Files.readAllBytes(file.toPath());
    
    String header = "blob " + fileContent.length + "\0";
    byte[] headerBytes = header.getBytes();
    
    byte[] blobData = new byte[headerBytes.length + fileContent.length];
    System.arraycopy(headerBytes, 0, blobData, 0, headerBytes.length);
    System.arraycopy(fileContent, 0, blobData, headerBytes.length, fileContent.length);
    
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    byte[] hashBytes = digest.digest(blobData);
    
    StringBuilder hashHex = new StringBuilder();
    for (byte b : hashBytes) {
      hashHex.append(String.format("%02x", b));
    }
    String hash = hashHex.toString();
    
    String dirName = hash.substring(0, 2);
    String fileName = hash.substring(2);
    File objectDir = new File(".git/objects/" + dirName);
    objectDir.mkdirs();
    
    File objectFile = new File(objectDir, fileName);
    try (FileOutputStream fos = new FileOutputStream(objectFile);
         DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
      dos.write(blobData);
    }
    
    return hash;
  }
  
  // Recursively write a tree object and return its hash
  static String writeTree(File directory) throws IOException, NoSuchAlgorithmException {
    List<TreeEntry> entries = new ArrayList<>();
    
    File[] files = directory.listFiles();
    if (files == null) {
      throw new RuntimeException("Cannot read directory: " + directory);
    }
    
    for (File file : files) {
      // Skip .git directory
      if (file.getName().equals(".git")) {
        continue;
      }
      
      if (file.isFile()) {
        String hash = createBlob(file);
        String mode = file.canExecute() ? "100755" : "100644";
        entries.add(new TreeEntry(mode, file.getName(), hash));
      } else if (file.isDirectory()) {
        String hash = writeTree(file);
        entries.add(new TreeEntry("40000", file.getName(), hash));
      }
    }
    
    // Sort entries alphabetically
    Collections.sort(entries);
    
    List<byte[]> contentParts = new ArrayList<>();
    int totalSize = 0;
    
    for (TreeEntry entry : entries) {
      // Format: <mode> <name>\0<20_byte_sha>
      String entryPrefix = entry.mode + " " + entry.name + "\0";
      byte[] entryPrefixBytes = entryPrefix.getBytes();
      
      byte[] hashBytes = new byte[20];
      for (int i = 0; i < 20; i++) {
        hashBytes[i] = (byte) Integer.parseInt(entry.hash.substring(i * 2, i * 2 + 2), 16);
      }
      
      contentParts.add(entryPrefixBytes);
      contentParts.add(hashBytes);
      totalSize += entryPrefixBytes.length + hashBytes.length;
    }
    
    String header = "tree " + totalSize + "\0";
    byte[] headerBytes = header.getBytes();
    
    byte[] treeData = new byte[headerBytes.length + totalSize];
    int pos = 0;
    System.arraycopy(headerBytes, 0, treeData, pos, headerBytes.length);
    pos += headerBytes.length;
    
    for (byte[] part : contentParts) {
      System.arraycopy(part, 0, treeData, pos, part.length);
      pos += part.length;
    }
    
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    byte[] hashBytes = digest.digest(treeData);
    
    StringBuilder hashHex = new StringBuilder();
    for (byte b : hashBytes) {
      hashHex.append(String.format("%02x", b));
    }
    String hash = hashHex.toString();
    
    String dirName = hash.substring(0, 2);
    String fileName = hash.substring(2);
    File objectDir = new File(".git/objects/" + dirName);
    objectDir.mkdirs();
    
    File objectFile = new File(objectDir, fileName);
    try (FileOutputStream fos = new FileOutputStream(objectFile);
         DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
      dos.write(treeData);
    }
    
    return hash;
  }
  
  // Clone repository from a remote URL
  static void cloneRepository(String repoUrl, String targetDir) throws Exception {
    // Create target directory
    File dir = new File(targetDir);
    if (!dir.mkdir()) {
      throw new RuntimeException("Failed to create directory: " + targetDir);
    }
    
    // Initialize git repository
    File gitDir = new File(dir, ".git");
    new File(gitDir, "objects").mkdirs();
    new File(gitDir, "refs/heads").mkdirs();
    
    // Discover refs from remote
    String discoverUrl = repoUrl + "/info/refs?service=git-upload-pack";
    Map<String, String> refs = discoverRefs(discoverUrl);
    
    // Find the actual commit SHA to fetch
    // Look for HEAD symref first, or fallback to main/master branch
    String headRef = null;
    String targetBranch = null;
    
    // Try to find a valid branch ref
    if (refs.containsKey("refs/heads/main")) {
      headRef = refs.get("refs/heads/main");
      targetBranch = "refs/heads/main";
    } else if (refs.containsKey("refs/heads/master")) {
      headRef = refs.get("refs/heads/master");
      targetBranch = "refs/heads/master";
    } else {
      // Find any head ref
      for (String ref : refs.keySet()) {
        if (ref.startsWith("refs/heads/")) {
          headRef = refs.get(ref);
          targetBranch = ref;
          break;
        }
      }
    }
    
    if (headRef == null) {
      throw new RuntimeException("No branch refs found in repository");
    }
    
    String uploadPackUrl = repoUrl + "/git-upload-pack";
    byte[] packfile = fetchPackfile(uploadPackUrl, headRef);
    
    unpackPackfile(packfile, gitDir);
    
    // Set HEAD
    File headFile = new File(gitDir, "HEAD");
    Files.write(headFile.toPath(), ("ref: " + targetBranch + "\n").getBytes());
    
    // Write refs
    for (Map.Entry<String, String> entry : refs.entrySet()) {
      String ref = entry.getKey();
      String sha = entry.getValue();
      
      if (ref.startsWith("refs/heads/") || ref.startsWith("refs/tags/")) {
        File refFile = new File(gitDir, ref);
        refFile.getParentFile().mkdirs();
        Files.write(refFile.toPath(), (sha + "\n").getBytes());
      }
    }
    
    // Checkout HEAD commit
    checkoutCommit(dir, gitDir, headRef);
  }
  
  // Discover refs from remote repository
  static Map<String, String> discoverRefs(String url) throws IOException {
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
        if (line.length() < 4) continue;
        
        // Skip the length prefix (4 hex digits)
        String content = line.substring(4);
        
        // Skip service announcement
        if (content.startsWith("# service=")) continue;
        if (content.trim().isEmpty()) continue;
        
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
  
  // Fetch packfile from remote
  static byte[] fetchPackfile(String url, String wantSha) throws IOException {
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
  
  // Write a pkt-line
  static void writePktLine(OutputStream out, String line) throws IOException {
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
  
  // Parse side-band data
  static byte[] parseSideBandData(byte[] data) throws IOException {
    // First, try to find PACK signature directly in the data
    for (int i = 0; i < data.length - 3; i++) {
      if (data[i] == 'P' && data[i+1] == 'A' && 
          data[i+2] == 'C' && data[i+3] == 'K') {
        // Found PACK signature, return from here to end
        return Arrays.copyOfRange(data, i, data.length);
      }
    }
    
    // If no PACK signature found, try parsing pkt-line format
    ByteArrayOutputStream packfile = new ByteArrayOutputStream();
    int pos = 0;
    
    while (pos < data.length) {
      if (pos + 4 > data.length) break;
      
      // Read pkt-line length
      String lengthHex = new String(data, pos, 4, StandardCharsets.UTF_8);
      
      // Check for flush-pkt
      if (lengthHex.equals("0000")) {
        pos += 4;
        continue;
      }
      
      // Parse length
      int length;
      try {
        length = Integer.parseInt(lengthHex, 16);
      } catch (NumberFormatException e) {
        pos++;
        continue;
      }
      
      // Validate length - must be at least 5 (4 for length + 1 for content)
      if (length < 5) {
        pos += 4;
        continue;
      }
      
      // Check if we have enough data
      if (pos + length > data.length) {
        break;
      }
      
      // Read the content after length prefix (length includes the 4-byte prefix itself)
      byte[] content = Arrays.copyOfRange(data, pos + 4, pos + length);
      
      if (content.length > 0) {
        // Check if first byte is a band indicator (1, 2, or 3)
        int firstByte = content[0] & 0xFF;
        
        if (firstByte == 1) {
          // Band 1: packfile data
          if (content.length > 1) {
            packfile.write(content, 1, content.length - 1);
          }
        } else if (firstByte == 2 || firstByte == 3) {
          // Band 2 (progress) and 3 (errors) - log to stderr
          String msg = new String(content, 1, content.length - 1, StandardCharsets.UTF_8);
          System.err.println("Server: " + msg);
        } else {
          // Not a side-band packet, might be NAK or other protocol message
          String msg = new String(content, StandardCharsets.UTF_8).trim();
          if (!msg.equals("NAK") && !msg.startsWith("acknowledgments")) {
            System.err.println("Protocol message: " + msg);
          }
        }
      }
      
      pos += length;
    }
    
    byte[] result = packfile.toByteArray();
    
    // If we got packfile data, return it
    if (result.length > 0) {
      return result;
    }
    
    // No packfile found - provide detailed error
    String preview = new String(data, 0, Math.min(data.length, 100), StandardCharsets.UTF_8);
    throw new IOException("No packfile data found in response (" + data.length + 
      " bytes). Preview: " + preview.replaceAll("[^\\x20-\\x7E]", "."));
  }
  
  // Unpack packfile and store objects
  static void unpackPackfile(byte[] packfile, File gitDir) throws Exception {
    if (packfile == null || packfile.length < 12) {
      throw new RuntimeException("Invalid packfile: too short (" + 
        (packfile == null ? "null" : packfile.length) + " bytes)");
    }
    
    // Wrap in PushbackInputStream to allow pushing back unused bytes after inflation
    PushbackInputStream in = new PushbackInputStream(new ByteArrayInputStream(packfile), 8192);
    
    byte[] header = new byte[12];
    if (in.read(header) != 12) {
      throw new RuntimeException("Invalid packfile: header too short (got " + packfile.length + " bytes)");
    }
    
    if (header[0] != 'P' || header[1] != 'A' || header[2] != 'C' || header[3] != 'K') {
      throw new RuntimeException("Invalid packfile signature (expected PACK, got: " + 
        new String(header, 0, 4) + ")");
    }
    
    // Read version
    int version = ((header[4] & 0xFF) << 24) | ((header[5] & 0xFF) << 16) | 
                  ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
    
    // Read object count
    int objectCount = ((header[8] & 0xFF) << 24) | ((header[9] & 0xFF) << 16) | 
                      ((header[10] & 0xFF) << 8) | (header[11] & 0xFF);
    
    // Parse objects
    List<PackObject> objects = new ArrayList<>();
    for (int i = 0; i < objectCount; i++) {
      PackObject obj = readPackObject(in);
      objects.add(obj);
    }
    
    // Resolve deltified objects
    Map<String, byte[]> objectData = new HashMap<>();
    for (PackObject obj : objects) {
      resolveObject(obj, objects, objectData, gitDir);
    }
  }
  
  // Read a pack object
  static PackObject readPackObject(InputStream in) throws IOException {
    // Read type and size
    int b = in.read();
    if (b == -1) {
      throw new IOException("Unexpected end of packfile");
    }
    
    int type = (b >> 4) & 0x07;
    long size = b & 0x0F;
    int shift = 4;
    
    while ((b & 0x80) != 0) {
      b = in.read();
      if (b == -1) {
        throw new IOException("Unexpected end of packfile while reading object size");
      }
      size |= ((long)(b & 0x7F)) << shift;
      shift += 7;
    }
    
    PackObject obj = new PackObject();
    obj.type = type;
    obj.size = size;
    
    // Handle different object types
    switch (type) {
      case 6 -> { // OFS_DELTA
        // Read negative offset
        long offset = in.read() & 0x7F;
        while ((b & 0x80) != 0) {
          b = in.read();
          offset = ((offset + 1) << 7) | (b & 0x7F);
        }
        obj.deltaOffset = offset;
        obj.data = readCompressedData(in);
      }
      case 7 -> { // REF_DELTA
        // Read base object SHA
        byte[] baseHash = new byte[20];
        in.read(baseHash);
        obj.baseHash = bytesToHex(baseHash);
        obj.data = readCompressedData(in);
      }
      default -> {
        // Regular object
        obj.data = readCompressedData(in);
      }
    }
    
    return obj;
  }
  
  // Read compressed data
  static byte[] readCompressedData(InputStream in) throws IOException {
    // We need to manually handle inflation to avoid consuming extra bytes from the stream
    java.util.zip.Inflater inflater = new java.util.zip.Inflater();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] inputBuffer = new byte[1024];
    byte[] outputBuffer = new byte[8192];
    int lastInputSize = 0;
    
    try {
      while (!inflater.finished()) {
        if (inflater.needsInput()) {
          int read = in.read(inputBuffer);
          if (read == -1) {
            break;
          }
          inflater.setInput(inputBuffer, 0, read);
          lastInputSize = read;
        }
        
        int decompressed = inflater.inflate(outputBuffer);
        if (decompressed > 0) {
          out.write(outputBuffer, 0, decompressed);
        }
      }
      
      // Push back unused bytes to the stream (if PushbackInputStream)
      int remaining = inflater.getRemaining();
      if (remaining > 0 && in instanceof PushbackInputStream) {
        // The remaining bytes are at the end of the last input we gave to the inflater
        byte[] unusedBytes = new byte[remaining];
        System.arraycopy(inputBuffer, lastInputSize - remaining, unusedBytes, 0, remaining);
        ((PushbackInputStream) in).unread(unusedBytes);
      }
      
    } catch (java.util.zip.DataFormatException e) {
      throw new IOException("Failed to decompress data", e);
    } finally {
      inflater.end();
    }
    
    return out.toByteArray();
  }
  
  // Resolve and store object
  static void resolveObject(PackObject obj, List<PackObject> allObjects, 
                           Map<String, byte[]> objectData, File gitDir) throws Exception {
    if (obj.resolved) return;
    
    byte[] data;
    String typeStr;
    
    if (obj.type == 6 || obj.type == 7) {
      // Deltified object - need to resolve base first
      byte[] baseData;
      
      if (obj.type == 6) {
        // OFS_DELTA - find base by offset
        return;
      } else {
        // REF_DELTA
        baseData = objectData.get(obj.baseHash);
        if (baseData == null) {
          // Try to load from disk
          baseData = loadObjectFromDisk(gitDir, obj.baseHash);
        }
        if (baseData == null) {
          return; // Can't resolve yet
        }
      }
      
      data = applyDelta(baseData, obj.data);
      
      String baseType = getObjectType(baseData);
      typeStr = baseType;
    } else {
      // Regular object
      data = obj.data;
      typeStr = switch (obj.type) {
        case 1 -> "commit";
        case 2 -> "tree";
        case 3 -> "blob";
        case 4 -> "tag";
        default -> throw new RuntimeException("Unknown object type: " + obj.type);
      };
    }
    
    // Create full object with header
    String header = typeStr + " " + data.length + "\0";
    byte[] headerBytes = header.getBytes();
    byte[] fullObject = new byte[headerBytes.length + data.length];
    System.arraycopy(headerBytes, 0, fullObject, 0, headerBytes.length);
    System.arraycopy(data, 0, fullObject, headerBytes.length, data.length);
    
    // Compute hash
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    byte[] hashBytes = digest.digest(fullObject);
    String hash = bytesToHex(hashBytes);
    
    // Store object
    String dirName = hash.substring(0, 2);
    String fileName = hash.substring(2);
    File objectDir = new File(gitDir, "objects/" + dirName);
    objectDir.mkdirs();
    
    File objectFile = new File(objectDir, fileName);
    try (FileOutputStream fos = new FileOutputStream(objectFile);
         DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
      dos.write(fullObject);
    }
    
    objectData.put(hash, data);
    obj.resolved = true;
    obj.hash = hash;
  }
  
  // Apply delta to base data
  static byte[] applyDelta(byte[] baseData, byte[] delta) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(delta);
    
    long srcSize = readVariableLength(in);
    
    long tgtSize = readVariableLength(in);
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    while (in.available() > 0) {
      int cmd = in.read();
      
      if ((cmd & 0x80) != 0) {
        // Copy instruction
        int offset = 0;
        int size = 0;
        
        if ((cmd & 0x01) != 0) offset |= in.read();
        if ((cmd & 0x02) != 0) offset |= in.read() << 8;
        if ((cmd & 0x04) != 0) offset |= in.read() << 16;
        if ((cmd & 0x08) != 0) offset |= in.read() << 24;
        
        if ((cmd & 0x10) != 0) size |= in.read();
        if ((cmd & 0x20) != 0) size |= in.read() << 8;
        if ((cmd & 0x40) != 0) size |= in.read() << 16;
        
        if (size == 0) size = 0x10000;
        
        out.write(baseData, offset, size);
      } else if (cmd > 0) {
        // Insert instruction
        byte[] data = new byte[cmd];
        in.read(data);
        out.write(data);
      }
    }
    
    return out.toByteArray();
  }
  
  // Read variable length integer
  static long readVariableLength(InputStream in) throws IOException {
    long value = 0;
    int shift = 0;
    int b;
    
    do {
      b = in.read();
      value |= ((long)(b & 0x7F)) << shift;
      shift += 7;
    } while ((b & 0x80) != 0);
    
    return value;
  }
  
  // Load object from disk
  static byte[] loadObjectFromDisk(File gitDir, String hash) {
    try {
      String dirName = hash.substring(0, 2);
      String fileName = hash.substring(2);
      File objectFile = new File(gitDir, "objects/" + dirName + "/" + fileName);
      
      if (!objectFile.exists()) return null;
      
      try (FileInputStream fis = new FileInputStream(objectFile);
           InflaterInputStream iis = new InflaterInputStream(fis)) {
        
        byte[] decompressed = iis.readAllBytes();
        
        // Find null byte
        int nullIndex = -1;
        for (int i = 0; i < decompressed.length; i++) {
          if (decompressed[i] == 0) {
            nullIndex = i;
            break;
          }
        }
        
        if (nullIndex == -1) return null;
        
        return Arrays.copyOfRange(decompressed, nullIndex + 1, decompressed.length);
      }
    } catch (IOException e) {
      return null;
    }
  }
  
  // Get object type from object data
  static String getObjectType(byte[] fullObjectWithHeader) {
    for (int i = 0; i < fullObjectWithHeader.length; i++) {
      if (fullObjectWithHeader[i] == ' ') {
        return new String(fullObjectWithHeader, 0, i);
      }
    }
    return "blob";
  }
  
  // Convert bytes to hex string
  static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
  
  // Checkout commit to working directory
  static void checkoutCommit(File workDir, File gitDir, String commitSha) throws Exception {
    // Read commit object
    byte[] commitData = loadObjectFromDisk(gitDir, commitSha);
    if (commitData == null) {
      throw new RuntimeException("Commit not found: " + commitSha);
    }
    
    // Parse commit to find tree
    String commitContent = new String(commitData);
    String[] lines = commitContent.split("\n");
    String treeSha = null;
    
    for (String line : lines) {
      if (line.startsWith("tree ")) {
        treeSha = line.substring(5).trim();
        break;
      }
    }
    
    if (treeSha == null) {
      throw new RuntimeException("No tree found in commit");
    }
    
    // Checkout tree
    checkoutTree(workDir, gitDir, treeSha, "");
  }
  
  // Recursively checkout tree
  static void checkoutTree(File workDir, File gitDir, String treeSha, String prefix) throws Exception {
    byte[] treeData = loadObjectFromDisk(gitDir, treeSha);
    if (treeData == null) {
      throw new RuntimeException("Tree not found: " + treeSha);
    }
    
    // Parse tree entries
    int pos = 0;
    while (pos < treeData.length) {
      // Read mode
      int spacePos = pos;
      while (spacePos < treeData.length && treeData[spacePos] != ' ') {
        spacePos++;
      }
      String mode = new String(treeData, pos, spacePos - pos);
      pos = spacePos + 1;
      
      // Read name
      int nullPos = pos;
      while (nullPos < treeData.length && treeData[nullPos] != 0) {
        nullPos++;
      }
      String name = new String(treeData, pos, nullPos - pos);
      pos = nullPos + 1;
      
      // Read hash
      if (pos + 20 > treeData.length) break;
      byte[] hashBytes = Arrays.copyOfRange(treeData, pos, pos + 20);
      String hash = bytesToHex(hashBytes);
      pos += 20;
      
      // Create file or directory
      String path = prefix + name;
      File file = new File(workDir, path);
      
      if (mode.equals("40000")) {
        // Directory
        file.mkdirs();
        checkoutTree(workDir, gitDir, hash, path + "/");
      } else {
        // File
        byte[] blobData = loadObjectFromDisk(gitDir, hash);
        if (blobData != null) {
          Files.write(file.toPath(), blobData);
          
          // Set executable if needed
          if (mode.equals("100755")) {
            file.setExecutable(true);
          }
        }
      }
    }
  }
  
  // Pack object class
  static class PackObject {
    int type;
    long size;
    byte[] data;
    long deltaOffset;
    String baseHash;
    boolean resolved;
    String hash;
  }
}
