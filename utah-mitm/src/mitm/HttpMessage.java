package mitm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HttpMessage
{

  private Pattern m_httpHeaderPattern;
  private Pattern m_headerLinePattern;
  
  private HashMap<String, ArrayList<String>> m_KVStore;
  
  public HttpMessage()
  {
    m_KVStore = new HashMap<String, ArrayList<String>>();
    m_httpHeaderPattern = Pattern.compile("^([A-Z]+.*\r\n\r\n)(.*)", Pattern.DOTALL);
    m_headerLinePattern = Pattern.compile("^([^:]+):\\s(.+)");
  }
  
  public HttpMessage(byte[] data) throws Exception
  {
    super();
    
    String dataAsString = new String(data, "UTF-8");
    Matcher headerMatcher = m_httpHeaderPattern.matcher(dataAsString);
    
    if (headerMatcher.find())
    {
      String[] headers = headerMatcher.group(1).split("\r\n");
      for (String headerLine : headers)
      {
        if (!m_KVStore.containsKey("Top"))
        {
          appendHeader("Top", headerLine);
        }
        else
        {
          Matcher headerLineMatcher = m_headerLinePattern.matcher(headerLine);
          
          if (headerLineMatcher.find())
          {
            String header = headerLineMatcher.group(1);
            String value = headerLineMatcher.group(2);
            
            this.appendHeader(header, value);
          }
        }
      }
      
      String body = headerMatcher.group(2);
      if (body.length() > 0)
      {
        this.appendHeader("Body", body);
      }
    }
    else
    {
      throw new IllegalArgumentException("data does not contain header!");
    }
  }
  
  public byte[] getData() throws Exception
  {
    StringBuilder sb = new StringBuilder();
    sb.append(getTopLine() + "\r\n");
    
    for (Entry<String, ArrayList<String>> kv : m_KVStore.entrySet())
    {
      if (!kv.getKey().equals("Top") && kv.getKey().equals("Body"))
      {
        for (String value : kv.getValue())
        {
          sb.append(kv.getKey() + ": " + value + "\r\n");
        }
      }
    }
    
    sb.append("\r\n");
    
    if (m_KVStore.containsKey("Body"))
    {
      sb.append(m_KVStore.get("Body").get(0));
    }
    
    return sb.toString().getBytes("UTF-8");
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
