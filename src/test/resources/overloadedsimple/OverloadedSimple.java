@SuppressWarnings("all")
public final class OverloadedSimple {
  public void setClientInfo(String key, String value) {}

  public void setClientInfo(Object p) {}

  public String getClientInfo(String name) {
    return null;
  }

  public Object getClientInfo() {
    return null;
  }
}
