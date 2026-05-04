package models;

/**
 * Represents a single object record parsed from a git packfile.
 *
 * <p>A {@code PackObject} is initially populated with metadata read from the packfile
 * header (type, declared size, and compressed data). Delta objects additionally carry
 * either a negative stream offset ({@link #deltaOffset} for OFS_DELTA) or the hex SHA-1
 * of their base ({@link #baseHash} for REF_DELTA). After {@link utils.PackfileParser}
 * resolves all delta chains, {@link #resolved} is set to {@code true} and {@link #hash}
 * holds the final 40-character hex SHA-1.
 */
public class PackObject {
  private int type;
  private long size;
  private byte[] data;
  private long deltaOffset;
  private String baseHash;
  private boolean resolved;
  private String hash;

  /**
   * Returns the object type identifier as defined by the packfile format:
   * {@code 1} = commit, {@code 2} = tree, {@code 3} = blob, {@code 4} = tag,
   * {@code 6} = OFS_DELTA, {@code 7} = REF_DELTA.
   *
   * @return the numeric object type
   */
  public int getType() {
    return type;
  }

  /**
   * Sets the object type identifier.
   *
   * @param type the numeric object type (1–4 for base objects, 6–7 for delta objects)
   */
  public void setType(int type) {
    this.type = type;
  }

  /**
   * Returns the declared uncompressed size of the object as encoded in the packfile header.
   *
   * @return the declared object size in bytes
   */
  public long getSize() {
    return size;
  }

  /**
   * Sets the declared uncompressed size of the object.
   *
   * @param size the declared object size in bytes
   */
  public void setSize(long size) {
    this.size = size;
  }

  /**
   * Returns the decompressed object data. For delta objects this is the raw delta
   * instructions; for base objects this is the final content.
   *
   * @return the decompressed data bytes, or {@code null} if not yet populated
   */
  public byte[] getData() {
    return data;
  }

  /**
   * Sets the decompressed object data.
   *
   * @param data the decompressed data bytes
   */
  public void setData(byte[] data) {
    this.data = data;
  }

  /**
   * Returns the negative stream offset used by OFS_DELTA objects to locate their base.
   *
   * @return the delta stream offset, or {@code 0} for non-OFS_DELTA objects
   */
  public long getDeltaOffset() {
    return deltaOffset;
  }

  /**
   * Sets the negative stream offset for an OFS_DELTA object.
   *
   * @param deltaOffset the delta stream offset
   */
  public void setDeltaOffset(long deltaOffset) {
    this.deltaOffset = deltaOffset;
  }

  /**
   * Returns the 40-character hex SHA-1 hash of the base object for REF_DELTA objects.
   *
   * @return the base object hash, or {@code null} for non-REF_DELTA objects
   */
  public String getBaseHash() {
    return baseHash;
  }

  /**
   * Sets the 40-character hex SHA-1 hash of the base object for a REF_DELTA object.
   *
   * @param baseHash the base object hash
   */
  public void setBaseHash(String baseHash) {
    this.baseHash = baseHash;
  }

  /**
   * Returns {@code true} if this object has been fully resolved and written to the object store.
   *
   * @return {@code true} after {@link utils.PackfileParser} has resolved and stored the object
   */
  public boolean isResolved() {
    return resolved;
  }

  /**
   * Marks this object as resolved ({@code true}) or pending ({@code false}).
   *
   * @param resolved {@code true} once the object has been written to the object store
   */
  public void setResolved(boolean resolved) {
    this.resolved = resolved;
  }

  /**
   * Returns the 40-character hex SHA-1 hash computed after the object is resolved.
   *
   * @return the object hash, or {@code null} if the object has not been resolved yet
   */
  public String getHash() {
    return hash;
  }

  /**
   * Sets the computed 40-character hex SHA-1 hash of the object.
   *
   * @param hash the object hash
   */
  public void setHash(String hash) {
    this.hash = hash;
  }
}
