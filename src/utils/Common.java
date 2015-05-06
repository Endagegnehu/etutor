/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 *
 * @author pfialho
 */
public class Common {

    /**
     * Force deletion of directory
     *
     * @param path
     * @return
     */
    public static boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    try {
                        Files.delete(files[i].toPath());
                    } catch (IOException ex) {
                        System.err.println("file " + files[i].toPath() + " not deleted: " + ex);
                    }
                }
            }
        }
        return (path.delete());
    }

    public static void setLoggerPath(Logger logger, String path) {
        File logpath = new File(path);
        try {
            logpath.createNewFile();
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            return;
        }

        FileHandler fh;
        try {
            // This block configure the logger with handler and formatter
            fh = new FileHandler(path, true);
            logger.addHandler(fh);
            logger.setLevel(Level.ALL);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException e) {
            System.err.println(e.getMessage());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Adds the specified path to the java library path
     *
     * @param pathToAdd the path to add
     * @throws Exception
     */
    public static void addLibraryPath(String pathToAdd) throws Exception {
        final Field usrPathsField = ClassLoader.class.getDeclaredField("usr_paths");
        usrPathsField.setAccessible(true);

        //get array of paths
        final String[] paths = (String[]) usrPathsField.get(null);

        //check if the path to add is already present
        for (String path : paths) {
            if (path.equals(pathToAdd)) {
                return;
            }
        }

        //add the new path
        final String[] newPaths = Arrays.copyOf(paths, paths.length + 1);
        newPaths[newPaths.length - 1] = pathToAdd;
        usrPathsField.set(null, newPaths);
    }

    public static void stringToFile(String contents, String dest, String enc) {
        File destParent = new File(dest).getParentFile();
        if (!destParent.exists()) {
            destParent.mkdir();
        }

        PrintWriter out = null;
        try {
            out = new PrintWriter(dest, enc);
            out.print(contents);
        } catch (UnsupportedEncodingException ex) {
            System.err.println("Error writing string to file: unsupported encoding: " + ex.getLocalizedMessage());
        } catch (FileNotFoundException ex) {
            System.err.println("Error writing string to file: file not found: " + ex.getLocalizedMessage());
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    public static void stringToFile(String contents, String dest) {
        PrintWriter out = null;
        try {
            out = new PrintWriter(dest);
            out.println(contents);
        } catch (FileNotFoundException ex) {
            System.err.println("Error writing string to file: " + ex.getLocalizedMessage());
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    public static String fileToString(String stmTMpath, String enc) throws Exception {
        StringBuilder fileData = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(stmTMpath)), enc))) {
            char[] buf = new char[1024];
            int numRead = 0;
            while ((numRead = reader.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
            }
        }

        return fileData.toString();
    }

    public static String fileToString(String owloutpath2) {

        StringBuilder ontstring = new StringBuilder();
        Scanner scanner = null;
        try {
//            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(owloutpath2)), "US-ASCII"));
            scanner = new Scanner(new File(owloutpath2));
        } catch (FileNotFoundException ex) {
            System.err.println("Error reading file as a string: " + ex.getLocalizedMessage());
        } catch (Exception ex1) {
            System.err.println("Other error reading file as a string: : " + ex1.getLocalizedMessage());
        }

        try {
            while (scanner.hasNextLine()) {
                ontstring.append(scanner.nextLine()).append("\r\n");
            }
        } finally {
            scanner.close();
        }

        return ontstring.toString();
    }

    public static String getUniqueId(String s) throws NoSuchAlgorithmException {
        //string unique id: from http://www.javapractices.com/topic/TopicAction.do?Id=56
        SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        byte[] result2 = sha.digest(s.getBytes());
        StringBuilder result = new StringBuilder();
        char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        for (int idx = 0; idx < result2.length; ++idx) {
            byte b = result2[idx];
            result.append(digits[ (b & 0xf0) >> 4]);
            result.append(digits[ b & 0x0f]);
        }

        return result.toString();
    }

    public static ArrayList<File> getFoldersWithFilesOnly(String rootpath) throws Exception {
        ArrayList<File> res = new ArrayList<>();

        File root = new File(rootpath);
        if (!root.exists()) {
            throw new Exception("path " + rootpath + " not found.");
        }

        boolean hasDir = false;
        for (File f : root.listFiles()) {
            if (f.isDirectory()) {
                hasDir = true;
                res.addAll(getFoldersWithFilesOnly(f.getAbsolutePath()));
            } else {
                if (!hasDir) {
                    res.add(f.getParentFile());
                    break;
                }
            }
        }

        return res;
    }

    public static String getHTML(String urlToRead) throws Exception {
        URL url;
        HttpURLConnection conn;
        BufferedReader rd;
        String line;
        String result = "";
//      try {


        String url1 = urlToRead.substring(0, urlToRead.indexOf("?"));
        String params = urlToRead.substring(urlToRead.indexOf("?") + 1, urlToRead.length());

        url = new URI("http", url1, "", params, null).toURL();       //"http","www.google.com","/ig/api","weather=São Paulo", null
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30 * 1000);
        conn.setReadTimeout(30 * 1000);
        rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        while ((line = rd.readLine()) != null) {
            result += line;
        }
        rd.close();
//      } catch (Exception e) {
//         e.printStackTrace();
//      }
        return result;
    }
}
