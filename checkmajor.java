import java.io.*;
import java.nio.file.*;
public class CheckMajor {
  public static void main(String[] args) throws Exception {
    for (String a : args) {
      Path p = Paths.get(a);
      try (DataInputStream in = new DataInputStream(new FileInputStream(p.toFile()))) {
        int magic = in.readInt();
        int minor = in.readUnsignedShort();
        int major = in.readUnsignedShort();
        System.out.println(p + " -> major=" + major + ", minor=" + minor);
      }
    }
  }
}
