package mitm;

public interface IDataFilter
{
  public HttpMessage handle(ConnectionDetails connectionDetails, HttpMessage message) throws Exception;
  
  public void connectionOpened(ConnectionDetails connectionDetails);

  public void connectionClosed(ConnectionDetails connectionDetails);
}
