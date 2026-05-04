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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

/**
 * Parses binary git packfiles, resolves delta chains, and stores resulting objects
 * in the {@code .git/objects} loose-object store.
 *
 * <p>Supports object types 1–4 (commit, tree, blob, tag) and type 7 (REF_DELTA).
 * Type 6 (OFS_DELTA) is not yet implemented.
 */
public class PackfileParser {

  private PackfileParser() {}

  /**
   * Unpacks all objects from a git packfile and writes them to the object store.
   *
   * <p>Reads the pack header to determine the object count, reads each raw object,
   * resolves delta chains in repeated passes, and stores the results in
   * {@code .git/objects} as individual DEFLATE-compressed loose objects.
   *
   * @param packfile the raw bytes of the packfile (must begin with the {@code PACK} header)
   * @param gitDir   the {@code .git} directory of the repository
   * @throws IOException if the packfile is invalid, an object cannot be decompressed,
   *                     or a resolved object cannot be written to disk
   */
  public static void unpackPackfile(byte[] packfile, File gitDir) throws IOException {
    if (packfile == null || packfile.length < 12) {
      throw new IOException("Invalid packfile: too short (" +
        (packfile == null ? "null" : packfile.length) + " bytes)");
    }

    PushbackInputStream in = new PushbackInputStream(new ByteArrayInputStream(packfile), 8192);
    int objectCount = readPackHeader(in, packfile.length);

    List<PackObject> objects = new ArrayList<>();
    for (int i = 0; i < objectCount; i++) {
      objects.add(readPackObject(in));
    }

    Map<String, PackObject> objectsByHash = new HashMap<>();
    preComputeNonDeltaHashes(objects, objectsByHash);

    Map<String, byte[]> objectData = new HashMap<>();
    resolveAllObjectsPasses(objects, objectData, gitDir);
  }

  /**
   * Reads and validates the 12-byte packfile header, returning the object count.
   *
   * @param in             the input stream positioned at the start of the packfile
   * @param packfileLength the total length of the packfile (used only in error messages)
   * @return the number of objects declared in the header
   * @throws IOException if the header is too short or does not begin with {@code PACK}
   */
  private static int readPackHeader(PushbackInputStream in, int packfileLength) throws IOException {
    byte[] header = new byte[12];
    if (in.read(header) != 12) {
      throw new IOException("Invalid packfile: header too short (got " + packfileLength + " bytes)");
    }
    if (header[0] != 'P' || header[1] != 'A' || header[2] != 'C' || header[3] != 'K') {
      throw new IOException("Invalid packfile signature (expected PACK, got: " +
        new String(header, 0, 4) + ")");
    }
    return ((header[8] & 0xFF) << 24) | ((header[9] & 0xFF) << 16) |
           ((header[10] & 0xFF) << 8) | (header[11] & 0xFF);
  }

  /**
   * Computes and caches SHA-1 hashes for all non-delta objects in the list.
   *
   * <p>Objects with type 1–4 are given the standard git header ({@code <type> <size>\0}),
   * hashed, and stored in {@code objectsByHash} for later delta resolution.
   *
   * @param objects        the list of parsed pack objects
   * @param objectsByHash  the map to populate with {@code hash → PackObject} entries
   * @throws IOException if the SHA-1 algorithm is unavailable
   */
  private static void preComputeNonDeltaHashes(List<PackObject> objects,
      Map<String, PackObject> objectsByHash) throws IOException {
    for (PackObject obj : objects) {
      if (obj.getType() >= 1 && obj.getType() <= 4) {
        String typeStr = switch (obj.getType()) {
          case 1 -> "commit";
          case 2 -> "tree";
          case 3 -> "blob";
          case 4 -> "tag";
          default -> throw new IOException("Unknown object type: " + obj.getType());
        };
        String objHeader = typeStr + " " + obj.getData().length + "\0";
        byte[] headerBytes = objHeader.getBytes();
        byte[] fullObject = new byte[headerBytes.length + obj.getData().length];
        System.arraycopy(headerBytes, 0, fullObject, 0, headerBytes.length);
        System.arraycopy(obj.getData(), 0, fullObject, headerBytes.length, obj.getData().length);
        MessageDigest digest;
        try {
          digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
          throw new IOException("SHA-1 algorithm not available", e);
        }
        byte[] hashBytes = digest.digest(fullObject);
        String hash = GitObjectUtils.bytesToHex(hashBytes);
        obj.setHash(hash);
        objectsByHash.put(hash, obj);
      }
    }
  }

