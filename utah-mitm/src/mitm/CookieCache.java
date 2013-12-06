package mitm;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

public class CookieCache
{
  private static CookieCache m_instance = null;
  private static ConcurrentHashMap<String, ArrayList<String>> m_cookies;
  
  private CookieCache() {
    m_cookies = new ConcurrentHashMap<String, ArrayList<String>>();
  }
  
  public static synchronized CookieCache getInstance() {
    if(m_instance == null) {
      m_instance = new CookieCache();
    }
    
    return m_instance;
  }
  
  public ArrayList<String> getCookies(String client) {
    return m_cookies.get(client);
  }
  
  public void setCookies(String client, ArrayList<String> cookies) {
    m_cookies.put(client, cookies);
  }
}
