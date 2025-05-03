import java.io.File;
import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import service.SyncService;
import service.impl.XSyncServiceImpl;

public class Test {

  private static final Log log = LogFactory.getLog(Test.class);

  public static void main(String[] args) {
    SyncService service;
    try {
      service =
          new XSyncServiceImpl()
              .setExpectedChunkSize(128)
              .setRootDir(new File("C:\\XSync"))
              .setCacheDir(new File("C:\\XSync\\cache"))
              .authenticate("email@gmail.com", "123456");
      Boolean ignored = service.sync(new File("C:\\XSync\\helloworld"));
    } catch (IOException e) {
      log.error(e);
    }
  }
}
