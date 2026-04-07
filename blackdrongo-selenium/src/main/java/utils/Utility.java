package utils;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import static com.google.common.io.Resources.getResource;

public class Utility {

    public static String getAbsolutePath(String path) throws URISyntaxException {
        File file = new File(path);
        return file.getAbsolutePath();
    }
}
