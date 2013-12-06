package mitm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Strippers
{

  public static byte[] removeAcceptEncoding(byte[] request) throws Exception
  {
    String data = new String(request, "UTF-8");
    
    Pattern p = Pattern.compile("(.*)Accept-Encoding: [,a-zA-Z]+\r\n(.*)", Pattern.DOTALL);
    Matcher m = p.matcher(data);
    
    if (m.find())
    {
      System.err.println("-- stripped \"Accept Encoding\"");
      data = m.group(1) + m.group(2);
    }
    
    return data.getBytes("UTF-8");
  }
  
  public static byte[] removeCookie(byte[] request) throws Exception
  {
    String data = new String(request, "UTF-8");
    
    Pattern p = Pattern.compile("(.*)Cookie: [^\r]+\r\n(.*)", Pattern.DOTALL);
    Matcher m = p.matcher(data);

    if (m.find())
    {
      System.err.println("-- stripped \"Cookie\"");
      data = m.group(1) + m.group(2);
    }
    
    return data.getBytes("UTF-8");
  }
  
}
