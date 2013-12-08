package mitm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class HttpMessage
{
  private HashMap<String, ArrayList<String>> m_KVStore;
  private byte[] m_bodyBytes;
  
  public HttpMessage()
  {
    m_KVStore = new HashMap<String, ArrayList<String>>();
    m_bodyBytes = new byte[0];
  }
  
  public String getHeadersAsString() throws Exception
  {
    StringBuilder sb = new StringBuilder();
    sb.append(getTopLine() + "\r\n");
    
    for (Entry<String, ArrayList<String>> kv : m_KVStore.entrySet())
    {
      if (!kv.getKey().equals("Top"))
      {
        for (String value : kv.getValue())
        {
          sb.append(kv.getKey() + ": " + value + "\r\n");
        }
      }
    }
    
    sb.append("\r\n");
    return sb.toString();
  }
  
  public String getBodyAsString() throws Exception 
  {
    return new String(m_bodyBytes, "UTF-8");
  }
  
  public void setBodyBytes(byte[] body) 
  {
    m_bodyBytes = body;
  }
  
  public String getDataAsString() throws Exception
  {
    return getHeadersAsString() + getBodyAsString();
  }
  
  public byte[] getData() throws Exception
  {
    byte[] headerData = getHeadersAsString().getBytes("UTF-8");
    byte[] allData = new byte[headerData.length + m_bodyBytes.length];
    System.arraycopy(headerData, 0, allData, 0, headerData.length);
    System.arraycopy(m_bodyBytes, 0, allData, headerData.length, m_bodyBytes.length);
    return allData;
  }
  
  public List<String> get(String header)
  {
    List<String> list = m_KVStore.get(header);
    return list == null ? new ArrayList<String>() : list;
  }
  
  public String getTopLine()
  {
    return m_KVStore.get("Top").get(0);
  }
  
  public boolean isRequest()
  {
    return !getTopLine().startsWith("HTTP");
  }
  
  public boolean containsHeader(String header)
  {
    return m_KVStore.containsKey(header);
  }
  
  public void appendHeader(String header, String value)
  {
    if (m_KVStore.containsKey(header))
    {
      m_KVStore.get(header).add(value);
    }
    else
    {
      ArrayList<String> values = new ArrayList<String>();
      values.add(value);
      m_KVStore.put(header, values);
    }
  }
  
  public void replaceHeader(String header, String... values)
  {
    removeHeader(header);
    
    for (String value : values)
    {
      appendHeader(header, value);
    }
  }
  
  public void removeHeader(String header)
  {
    m_KVStore.remove(header);
  }
}