  /**
   * Attempts to resolve a single pack object, suppressing "base not found" failures.
   *
   * @param obj        the object to resolve
   * @param objectData the map of already-resolved object data keyed by hash
   * @param gitDir     the {@code .git} directory of the repository
   * @return {@code true} if the object was resolved; {@code false} if the base object
   *         was not yet available
   * @throws IOException if resolution fails for any reason other than a missing base
   */
  private static boolean tryResolveObject(PackObject obj, Map<String, byte[]> objectData,
      File gitDir) throws IOException {
    try {
      resolveObject(obj, objectData, gitDir);
      return true;
    } catch (IOException e) {
      if (e.getMessage() != null && e.getMessage().startsWith("Base object not found")) {
        return false;
      }
      throw e;
    }
  }

  /**
   * Resolves all objects through repeated passes until no further progress is made.
   *
   * <p>In each pass, every unresolved object is attempted. The loop terminates when
   * all objects are resolved or no additional objects were resolved in a full pass
   * (indicating an unresolvable cycle or permanently missing base).
   *
   * @param objects    the list of all parsed pack objects
   * @param objectData the map accumulating resolved data keyed by hash
   * @param gitDir     the {@code .git} directory of the repository
   * @throws IOException if any resolvable object cannot be written to disk
   */
  private static void resolveAllObjectsPasses(List<PackObject> objects,
      Map<String, byte[]> objectData, File gitDir) throws IOException {
    int unresolvedCount = Integer.MAX_VALUE;
    int newUnresolvedCount;
    do {
      newUnresolvedCount = 0;
      for (PackObject obj : objects) {
        if (!obj.isResolved() && !tryResolveObject(obj, objectData, gitDir)) {
          newUnresolvedCount++;
        }
      }
      if (newUnresolvedCount > 0 && newUnresolvedCount >= unresolvedCount) {
        break;
      }
      unresolvedCount = newUnresolvedCount;
    } while (newUnresolvedCount > 0);
  }

  /**
   * Reads a single object record from a packfile input stream.
   *
   * <p>Decodes the variable-length type/size header, then reads the object payload:
   * for OFS_DELTA (type 6) the negative base offset is read; for REF_DELTA (type 7)
   * the 20-byte base SHA-1 is read; all other types read only compressed data.
   *
   * @param in the input stream positioned at the start of an object record
   * @return a {@link PackObject} populated with type, size, and raw data
   * @throws IOException if the stream ends unexpectedly
   */
  private static PackObject readPackObject(InputStream in) throws IOException {
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
    
    switch (type) {
      case 6 -> {
        b = in.read();
        long offset = b & 0x7F;
        while ((b & 0x80) != 0) {
          b = in.read();
          offset = ((offset + 1) << 7) | (b & 0x7F);
        }
        obj.setDeltaOffset(offset);
        obj.setData(readCompressedData(in));
      }
      case 7 -> {
        byte[] baseHash = in.readNBytes(20);
        if (baseHash.length != 20) {
          throw new IOException("Unexpected end of packfile while reading base hash");
        }
        obj.setBaseHash(GitObjectUtils.bytesToHex(baseHash));
        obj.setData(readCompressedData(in));
      }
      default -> obj.setData(readCompressedData(in));
    }
    
    return obj;
  }
  
