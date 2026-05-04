package utils;

import models.PackObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

public class PackfileParser {
  
  /**
   * Unpack packfile and store objects
   */
  public static void unpackPackfile(byte[] packfile, File gitDir) throws Exception {
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
    Map<String, PackObject> objectsByHash = new HashMap<>();
    
    for (int i = 0; i < objectCount; i++) {
      PackObject obj = readPackObject(in);
      objects.add(obj);
    }
    
    // Pre-compute hashes for non-delta objects so we can reference them
    for (PackObject obj : objects) {
      if (obj.getType() >= 1 && obj.getType() <= 4) {
        String typeStr = switch (obj.getType()) {
          case 1 -> "commit";
          case 2 -> "tree";
          case 3 -> "blob";
          case 4 -> "tag";
          default -> throw new RuntimeException("Unknown object type: " + obj.getType());
        };
        String objHeader = typeStr + " " + obj.getData().length + "\0";
        byte[] headerBytes = objHeader.getBytes();
        byte[] fullObject = new byte[headerBytes.length + obj.getData().length];
        System.arraycopy(headerBytes, 0, fullObject, 0, headerBytes.length);
        System.arraycopy(obj.getData(), 0, fullObject, headerBytes.length, obj.getData().length);
        
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hashBytes = digest.digest(fullObject);
        String hash = GitObjectUtils.bytesToHex(hashBytes);
        obj.setHash(hash);
        objectsByHash.put(hash, obj);
      }
    }
    
    // Resolve all objects (may need multiple passes for delta chains)
    Map<String, byte[]> objectData = new HashMap<>();
    int unresolvedCount = Integer.MAX_VALUE;
    int newUnresolvedCount;
    do {
      newUnresolvedCount = 0;
      for (PackObject obj : objects) {
        if (!obj.isResolved()) {
          try {
            resolveObject(obj, objects, objectData, gitDir, objectsByHash);
          } catch (RuntimeException e) {
            // Base not yet available - will try again in next pass
            if (e.getMessage() != null && e.getMessage().startsWith("Base object not found")) {
              newUnresolvedCount++;
            } else {
              throw e;
            }
          }
        }
      }
      // Guard against infinite loop: stop if no progress was made
      if (newUnresolvedCount > 0 && newUnresolvedCount >= unresolvedCount) {
        break;
      }
      unresolvedCount = newUnresolvedCount;
    } while (newUnresolvedCount > 0);
  }
  
  /**
   * Read a pack object
   */
  private static PackObject readPackObject(InputStream in) throws IOException {
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
    obj.setType(type);
    obj.setSize(size);
    
    // Handle different object types
    switch (type) {
      case 6 -> { // OFS_DELTA
        // Read negative offset - must read a fresh byte (not the size byte `b`)
        b = in.read();
        long offset = b & 0x7F;
        while ((b & 0x80) != 0) {
          b = in.read();
          offset = ((offset + 1) << 7) | (b & 0x7F);
        }
        obj.setDeltaOffset(offset);
        obj.setData(readCompressedData(in));
      }
      case 7 -> { // REF_DELTA
        // Read base object SHA
        byte[] baseHash = new byte[20];
        in.read(baseHash);
        obj.setBaseHash(GitObjectUtils.bytesToHex(baseHash));
        obj.setData(readCompressedData(in));
      }
      default -> {
        // Regular object
        obj.setData(readCompressedData(in));
      }
    }
    
    return obj;
  }
  
  /**
   * Read compressed data
   */
  private static byte[] readCompressedData(InputStream in) throws IOException {
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
        PushbackInputStream pis = (PushbackInputStream) in;
        int offset = lastInputSize - remaining;
        pis.unread(inputBuffer, offset, remaining);
      }
      
    } catch (java.util.zip.DataFormatException e) {
      throw new IOException("Failed to decompress data", e);
    } finally {
      inflater.end();
    }
    
    return out.toByteArray();
  }
  
  /**
   * Resolve and store object
   */
  private static void resolveObject(PackObject obj, List<PackObject> allObjects, 
                           Map<String, byte[]> objectData, File gitDir,
                           Map<String, PackObject> objectsByHash) throws Exception {
    if (obj.isResolved()) return;
    
    byte[] data;
    String typeStr;
    
    if (obj.getType() == 6 || obj.getType() == 7) {
      // Deltified object - need to resolve base first
      byte[] baseData;
      
      if (obj.getType() == 6) {
        // OFS_DELTA - find base by offset
        throw new RuntimeException("OFS_DELTA not yet implemented");
      } else {
        // REF_DELTA - load base by hash
        String baseHash = obj.getBaseHash();
        
        // Check if base is in objectData (already resolved in this unpack)
        baseData = objectData.get(baseHash);
        
        // Check if base is on disk  
        if (baseData == null) {
          baseData = GitObjectUtils.loadObjectFromDisk(gitDir, baseHash);
        }
        
        if (baseData == null || baseData.length == 0) {
          throw new RuntimeException("Base object not found: " + baseHash);
        }
      }
      
      data = applyDelta(baseData, obj.getData());
      
      String baseType = GitObjectUtils.getObjectType(baseData);
      typeStr = baseType;
    } else {
      // Regular object
      data = obj.getData();
      typeStr = switch (obj.getType()) {
        case 1 -> "commit";
        case 2 -> "tree";
        case 3 -> "blob";
        case 4 -> "tag";
        default -> throw new RuntimeException("Unknown object type: " + obj.getType());
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
    String hash = GitObjectUtils.bytesToHex(hashBytes);
    
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
    obj.setResolved(true);
    obj.setHash(hash);
  }
  
  /**
   * Apply delta to base data
   */
  private static byte[] applyDelta(byte[] baseData, byte[] delta) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(delta);
    
    long srcSize = readVariableLength(in);
    
    long tgtSize = readVariableLength(in);
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    while (in.available() > 0) {
      int cmd = in.read();
      
      if ((cmd & 0x80) != 0) {
        // Copy command
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
        // Insert command
        byte[] newData = new byte[cmd];
        in.read(newData);
        out.write(newData);
      }
    }
    
    return out.toByteArray();
  }
  
  /**
   * Read variable length integer
   */
  private static long readVariableLength(InputStream in) throws IOException {
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
}
