package models;

public class PackObject {
  private int type;
  private long size;
  private byte[] data;
  private long deltaOffset;
  private String baseHash;
  private boolean resolved;
  private String hash;
  
  /** 
   * @return int
   */
  public int getType() {
    return type;
  }
  
  /** 
   * @param type
   */
  public void setType(int type) {
    this.type = type;
  }
  
  /** 
   * @return long
   */
  public long getSize() {
    return size;
  }
  
  /** 
   * @param size
   */
  public void setSize(long size) {
    this.size = size;
  }
  
  /** 
   * @return byte[]
   */
  public byte[] getData() {
    return data;
  }
  
  /** 
   * @param data
   */
  public void setData(byte[] data) {
    this.data = data;
  }
  
  /** 
   * @return long
   */
  public long getDeltaOffset() {
    return deltaOffset;
  }
  
  /** 
   * @param deltaOffset
   */
  public void setDeltaOffset(long deltaOffset) {
    this.deltaOffset = deltaOffset;
  }
  
  /** 
   * @return String
   */
  public String getBaseHash() {
    return baseHash;
  }
  
  /** 
   * @param baseHash
   */
  public void setBaseHash(String baseHash) {
    this.baseHash = baseHash;
  }
  
  /** 
   * @return boolean
   */
  public boolean isResolved() {
    return resolved;
  }
  
  /** 
   * @param resolved
   */
  public void setResolved(boolean resolved) {
    this.resolved = resolved;
  }
  
  /** 
   * @return String
   */
  public String getHash() {
    return hash;
  }
  
  /** 
   * @param hash
   */
  public void setHash(String hash) {
    this.hash = hash;
  }
}
