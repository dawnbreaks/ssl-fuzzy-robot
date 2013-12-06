package mitm;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// https://github.com/moxie0/sslstrip/blob/master/sslstrip/CookieCleaner.py

public class CookieCleaner
{
  private static CookieCleaner m_instance = null;
  private Set<String> m_cleanedCookies;
  
  private CookieCleaner() {
    m_cleanedCookies = new HashSet<String>();
  }
  
  public static synchronized CookieCleaner getInstance() {
    if(m_instance == null) {
      m_instance = new CookieCleaner();
    }
    
    return m_instance;
  }
  
  public synchronized boolean add(String client, String domain) {
    return m_cleanedCookies.add(client + domain);
  }
  
  // TODO: return http message class
  public List<String> getExpireHeaders(String method, String client, String host, HttpMessage headers, String path) {
    String domain = getDomainFor(host);
    add(client, domain);
    
    List<String> expiredCookies = new ArrayList<String>();
    ArrayList<String> cookies = new ArrayList<String>();
    //cookies = headers.get("cookie");
    
    for(String cookie : cookies) {
      List<String> expireHeaders = new ArrayList<String>();
      for(String subCookie : cookie.split(";")) {
        String cookieKey = subCookie.split("=")[0].trim();
        String expireHeadersForCookie = getExpireCookieStringFor(cookieKey, host, domain, path);
        expireHeaders.add(expireHeadersForCookie);
      }
      expiredCookies.add(join(expireHeaders, ";"));
    }
    
    
    return expiredCookies;
  }

  public String getDomainFor(String host)
  {
    String[] hostParts = host.split(".");
    String domain = host;
    
    if(hostParts.length >= 2) 
    {
      domain = "." + hostParts[hostParts.length - 2] + "." + hostParts[hostParts.length - 1];
    }
    
    return domain;
  }
  
  public String getExpireCookieStringFor(String cookieKey, String host,
      String domain, String path)
  {
    // TODO Auto-generated method stub
    return null;
  }


  public boolean hasCookies(String headers)
  {
    // TODO: use http message class
    // if headers contains cookie key
    // then return true
    return false;
  }
  
  public boolean isClean(String method, String client, String host, String headers) {
    if(method == "POST")      return true;  
    if(!hasCookies(headers))  return true;
    
    return m_cleanedCookies.contains(client + getDomainFor(host));
  }
  
  public String join(List<String> strings, String delimiter) {
    if(strings.isEmpty()) 
    {
      return "";
    }
    
    String message = strings.get(0);
    for(int i = 1; i < strings.size(); i++) {
      message += delimiter + strings.get(i);
    }
    
    return message;
  }
 
}
