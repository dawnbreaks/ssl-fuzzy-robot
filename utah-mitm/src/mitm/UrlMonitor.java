package mitm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlMonitor
{
  private static UrlMonitor m_instance = null;
  private Set<String> m_strippedUrls;
  
  private UrlMonitor() {
    m_strippedUrls = new HashSet<String>();
  }
  
  public static synchronized UrlMonitor getInstance() {
    if(m_instance == null) {
      m_instance = new UrlMonitor();
    }
    
    return m_instance;
  }
  
  public synchronized boolean add(String client, String url) {
    return m_strippedUrls.add(client + url);
  }
  
  public void addSecureLink(String client, String url)
  {
//    int methodIndex = url.indexOf("//") + 2;
//    String method = url.substring(0, methodIndex);
//    
//    int pathIndex = url.indexOf("/", methodIndex);
//    String host = url.substring(methodIndex, pathIndex);
//    String path = url.substring(pathIndex);
    System.err.println("-- added secure url: " + url);
    add(client, url);
  }

  public boolean isSecureLink(String client, String url)
  {
    return m_strippedUrls.contains(client + url);
  }
}