  /**
   * Reads and decompresses a DEFLATE-compressed data block from the stream.
   *
   * <p>Uses a manual {@link java.util.zip.Inflater} rather than
   * {@link java.util.zip.InflaterInputStream} so that any unconsumed bytes are pushed
   * back onto the underlying {@link PushbackInputStream}, leaving it correctly
   * positioned for the next object record.
   *
   * @param in the input stream positioned at the start of the compressed data
   * @return the decompressed bytes
   * @throws IOException if the data cannot be decompressed or the stream ends unexpectedly
   */
  private static byte[] readCompressedData(InputStream in) throws IOException {
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
      
      int remaining = inflater.getRemaining();
      if (remaining > 0 && in instanceof PushbackInputStream pis) {
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
   * Resolves a single pack object and writes it to the loose-object store.
   *
   * <p>For delta objects (types 6 and 7) the base object is located either in the
   * already-resolved map or on disk, the delta is applied, and the resulting type is
   * inherited from the base. For non-delta objects the raw data and type string are
   * used directly. The resolved object is DEFLATE-compressed and stored under
   * {@code .git/objects/<prefix>/<suffix>}.
   *
   * @param obj        the pack object to resolve
   * @param objectData the map accumulating resolved object data keyed by hash
   * @param gitDir     the {@code .git} directory of the repository
   * @throws IOException                  if the base object is missing, the delta cannot
   *                                      be applied, or the object cannot be written
   * @throws UnsupportedOperationException if the object has type 6 (OFS_DELTA)
   */
  private static void resolveObject(PackObject obj,
                           Map<String, byte[]> objectData, File gitDir) throws IOException {
    if (obj.isResolved()) return;

    byte[] data;
    String typeStr;

    if (obj.getType() == 6 || obj.getType() == 7) {
      byte[] baseData;

      if (obj.getType() == 6) {
        throw new UnsupportedOperationException("OFS_DELTA not yet implemented");
      } else {
        String baseHash = obj.getBaseHash();

        baseData = objectData.get(baseHash);

        if (baseData == null) {
          baseData = GitObjectUtils.loadObjectFromDisk(gitDir, baseHash);
        }

        if (baseData == null || baseData.length == 0) {
          throw new IOException("Base object not found: " + baseHash);
        }
      }

      data = applyDelta(baseData, obj.getData());

      String baseType = GitObjectUtils.getObjectType(baseData);
      typeStr = baseType;
    } else {
      data = obj.getData();
      typeStr = switch (obj.getType()) {
        case 1 -> "commit";
        case 2 -> "tree";
        case 3 -> "blob";
        case 4 -> "tag";
        default -> throw new IOException("Unknown object type: " + obj.getType());
      };
    }

    String header = typeStr + " " + data.length + "\0";
    byte[] headerBytes = header.getBytes();
    byte[] fullObject = new byte[headerBytes.length + data.length];
    System.arraycopy(headerBytes, 0, fullObject, 0, headerBytes.length);
    System.arraycopy(data, 0, fullObject, headerBytes.length, data.length);

    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-1 algorithm not available", e);
    }
    byte[] hashBytes = digest.digest(fullObject);
    String hash = GitObjectUtils.bytesToHex(hashBytes);

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
   * Executes a single copy instruction from a delta stream.
   *
   * <p>Reads the offset and size fields indicated by the set bits in {@code cmd},
   * then appends the corresponding slice of {@code baseData} to {@code out}.
   * A decoded size of zero is treated as {@code 0x10000} per the git delta format.
   *
   * @param cmd      the copy command byte (bit 7 must be set)
   * @param baseData the base object data to copy from
   * @param in       the delta byte stream positioned after the command byte
   * @param out      the output stream receiving the reconstructed content
   */
  private static void applyCopyCommand(int cmd, byte[] baseData, ByteArrayInputStream in,
      ByteArrayOutputStream out) {
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
  }

  /**
   * Applies a binary delta to base data and returns the reconstructed content.
   *
   * <p>The delta stream begins with two variable-length integers (base size and result
   * size, both ignored here), followed by a sequence of copy and insert instructions.
   * A copy instruction (bit 7 set) is handled by {@link #applyCopyCommand}; an insert
   * instruction (bits 0–6, non-zero) appends the next {@code cmd} literal bytes.
   *
   * @param baseData the original base object content
   * @param delta    the delta bytes to apply
   * @return the reconstructed content after applying the delta
   * @throws IOException if the delta stream is malformed
   */
  private static byte[] applyDelta(byte[] baseData, byte[] delta) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(delta);
    readVariableLength(in);
    readVariableLength(in);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    while (in.available() > 0) {
      int cmd = in.read();
      if ((cmd & 0x80) != 0) {
        applyCopyCommand(cmd, baseData, in, out);
      } else if (cmd > 0) {
        out.write(in.readNBytes(cmd));
      }
    }

    return out.toByteArray();
  }
  
  /**
   * Reads a variable-length (little-endian base-128) integer from the stream.
   *
   * <p>Each byte contributes 7 bits of value; the high bit of each byte indicates
   * whether more bytes follow.
   *
   * @param in the input stream to read from
   * @return the decoded non-negative integer value
   * @throws IOException if the stream ends before the value is complete
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
