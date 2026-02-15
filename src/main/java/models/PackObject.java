package models;

public class PackObject {
  private int type;
  private long size;
  private byte[] data;
  private long deltaOffset;
  private String baseHash;
  private boolean resolved;
  private String hash;
  
  public int getType() {
    return type;
  }
  
  public void setType(int type) {
    this.type = type;
  }
  
  public long getSize() {
    return size;
  }
  
  public void setSize(long size) {
    this.size = size;
  }
  
  public byte[] getData() {
    return data;
  }
  
  public void setData(byte[] data) {
    this.data = data;
  }
  
  public long getDeltaOffset() {
    return deltaOffset;
  }
  
  public void setDeltaOffset(long deltaOffset) {
    this.deltaOffset = deltaOffset;
  }
  
  public String getBaseHash() {
    return baseHash;
  }
  
  public void setBaseHash(String baseHash) {
    this.baseHash = baseHash;
  }
  
  public boolean isResolved() {
    return resolved;
  }
  
  public void setResolved(boolean resolved) {
    this.resolved = resolved;
  }
  
  public String getHash() {
    return hash;
  }
  
  public void setHash(String hash) {
    this.hash = hash;
  }
}
