package mitm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HttpMessage
{

  private Pattern m_httpHeaderPattern;
  private Pattern m_headerLinePattern;
  private String m_firstHeader;
  
  private HashMap<String, ArrayList<String>> m_KVStore;
  
  public HttpMessage(byte[] data) throws Exception
  {
    m_KVStore = new HashMap<String, ArrayList<String>>();
    m_httpHeaderPattern = Pattern.compile("^([A-Z]+.*\r\n\r\n)(.*)", Pattern.DOTALL);
    m_headerLinePattern = Pattern.compile("^([^:]+):\\s([^\r]+)\r\n");
    
    String dataAsString = new String(data, "UTF-8");
    
    Matcher headerMatcher = m_httpHeaderPattern.matcher(dataAsString);
    
    if (headerMatcher.find())
    {
      String[] headers = headerMatcher.group(1).split("\r\n");
      for (String headerLine : headers)
      {
        Matcher headerLineMatcher = m_headerLinePattern.matcher(headerLine);
        
        if (headerLineMatcher.find())
        {
          String header = headerLineMatcher.group(1);
          String value = headerLineMatcher.group(2);
          
          if (m_firstHeader == null)
          {
            m_firstHeader = header;
          }
          
          this.appendHeader(header, value);
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
    
    return sb.toString().getBytes("UTF-8");
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
