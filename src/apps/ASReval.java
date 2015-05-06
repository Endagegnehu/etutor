/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package apps;

import agent.LogFile;
import agent.StmLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import qa.QA;
import speech.ASR;
import utils.Common;
import utils.NLP;

/**
 *
 * @author pfialho
 */
public class ASReval {

    private final String debugFilesFolder;
    private final String audioFilenameEndsWith;
    private final String asrTransEndsWith;
    private final String dumpPath;
    private static DecimalFormat dec = new DecimalFormat("###.###");
    private static boolean hasFrameID = false;

    // TRS mode
    public ASReval(String debugFilesFolder, String audioFilenameEndsWith, String dumpPath) {
        this.debugFilesFolder = debugFilesFolder;
        this.audioFilenameEndsWith = audioFilenameEndsWith;
        this.asrTransEndsWith = null;
        this.dumpPath = dumpPath;
    }

    // generic mode
    public ASReval(String debugFilesFolder, String audioFilename, String asrTransEndsWith, String dumpPath) {
        this.debugFilesFolder = debugFilesFolder;
        this.audioFilenameEndsWith = audioFilename;
        this.asrTransEndsWith = asrTransEndsWith;
        this.dumpPath = dumpPath;
    }

    public void organizeDebugFiles(int filesPerFolder) throws Exception {
        File path = new File(debugFilesFolder);
        ArrayList<File> debugFolders = Common.getFoldersWithFilesOnly(debugFilesFolder);

        //inline alternative for file/folder sort
        Collections.sort(debugFolders, new Comparator<File>() {
            DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'_'HH'h'mm'm'ss's'");

            @Override
            public int compare(File a, File b) {
                Date date = null;
                Date date2 = null;
                try {
                    date = inputFormat.parse(a.getName());
                    date2 = inputFormat.parse(b.getName());
                } catch (ParseException ex) {
                    System.err.println(ex.getMessage());
                }
                return date.compareTo(date2);
            }
        });

        for (int i = 0; i < debugFolders.size(); i++) {
            File currFolder = debugFolders.get(i);

            if (currFolder.listFiles().length == filesPerFolder) {
                if (new File(currFolder, "out-received.raw").length() < 10000) {       //10k
                    if (!Common.deleteDirectory(currFolder)) {
                        System.err.println(currFolder.getName() + "\tnot deleted! (and should have been...due to utils.Common)");
                    } else {
                        System.err.println(currFolder.getName() + "\tdeleted!");
                    }
                }

                continue;
            }

            int j = i;
            File nextFolder = debugFolders.get(j + 1);
            if (nextFolder.listFiles().length < filesPerFolder) {
                for (File currFolderFile : currFolder.listFiles()) {
                    Files.copy(currFolderFile.toPath(), nextFolder.toPath().resolve(currFolderFile.toPath().getFileName()));
                }

                System.err.println("- " + currFolder.getName() + "\tmoved to " + nextFolder.getName());

                if (!Common.deleteDirectory(currFolder)) {
                    System.err.println(currFolder.getName() + "\tnot deleted! (and should have been...due to utils.Common)");
                } else {
                    System.err.println(currFolder.getName() + "\tdeleted!");
                }
            } else {
                System.err.println(currFolder.getName() + "\tunable to solve");
            }
        }

    }

    //returns [< /.raw/, /.results/, etc >, ...]
    public TreeSet<LogFile> getDebugFiles() throws Exception {       //String debugFilesFolder, String audioFilename, String asrTransEndsWith
        TreeSet<LogFile> res = new TreeSet<LogFile>();

        File path = new File(debugFilesFolder);
        ArrayList<File> debugFolders = Common.getFoldersWithFilesOnly(debugFilesFolder);

        for (File dfolder : debugFolders) {
            LogFile logData = new LogFile();

//            String fullpath = dfolder.getAbsolutePath();
            logData.id = dfolder.getName();      //fullpath.substring(debugFilesFolder.length(), fullpath.length())

            String trsXmlPath = null;       //for trs mode only
            for (File logFile : dfolder.listFiles()) {
                if (logFile.getName().endsWith(audioFilenameEndsWith)) {
                    logData.audioPath = logFile.getAbsolutePath();
                }

                if (asrTransEndsWith != null && logFile.getName().endsWith(asrTransEndsWith)) {     // generic mode
                    logData.resultsPath = logFile.getAbsolutePath();
                } else {
                    if (logFile.getName().endsWith(".trs")) {                                       // trs mode
                        trsXmlPath = logFile.getAbsolutePath();
                    }
                }
            }

            //<editor-fold defaultstate="collapsed" desc="for TRS - unfinished">
            if (asrTransEndsWith == null && trsXmlPath != null) {               // trs mode
                logData.trsTimeTrans = new HashMap<>();

                DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
                domFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                domFactory.setNamespaceAware(true); // never forget this!
                DocumentBuilder builder = domFactory.newDocumentBuilder();
                Document doc = builder.parse(new File(trsXmlPath));
                XPathFactory factory = XPathFactory.newInstance();
                XPath xpath = factory.newXPath();

                XPathExpression expr = xpath.compile("//Turn");       // /@text
                Object result = expr.evaluate(doc, XPathConstants.NODESET);
                NodeList nodes = (NodeList) result;
                for (int i = 0; i < nodes.getLength(); i++) {
                    float turnEndTime = Float.parseFloat(nodes.item(i).getAttributes().getNamedItem("endTime").getTextContent());

                    ArrayList<Map.Entry<String, String>> allturntexts = new ArrayList<>();
                    String[] tmpallturntexts = nodes.item(i).getTextContent().trim().split("\\n+");

                    if (tmpallturntexts.length % 2 != 0) {
                        System.err.println("error: odd asr / manual transcript pair (ie, one is missing) in: " + dfolder);
                        break;
                    }

                    for (int jj = 0; jj < tmpallturntexts.length - 1; jj++) {
                        String ta = NLP.normPunctLCaseDMarks(tmpallturntexts[jj]).trim();
                        String tm = NLP.normPunctLCaseDMarks(tmpallturntexts[++jj]).trim();

                        allturntexts.add(new AbstractMap.SimpleEntry<>(ta, tm));
                    }

                    ArrayList<Map.Entry<Float, Float>> allturntimes = new ArrayList<>();
                    NodeList ns = nodes.item(i).getChildNodes();
                    float ti = -1;
                    float tf = -1;
                    for (int ii = 0; ii < ns.getLength(); ii++) {
                        if (ns.item(ii).getNodeName().trim().equals("Sync")) {
                            if (ti < 0) {
                                ti = Float.parseFloat(ns.item(ii).getAttributes().getNamedItem("time").getTextContent());
                            } else {
                                tf = Float.parseFloat(ns.item(ii).getAttributes().getNamedItem("time").getTextContent());
                            }

                            if (ti >= 0 && tf > 0) {
                                allturntimes.add(new AbstractMap.SimpleEntry<>(ti, tf));
                                ti = tf;
                                tf = -1;
                            }
                        }
                    }

                    if (allturntexts.size() != allturntimes.size()) {
                        System.err.println("error: unmapped time or transcript in: " + dfolder);
                        break;
                    }

                    String ttstext = nodes.item(i).getAttributes().getNamedItem("text").getTextContent().trim(); //getNodeValue().trim();
                    //IAgent.lang l = IAgent.lang.valueOf(nodes.item(i).getAttributes().getNamedItem("lang").getTextContent().toLowerCase().trim());
                    String l = "";

                    XPathExpression expr2 = xpath.compile("/asrttskeys/ttsval[@lang='" + l + "']/*/text()");
                    Object result2 = expr2.evaluate(doc, XPathConstants.NODESET);
                    NodeList nodes2 = (NodeList) result2;

                    HashSet<String> asrkeys = new HashSet<>();
                    for (int j = 0; j < nodes2.getLength(); j++) {
                        String asrkey = nodes2.item(j).getNodeValue().trim();
                        asrkeys.add(asrkey);
                    }
                }
            }
            //</editor-fold>

            if (logData.id != null && logData.audioPath != null
                    && ((logData.trsTimeTrans != null && !logData.trsTimeTrans.isEmpty()) || logData.resultsPath != null)) {
                res.add(logData);
            } else {
                System.err.println("error parsing log for: " + dfolder);
            }
        }

        return res;
    }

    public static ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> getAudioSegments(String audioFilePath, String fullWAVdumpDir, String segWAVrootDumpDir) {
        ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> res = new ArrayList<>();
        boolean dumpAccurateSegTimings = true;      //if false, sequential segments will be forced

        File fileIn = new File(audioFilePath);
        try {
            FileInputStream fis = new FileInputStream(audioFilePath);
            AudioInputStream audioInputStream = new AudioInputStream(fis, ASR.defaultRecConf, fileIn.length() / 2);

            int frameLength = (int) audioInputStream.getFrameLength();
            int frameSize = (int) audioInputStream.getFormat().getFrameSize();
            byte[] audioBytes = new byte[frameLength * frameSize];      //read entire file at once

            try {
                int numBytesRead = audioInputStream.read(audioBytes);

                //<editor-fold defaultstate="collapsed" desc="dump full WAV">
                if (fullWAVdumpDir != null) {
                    //                    File f = new File(audioFilePath);
                    //                    String f11 = audioFilePath.substring(0, audioFilePath.lastIndexOf("\\") - 1);
                    //                    String f2 = audioFilePath.substring(f11.lastIndexOf("\\") + 1, f11.length());
                    //                    File d = new File("dump\\logTestPTwav\\" + f2 + "\\");
                    //                    d.mkdir();
                    //                    File out = new File("dump\\logTestPTwav\\" + f2 + "\\out-received.wav");
                    File out = new File(fullWAVdumpDir + "\\out-received.wav");
                    ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
                    AudioInputStream ai = new AudioInputStream(bais, ASR.defaultRecConf, audioBytes.length);
                    AudioSystem.write(ai, AudioFileFormat.Type.WAVE, out);
                }
                //</editor-fold>

                //get sample values
                int sindx = 0;
                ByteArrayOutputStream tmpSegs = new ByteArrayOutputStream();
                float prevSegStart = 0f;
                int initrim = 16;       // good: 16 | confortably: 30         //must be divisable by 2
                int endtrim = 30;       // good: 30 | confortably: 50

                if (!hasFrameID) {
                    //<editor-fold defaultstate="collapsed" desc="!hasFrameID">
                    for (int i = 0; i < audioBytes.length;) {
                        int low = (int) audioBytes[i];
                        tmpSegs.write(audioBytes[i]);
                        i++;
                        int high = (int) audioBytes[i];
                        tmpSegs.write(audioBytes[i]);
                        i++;
                        //                    samples[sindx] = (high << 8) + (low & 0x00ff);

                        int sampleVal = (high << 8) + (low & 0x00ff);
                        if (sampleVal == 32000) {       // && Math.round((float) sindx / 16000) > 0
                            //<editor-fold defaultstate="collapsed" desc="comment">
                            //                        res.add(((float) sindx / 16000));

                            //                        if (!firstSegDone) {
                            //                            firstSegDone = true;
                            //                            tmpSegs.reset();
                            //                            sindx++;
                            //                            continue;
                            //                        }
                            //</editor-fold>

//                            float sini = prevSegStart;
//                            float sfim = ((float) sindx / ASR.defaultRecConf.getSampleRate());

                            byte[] full = tmpSegs.toByteArray();
                            if (full.length > (ASR.minSegLenSamples * 2)) {
                                //trim EOS marker artifacts
                                byte[] subset = Arrays.copyOfRange(full, initrim, full.length - endtrim);     // ideally: (full, 12, full.length - 30)

                                res.add(new AbstractMap.SimpleEntry<>(
                                        new AbstractMap.SimpleEntry<>(prevSegStart, ((float) (sindx
                                        - (dumpAccurateSegTimings ? endtrim / 2 : 0))
                                        / ASR.defaultRecConf.getSampleRate())), subset));

                                //<editor-fold defaultstate="collapsed" desc="dump segment WAV">
                                if (segWAVrootDumpDir != null) {
                                    File out = new File(segWAVrootDumpDir + "\\" + dec.format(prevSegStart) + "_" + dec.format(((float) (sindx - 15) / ASR.defaultRecConf.getSampleRate())) + ".wav");
                                    ByteArrayInputStream bais = new ByteArrayInputStream(subset);
                                    AudioInputStream ai = new AudioInputStream(bais, ASR.defaultRecConf, subset.length);
                                    AudioSystem.write(ai, AudioFileFormat.Type.WAVE, out);
                                }
                                //</editor-fold>                                
                            } else {
                                System.err.println("\tdiscarded segment at: " + prevSegStart + " - "
                                        + (prevSegStart + ((float) (full.length / 2) / ASR.defaultRecConf.getSampleRate())));

                                if (!dumpAccurateSegTimings) {
                                    tmpSegs.reset();
                                    continue;
                                }
                            }

                            prevSegStart = ((float) (sindx
                                    + (dumpAccurateSegTimings ? initrim / 2 : 0))
                                    / ASR.defaultRecConf.getSampleRate());
                            tmpSegs.reset();
                        }
                        sindx++;
                    }

                    byte[] full = tmpSegs.toByteArray();
                    if (full.length > (ASR.minSegLenSamples * 2)) {
                        byte[] subset = Arrays.copyOfRange(full, initrim, full.length);

                        res.add(new AbstractMap.SimpleEntry<>(
                                new AbstractMap.SimpleEntry<>(prevSegStart, ((float) frameLength / ASR.defaultRecConf.getSampleRate())), subset));

                        //<editor-fold defaultstate="collapsed" desc="dump segment WAV">
                        if (segWAVrootDumpDir != null) {
                            File out = new File(segWAVrootDumpDir + "\\" + prevSegStart + "_" + ((float) frameLength / ASR.defaultRecConf.getSampleRate()) + ".wav");
                            ByteArrayInputStream bais = new ByteArrayInputStream(subset);
                            AudioInputStream ai = new AudioInputStream(bais, ASR.defaultRecConf, subset.length);
                            AudioSystem.write(ai, AudioFileFormat.Type.WAVE, out);
                        }
                        //</editor-fold> 
                    } else {
                        System.err.println("\tdiscarded segment at: " + prevSegStart + " - " + ((float) frameLength / ASR.defaultRecConf.getSampleRate()));
                    }
                    //                res.add(((float) frameLength / 16000));
                    //</editor-fold>
                }

            } catch (Exception ex) {
                System.err.println(ex);
            }
        } catch (Exception e) {
            System.err.println(e);
        }

        return res;
    }

    public static ArrayList<byte[]> getAudioSegments(String audioFilePath, String segWAVrootDumpDir) {
        ArrayList<byte[]> res = new ArrayList<>();

        File fileIn = new File(audioFilePath);
        try {
            FileInputStream fis = new FileInputStream(audioFilePath);
            AudioInputStream audioInputStream = new AudioInputStream(fis, ASR.defaultRecConf, fileIn.length() / 2);

            int frameLength = (int) audioInputStream.getFrameLength();
            int frameSize = (int) audioInputStream.getFormat().getFrameSize();
            byte[] audioBytes = new byte[frameLength * frameSize];      //read entire file at once

            try {
                int numBytesRead = audioInputStream.read(audioBytes);

                ByteArrayOutputStream tmpSegs = new ByteArrayOutputStream();

                if (hasFrameID) {
                    //<editor-fold defaultstate="collapsed" desc="hasFrameID">
                    int audimusFrameSizeInShorts = (int) (ASR.defaultRecConf.getSampleRate() * ASR.frameSizeSegs);        //160

                    ByteBuffer bb = ByteBuffer.wrap(audioBytes);
                    bb.order((ASR.defaultRecConf.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN));     //ByteOrder.LITTLE_ENDIAN

                    bb.getInt();
                    while (bb.hasRemaining()) {

                        for (int kk = 0; kk < audimusFrameSizeInShorts; kk++) {
                            if (!bb.hasRemaining()) {
                                break;
                            }
                            short x = bb.getShort();
                            byte[] b = new byte[2];
                            b[0] = (byte) (x & 0xff);
                            b[1] = (byte) ((x >> 8) & 0xff);
                            tmpSegs.write(b);
                        }

                        if (!bb.hasRemaining()) {
                            break;
                        }

                        int aa = bb.getInt();
                        if (aa == 0) {
                            res.add(tmpSegs.toByteArray());

                            //<editor-fold defaultstate="collapsed" desc="dump segs">
                            if (segWAVrootDumpDir != null) {
                                File out = new File(segWAVrootDumpDir + "\\" + res.size() + "_" + ((float) frameLength / ASR.defaultRecConf.getSampleRate()) + ".wav");
                                ByteArrayInputStream bais = new ByteArrayInputStream(tmpSegs.toByteArray());
                                AudioInputStream ai = new AudioInputStream(bais, ASR.defaultRecConf, audioBytes.length);
                                AudioSystem.write(ai, AudioFileFormat.Type.WAVE, out);
                            }
                            //</editor-fold>

                            tmpSegs.reset();
                        }
                    }
                    res.add(tmpSegs.toByteArray());

                    //<editor-fold defaultstate="collapsed" desc="dump last seg">
                    if (segWAVrootDumpDir != null) {
                        File out = new File(segWAVrootDumpDir + "\\" + res.size() + "_" + ((float) frameLength / ASR.defaultRecConf.getSampleRate()) + ".wav");
                        ByteArrayInputStream bais = new ByteArrayInputStream(tmpSegs.toByteArray());
                        AudioInputStream ai = new AudioInputStream(bais, ASR.defaultRecConf, audioBytes.length);
                        AudioSystem.write(ai, AudioFileFormat.Type.WAVE, out);
                    }
                    //</editor-fold>
                }

                if (!hasFrameID) {
                    //<editor-fold defaultstate="collapsed" desc="!hasFrameID">
                    //                int sindx = 0;
                    for (int i = 0; i < audioBytes.length;) {
                        short low = (short) audioBytes[i];
                        tmpSegs.write(audioBytes[i]);
                        i++;
                        short high = (short) audioBytes[i];
                        tmpSegs.write(audioBytes[i]);
                        i++;

                        int sampleVal = (high << 8) + (low & 0x00ff);
                        if (sampleVal == 32000) {
                            byte[] full = tmpSegs.toByteArray();
//                            if (full.length > 1000) {        //bad segment inference
                            byte[] subset = null;
                            int trimsize = 42;          //must be multiples of 2!
                            if (full.length > (trimsize)) {
                                subset = Arrays.copyOfRange(full, 12, full.length - 30);
                            } else {
                                subset = full;
                            }

                            res.add(subset);       // subset      tmpSegs.toByteArray()
//                            }

                            //<editor-fold defaultstate="collapsed" desc="dump segs">
                            if (segWAVrootDumpDir != null) {
                                File out = new File(segWAVrootDumpDir + "\\" + res.size() + "_" + ((float) frameLength / ASR.defaultRecConf.getSampleRate()) + ".wav");
                                ByteArrayInputStream bais = new ByteArrayInputStream(subset);
                                AudioInputStream ai = new AudioInputStream(bais, ASR.defaultRecConf, subset.length);
                                AudioSystem.write(ai, AudioFileFormat.Type.WAVE, out);
                            }
                            //</editor-fold>

                            tmpSegs.reset();
                        }

                        //                    sindx++;
                    }

                    byte[] full = tmpSegs.toByteArray();
                    byte[] subset = null;
                    int trimsize = 42;          //must be multiples of 2!
                    if (full.length > (trimsize)) {
                        subset = Arrays.copyOfRange(full, 12, full.length - 30);
                    } else {
                        subset = full;
                    }

                    res.add(subset);
                    //</editor-fold>
                }
//                if(res.isEmpty())
//                    res.add(b.toByteArray());

            } catch (Exception ex) {
                System.err.println(ex);
            }
        } catch (Exception e) {
            System.err.println(e);
        }

        return res;
    }

    public static String cleanSilences(String resultsFilePath) {
        String res = utils.Common.fileToString(resultsFilePath);
        return res.replaceAll("<s> </s> \r\n", "");
    }

    public static String getSTM(LogFile l, ASR asr) throws Exception {
        System.err.println("- " + l.id);

        String stm = "";    // "/" + l.id + "/out-received.wav 1 asrTranscript 0 0.001 <o,F0,male> \n";

        ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> segstarts = getAudioSegments(l.audioPath, null, null);
//        segstarts.remove(0);                // MONSERRATE LOGS ONLY!?!

        for (int i = 0; i < segstarts.size(); i++) {
//            String recog = "";
            String recog = asr.recognizeBytes(segstarts.get(i).getValue()).getRecog();
            if (recog.equals("_REPEAT_")) {
                recog = "";
            }

            stm += "/" + l.id + "/out-received.wav 1 asrTranscript "
                    + (i == 0 ? "0.002" : dec.format(segstarts.get(i).getKey().getKey()).replace(',', '.'))
                    + " "
                    + dec.format(segstarts.get(i).getKey().getValue()).replace(',', '.')
                    + " <o,f0,unknown> " + recog + " \n";
        }

        return stm;
    }

    public void getSTMs(final ASR asr) throws Exception {
        final File outdir = new File(dumpPath + "/"
                + debugFilesFolder.replaceAll("[\\/:]", "")
                + "_" + new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime()) + "/");
        if (!outdir.mkdir()) {
            throw new Exception("unable to create dir: " + outdir.getAbsolutePath() + " ...exiting.");
        }

        TreeSet<LogFile> logs = getDebugFiles();
        for (final LogFile l : logs) {
//            if (asr == null) {
//                Common.stringToFile(getSTM(l), outdir.getAbsolutePath() + "/" + l.id + ".stm");
//            } else {

            Common.stringToFile(getSTM(l, asr), outdir.getAbsolutePath() + "/" + l.id + ".stm");

//            }
        }
    }

    public ArrayList<StmLine> getCleanSTM(String stmTMpath) throws Exception {
        ArrayList<StmLine> res2 = new ArrayList<>();

        String stmstr = Common.fileToString(stmTMpath, "ISO-8859-1");

        stmstr = stmstr.replaceAll(";;.*\r\n", "");                     // STM comments
        stmstr = stmstr.replaceAll(".*0.000 0.001 <o,f0,>\r\n", "");        //dummy first segment, on some STMs
//        stmstr = stmstr.replaceAll(".*s 1 [^0-9a].*", "");                  // all whose speaker isn't 2.... nor a(srTrans...) - ex: <nontrans> for whole segment

//        String res = "";
        int linenum = 0;
        for (String s : stmstr.split("\r\n")) {
            String sorig = s;
            linenum++;

//            if(linenum == 39){
//                System.err.println();
//            }

            StmLine sl = new StmLine();

            if (s.contains("_Crian")
                    || s.contains("_nonnativePT")
                    || s.contains("_nonnativeEN")
                    || s.contains("_multispkr")
                    || s.contains("_Multispkr")
                    || s.contains("[language=")
                    || s.contains("_crian")) {
                sl.nonStdSpkr = "TO DO";
            }

            Pattern p2 = Pattern.compile("^[^\\s]+ . [^\\s]+ ([^\\s]+) ([^\\s]+) ");
            Matcher m2 = p2.matcher(s);
            if (m2.find()) {
                sl.sini = m2.group(1);
                sl.sfim = m2.group(2);

                try {
                    Float.parseFloat(sl.sini);
                } catch (Exception ex) {
                    throw new Exception("stm: seg start: not a number");
                }
                try {
                    Float.parseFloat(sl.sfim);
                } catch (Exception ex) {
                    throw new Exception("stm: seg end: not a number");
                }
            } else {
                throw new Exception("stm: can't find seg start or end");
            }

            //        stmstr = stmstr.replaceAll(".*_Criança.*", "");
            //                stmstr = stmstr.replaceAll(".*[b].*", "");
            s = s.replaceAll(".*s 1 [^0-9a].*", "");                  // all whose speaker isn't 2.... nor a(srTrans...) - ex: <nontrans> for whole segment
            s = s.replaceAll(".*<o,f0,unknown> ", "");
            s = s.replaceAll(".*<o,f5,unknown> ", "");
            s = s.replaceAll("\\[language[^\\s]+\\]\\s", "");

            StringBuffer sb = new StringBuffer();
            Pattern p = Pattern.compile("(\\s*\\[[^\\]]+\\]\\s*)");
            Matcher m = p.matcher(s);
            while (m.find()) {
                m.appendReplacement(sb, " ");
            }
            m.appendTail(sb);

            String a = sb.toString();
            a = a.replaceAll("\\%[^\\>]+", " ");
            a = a.replaceAll("\\&[^\\&]+", " ");
            a = a.replaceAll("[\\p{Punct}&&[^-']]+", " ");

            a = a.replaceAll(" [^\\s]+\\- ", " ").replaceAll(" [^\\s]+\\-$", " ").replaceAll("[^\\s]+\\-$", " ");        // " abc- " " abc-\n" "abc-"
            a = a.replaceAll(" [^\\s]+\\-\\>", " ").replaceAll("\\<[^\\s]+\\- ", " ");                              // " abc->" "<abc- "
            a = a.replaceAll(" -[^\\s]+ ", " ").replaceAll(" -[^\\s]+$", "").replaceAll("^\\-[^\\s]+", " ");       // " -abc " " -abc\n" "-abc"                         "\\s-" "-\r\n"
            a = a.replaceAll("\\<-[^\\s]+ ", " ").replaceAll("\\<-[^\\s]+ ", " ");                                  // "<-abc " "<-abc "

//            res += (a.replaceAll(" +", " ").toLowerCase().trim() + "\n");
//            res2.add(new AbstractMap.SimpleEntry<>((a.replaceAll(" +", " ").toLowerCase().trim()), tosave));

            sl.cleanTM = a.replaceAll(" +", " ").toLowerCase().trim();
            res2.add(sl);

            if (sl.cleanTM.trim().isEmpty() && !sorig.contains("ignore_time_segment_in_scoring") && !sorig.contains("inter_segment_gap")) {
                System.err.println("warn: sorig=" + sorig + " marked as empty");
            }

        }

        return res2;
    }

    public void sendASRtoQA(List<ASR> asrs, List<QA> qa) throws Exception {
        DateFormat outputFormat = new SimpleDateFormat("yy'Y'MM'M'dd'D'HH'h'mm'm'ss's'");
        DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'_'HH'h'mm'm'ss's'");

        Map<String, ASR> evalTasks2 = new HashMap<>();
        for (ASR a : asrs) {
            evalTasks2.put(a.getID(), a);
        }

        final Map<String, ASR> evalTasks = evalTasks2;
        HashMap<String, Integer> finished = new HashMap<>();
        TreeSet<LogFile> logs = getDebugFiles();
        for (LogFile l : logs) {
//                    if (!l.id.startsWith("2012-08-25") && !l.id.startsWith("2012-08-31")) {
//                        continue;
//                    }

            System.err.println("- log: " + l.id);
            Date date = inputFormat.parse(l.id);
            final String outDate = outputFormat.format(date);

            int logAdvance2in1 = 0;
            if (finished.containsKey(outDate)) {
                logAdvance2in1 = finished.get(outDate) + 1;
            }

            ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> segs = getAudioSegments(l.audioPath, null, null);
//            segs.remove(0);                // MONSERRATE LOGS ONLY!?!

            int j2 = 0;
            for (int j = 0; j < segs.size(); j++) {
                final byte[] sound = segs.get(j).getValue();

                final int jj = j + logAdvance2in1;
                j2 = jj;

                String src = "speech_" + outDate + "_#" + jj + "_" + dec.format(segs.get(j).getKey().getValue()).replace(',', '.') + "s";
                for (final String et : evalTasks.keySet()) {
                    try {
                        String id = et + "_" + "fullTM";
                        String recog = evalTasks.get(et).recognizeBytes(sound).getRecog();

                        for (QA qa1 : qa) {
                            qa1.ask(id, src, recog);
                            System.out.println("sent: " + recog + " | id=" + id + " | source=" + src);
                        }
                    } catch (Exception ex) {
                        System.err.println(et + " error: " + ex);
                    }
                }
            }

            finished.put(outDate, j2);

            System.gc();
        }

    }

    public void sendASRtoQA(ASR asr, QA qa) throws Exception {
        DateFormat outputFormat = new SimpleDateFormat("yy'Y'MM'M'dd'D'HH'h'mm'm'ss's'");
        DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'_'HH'h'mm'm'ss's'");

        HashMap<String, Integer> finished = new HashMap<>();
        TreeSet<LogFile> logs = getDebugFiles();
        for (LogFile l : logs) {
//            if (l.id.startsWith("2012-07-26") ){//|| l.id.startsWith("2012-03-19")) {
//                continue;
//            }

//            if (!l.id.startsWith("2012-08-31")) {
//                continue;
//            }

//            System.err.println("-\r\n-\t log: " + l.id + "\r\n-");
            System.err.println("- log: " + l.id);
            Date date = inputFormat.parse(l.id);
            final String outDate = outputFormat.format(date);

            int logAdvance2in1 = 0;
            if (finished.containsKey(outDate)) {
                logAdvance2in1 = finished.get(outDate) + 1;
            }

//            ArrayList<byte[]> segs = getAudioSegments(l.audioPath);
            ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> segs = getAudioSegments(l.audioPath, null, null);
//            segs.remove(0);                // MONSERRATE LOGS ONLY!?!

            int j2 = 0;
            for (int j = 0; j < segs.size(); j++) {
                final byte[] sound = segs.get(j).getValue();

                final int jj = j + logAdvance2in1;
                j2 = jj;

                try {
                    String recog = asr.recognizeBytes(sound).getRecog();

                    String id = asr.getID() + "_" + "fullTM";
                    String src = "speech_" + outDate + "_#" + jj + "_" + dec.format(segs.get(j).getKey().getValue()).replace(',', '.') + "s";
                    qa.ask(id, src, recog);

//                    System.out.println("sent: " + recog + " | id=" + id + " | source=" + src);
                } catch (Exception ex) {
                    System.err.println(" error: " + ex);
                }
            }

            finished.put(outDate, j2);
            System.gc();
        }
    }

    public void dumpASRandSendToQA(ASR asr, String stmFolder, boolean isolatedDumpFolder, QA qa) throws Exception {
//        DateFormat outputFormat = new SimpleDateFormat("yyMMdd");
        DateFormat outputFormat = new SimpleDateFormat("yy'Y'MM'M'dd'D'HH'h'mm'm'ss's'");
        DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'_'HH'h'mm'm'ss's'");

        String id = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
        String hyps = "";

        TreeSet<String> stms = new TreeSet<>();
        for (String stm1 : new File(stmFolder).list()) {
            stms.add(stm1.replaceAll(".stm", "").trim());
        }

        HashMap<String, Integer> finished = new HashMap<>();
        TreeSet<LogFile> logs = getDebugFiles();
        for (String stm : stms) {
            ArrayList<StmLine> interacts = getCleanSTM(stmFolder + "/" + stm + ".stm");

            System.err.println("- log: " + stm);
            System.err.println("\t--stm line num: " + interacts.size());

            for (LogFile l : logs) {
                if (l.id.equalsIgnoreCase(stm)) {
                    Date date = inputFormat.parse(l.id);
                    final String outDate = outputFormat.format(date);
                    int logAdvance2in1 = 0;
                    if (finished.containsKey(outDate)) {
                        logAdvance2in1 = finished.get(outDate) + 1;
                    }

                    ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> segs = ASReval.getAudioSegments(l.audioPath, null, null);     //                            File d = new File("dump\\segs\\" + stm + "\\");       //                            d.mkdir();
                    System.err.println("\t--segs num: " + segs.size());

                    int j2 = 0;
                    for (int i = 0; i < interacts.size(); i++) {
                        StmLine inter = interacts.get(i);

                        final int jj = i + logAdvance2in1;
                        j2 = jj;

                        //<editor-fold defaultstate="collapsed" desc="discard speakers">
                        //                                if (inter.nonStdSpkr != null) {
                        //                                    refs += "\n";
                        //                                    hyps += "\n";
                        //                                    continue;
                        //                                }
                        //</editor-fold>

                        String recog = asr.recognizeBytes(segs.get(i).getValue()).getRecog();
                        if (!recog.equals("_REPEAT_")) {
                            if (!isolatedDumpFolder) {
                                System.out.print(recog + "\n");
                            } else {
                                hyps += recog + "\n";
                            }
                        } else {
                            if (!isolatedDumpFolder) {
                                System.out.print("\n");
                            } else {
                                hyps += "\n";
                            }
                        }

                        try {
                            qa.ask(qa.getID(), "speech_" + outDate + "_#" + jj + "_" + inter.sfim + "s", recog);
                        } catch (Exception ex) {
                            System.err.println("--ERROR: QA send failed. continuing...");
                        }
                    }

                    if (isolatedDumpFolder) {
                        Common.stringToFile(hyps, dumpPath + "hyps_" + id + "/" + stm + ".txt", "UTF-8");
                        hyps = "";
                    }

                    finished.put(outDate, j2);
                    System.gc();

                    break;
                }
            }
        }
    }

    public void dumpASR(ASR asr, String stmFolder, boolean isolatedDumpFolder) throws Exception {
        DateFormat outputFormat = new SimpleDateFormat("yyMMdd");
        DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'_'HH'h'mm'm'ss's'");

        String id = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
        String hyps = "";

        TreeSet<String> stms = new TreeSet<>();
        for (String stm1 : new File(stmFolder).list()) {
            stms.add(stm1.replaceAll(".stm", "").trim());
        }

        HashMap<String, Integer> finished = new HashMap<>();
        TreeSet<LogFile> logs = getDebugFiles();
        for (String stm : stms) {
            ArrayList<StmLine> interacts = getCleanSTM(stmFolder + "/" + stm + ".stm");

            System.err.println("- log: " + stm);
            System.err.println("\t--stm line num: " + interacts.size());

            for (LogFile l : logs) {
                if (l.id.equalsIgnoreCase(stm)) {
                    Date date = inputFormat.parse(l.id);
                    final String outDate = outputFormat.format(date);
                    int logAdvance2in1 = 0;
                    if (finished.containsKey(outDate)) {
                        logAdvance2in1 = finished.get(outDate) + 1;
                    }

                    ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> segs = ASReval.getAudioSegments(l.audioPath, null, null);     //                            File d = new File("dump\\segs\\" + stm + "\\");       //                            d.mkdir();
                    System.err.println("\t--segs num: " + segs.size());

                    int j2 = 0;
                    for (int i = 0; i < interacts.size(); i++) {
                        StmLine inter = interacts.get(i);

                        final int jj = i + logAdvance2in1;
                        j2 = jj;

                        //<editor-fold defaultstate="collapsed" desc="discard speakers">
                        //                                if (inter.nonStdSpkr != null) {
                        //                                    refs += "\n";
                        //                                    hyps += "\n";
                        //                                    continue;
                        //                                }
                        //</editor-fold>

                        String recog = asr.recognizeBytes(segs.get(i).getValue()).getRecog();
                        if (!recog.equals("_REPEAT_")) {
                            if (!isolatedDumpFolder) {
                                System.out.print(recog + "\n");
                            } else {
                                hyps += recog + "\n";
                            }
                        } else {
                            if (!isolatedDumpFolder) {
                                System.out.print("\n");
                            } else {
                                hyps += "\n";
                            }
                        }
                    }

                    if (isolatedDumpFolder) {
                        Common.stringToFile(hyps, dumpPath + "hyps_" + id + "/" + stm + ".txt", "UTF-8");
                        hyps = "";
                    }

                    finished.put(outDate, j2);
                    System.gc();

                    break;
                }
            }
        }
    }

    public void dumpASR(ASR asr) throws Exception {
        DateFormat outputFormat = new SimpleDateFormat("yyMMdd");
        DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'_'HH'h'mm'm'ss's'");

        HashMap<String, Integer> finished = new HashMap<>();
        TreeSet<LogFile> logs = getDebugFiles();
        for (LogFile l : logs) {
//            if (l.id.startsWith("2012-07-26") ){//|| l.id.startsWith("2012-03-19")) {
//                continue;
//            }

//            if (!l.id.startsWith("2012-03-19")) {
//                continue;
//            }

//            System.err.println("-\r\n-\t log: " + l.id + "\r\n-");
            System.err.println("- log: " + l.id);
            Date date = inputFormat.parse(l.id);
            final String outDate = outputFormat.format(date);

            int logAdvance2in1 = 0;
            if (finished.containsKey(outDate)) {
                logAdvance2in1 = finished.get(outDate) + 1;
            }

//            ArrayList<byte[]> segs = getAudioSegments(l.audioPath, null);
            ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> segs1 = ASReval.getAudioSegments(l.audioPath, null, null);
//            segs.remove(0);                                             // MONSERRATE LOGS ONLY!?!

            int j2 = 0;
            for (int j = 0; j < segs1.size(); j++) {
                final byte[] sound = segs1.get(j).getValue();

                final int jj = j + logAdvance2in1;
                j2 = jj;

                try {
                    String recog = asr.recognizeBytes(sound).getRecog();
                    if (!recog.equals("_REPEAT_")) {
                        System.out.println(recog);
                    } else {
                        System.out.println("");
                    }
                } catch (Exception ex) {
                    System.err.println(" error: " + ex);
                }
            }

            finished.put(outDate, j2);
            System.gc();
        }
    }

    public void dumpASR(List<ASR> asrs, String dumpFileInfix) throws Exception {
        DateFormat outputFormat = new SimpleDateFormat("yyMMdd");
        DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'_'HH'h'mm'm'ss's'");

        String now = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
        Map<String, AbstractMap.SimpleEntry<ASR, PrintWriter>> evalTasks2 = new HashMap<>();
        for (ASR a : asrs) {
            PrintWriter printStream = null;

            if (dumpFileInfix != null) {
                File file = new File(dumpPath, a.getID() + "_" + dumpFileInfix + "_" + now + ".txt");
                if (!file.exists()) {
                    file.createNewFile();
                }

//            FileWriter fileWritter = new FileWriter(file.getName(), true);      //true = append file
//            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);

                printStream = new PrintWriter(new FileOutputStream(file), true);
            } else {
                printStream = new PrintWriter(System.out, true);
            }

            evalTasks2.put(a.getID(), new AbstractMap.SimpleEntry<>(a, printStream));
        }

        final Map<String, AbstractMap.SimpleEntry<ASR, PrintWriter>> evalTasks = evalTasks2;

        HashMap<String, Integer> finished = new HashMap<>();
        TreeSet<LogFile> logs = getDebugFiles();
        for (LogFile l : logs) {
//            if (l.id.startsWith("2012-03-18") || l.id.startsWith("2012-03-19")) {
//                continue;
//            }

//            System.err.println("-\r\n-\t log: " + l.id + "\r\n-");
            System.err.print("\n- log: " + l.id + "\n");
            Date date = inputFormat.parse(l.id);
            final String outDate = outputFormat.format(date);

            int logAdvance2in1 = 0;
            if (finished.containsKey(outDate)) {
                logAdvance2in1 = finished.get(outDate) + 1;
            }

            ArrayList<byte[]> segs = getAudioSegments(l.audioPath, null);
            int j2 = 0;
            for (int j = 0; j < segs.size(); j++) {
                final byte[] sound = segs.get(j);

                final int jj = j + logAdvance2in1;
                j2 = jj;

                for (final String et : evalTasks.keySet()) {
                    //<editor-fold defaultstate="collapsed" desc="threads part 1">
                    //                    while (Thread.getAllStackTraces().keySet().size() > (5 + evalTasks.keySet().size())) {
                    //                        try {
                    //                            Thread.sleep(100);     //1s = 1000
                    //                        } catch (InterruptedException ex) {
                    //                        }
                    //                    }
                    //
//                                        new Thread(
//                                                new Runnable() {
//                                                    @Override
//                                                    public void run() {
//                                                        try {
                    //</editor-fold>

                    String recog = evalTasks.get(et).getKey().recognizeBytes(sound).getRecog();
                    if (!recog.equals("_REPEAT_")) {
                        evalTasks.get(et).getValue().println(recog);
                    } else {
                        evalTasks.get(et).getValue().println();
                    }

                    //<editor-fold defaultstate="collapsed" desc="threads part 2">
                    //                                    } catch (Exception ex) {
                    //                                        System.err.println(" error: " + ex);
                    //                                    }
                    //                                }
                    //                            }).start();
                    //</editor-fold>
                }
            }

            finished.put(outDate, j2);
            System.gc();
        }

        for (String et : evalTasks.keySet()) {
            evalTasks.get(et).getValue().close();
        }
    }
}
//<editor-fold defaultstate="collapsed" desc="comment">
//
//
//
//public ArrayList<byte[]> getAudioSegments(String audioFilePath, int maxEstimNumInteracts) {
//    ArrayList<byte[]> res = new ArrayList<byte[]>();
//
//    int totalFramesRead = 0;
//    File fileIn = new File(audioFilePath);
//
//    try {
//        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(fileIn);
//        int bytesPerFrame = audioInputStream.getFormat().getFrameSize();
//
//        int frameLength = (int) audioInputStream.getFrameLength();
//        int frameSize = (int) audioInputStream.getFormat().getFrameSize();
//        byte[] audioBytes = new byte[frameLength * frameSize];      //read entire file at once
//
//        boolean segDone = false;
//        while (!segDone) {
//            ArrayList<byte[]> aSegment = new ArrayList<byte[]>();
//
//            try {
//                int numBytesRead = 0;
//                while ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {
//
//                    //get sample values
//                    int[] samples = new int[frameLength];
//                    int sindx = 0;
//                    for (int i = 0; i < audioBytes.length;) {
//                        int low = (int) audioBytes[i];
//                        i++;
//                        int high = (int) audioBytes[i];
//                        i++;
//                        samples[sindx] = (high << 8) + (low & 0x00ff);
//                        sindx++;
//                    }
//
//                    //DEBUGS
////                        System.out.print("audio:\n");
////                        for (int i = 0; i < 1000; i++) {
////                            System.out.print(", " + audioBytes[i]);
////                        }
////
////                        System.out.print("\n\nsamples:\n");
//
//                    //System.out.print(Arrays.toString(samples.descendingSet().toArray()));
//                    String tmp = "";
//                    for (int i = 0; i < samples.length; i++) {
//                        tmp += samples[i] + "\r\n";
//                    }
//                    utils.Common.stringToFile(tmp, "e:\\tmp.txt");
//
//
//                    System.err.println();
//
//                }
//            } catch (Exception ex) {
//                // Handle the error...
//            }
//        }
//    } catch (Exception e) {
//        // Handle the error...
//    }
//
//    return res;
//}
//
//
//
//
//    public void start() {
//        String now = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
//        File refRoot = new File(dumpPath, "ref" + now);
//        File hypRoot = new File(dumpPath, "hyp" + now);
////        refRoot.mkdir();
////        hypRoot.mkdir();
//
//        TreeSet<Log> rawToResult = null;
//        try {
//            rawToResult = getDebugFiles(); //debugFilesFolder, audioFilename, asrTransEndsWith
//        } catch (Exception ex) {
//            System.err.println(ex.getMessage());
//        }
//
//        for (Log debugFile : rawToResult) {
//            int maxEstimNumInteracts = 0;
//
//            if (debugFile.resultsPath != null) {        // generic mode 
//                // clean .results and copy to /ref/
//                String cleanResults = cleanSilences(debugFile.resultsPath);
//                //utils.Common.stringToFile(cleanResults, new File(refRoot, debugFile.id + ".txt").getAbsolutePath());
//                maxEstimNumInteracts = cleanResults.split("\r\n").length;
//            }
//
////            ArrayList<Float> res = getAudioSegments(debugFile.audioPath);
////
////            for (byte[] audioSegment : getAudioSegments(debugFile.audioPath, maxEstimNumInteracts)) {
////                try {
////                    String recog = asr.recognizeBytes(audioSegment);
////                } catch (Exception ex) {
////                    System.err.println("asr-eval: asr error on " + debugFile.audioPath + ": " + ex.getMessage());
////                }
////            }
//            
//            //            HashMap<String,String> rawToResult = ASReval.getDebugFiles(debugFilesFolder, audioFilename);
//            //            for(String audioPath : rawToResult.keySet()){
//            //                String resultPath = rawToResult.get(audioPath);
//            //
//            //                File refPath = new File(af.config.getProperty("dump"), "");
//            //                utils.Common.stringToFile(ASReval.cleanSilences(resultPath), refPath);
//            //
//            //                for(byte[] audioSegment : ASReval.getAudioSegments(audioPath)){
//            //                    try {
//            //                        String recog = af.asr.get(Agent.lang.PT).recognizeBytes(audioSegment);
//            //                    } catch (Exception ex) {
//            //                        System.err.println("asr-eval: asr error on " + audioPath + ": " + ex.getMessage());
//            //                    }
//            //                }
//            //
//            //                File hypPath = new File(af.config.getProperty("dump"), "");
//            //            }
//        }
//    }
//</editor-fold>
//<editor-fold defaultstate="collapsed" desc=".result based getSTM">
//    public static String getSTM(Log l) {
//        String stm = "";
//
//        LinkedList<String> cleanResults1 = new LinkedList<>(Arrays.asList(ASReval.cleanSilences(l.resultsPath).replaceAll("<s>", "").replaceAll("</s>", "").trim().split("\\s+\r\n\\s+")));
//
//        String fullresults = Common.fileToString(l.resultsPath);
//        LinkedList<String> cleanResults2 = new LinkedList<>(Arrays.asList(fullresults.replaceAll("<s>", "").replaceAll("</s>", "").split("\r\n")));
//        int silenceCnt = 0;
//        for (String rs : cleanResults2) {
//            if (rs.trim().isEmpty()) {
//                silenceCnt++;
//            }
//        }
//
//        ArrayList<Float> segstarts = ASReval.getAudioSegments(l.audioPath, null);
//
//        LinkedList<String> cleanResults = null;
//        if ((segstarts.size() - 1) >= cleanResults2.size()) {
//            cleanResults = cleanResults2;
//        } else {
//            if (segstarts.size() == cleanResults1.size() || silenceCnt < (Math.round(cleanResults2.size() * 25 / 100))) {
//                cleanResults = cleanResults1;
//            } else {
//                //try to fit results to segments
//                while (!cleanResults2.isEmpty() && cleanResults2.getLast().trim().isEmpty()) {
//                    cleanResults2.pollLast();
//                }
//
//                int maxLoops = 100000;
//                int loopCnt = 0;
//                while (cleanResults2.size() > (segstarts.size() - 1) && loopCnt < maxLoops) {
//                    int idx = 0 + (int) (Math.random() * (((cleanResults2.size() - 1) - 0) + 1));
//
//                    if (cleanResults2.get(idx).trim().isEmpty()) {
//                        cleanResults2.remove(idx);
//                    }
//                    loopCnt++;
//                }
//
//                if (cleanResults2.size() <= (segstarts.size() - 1)) {
//                    cleanResults = cleanResults2;
//                } else {
//                    cleanResults = cleanResults1;
//                }
//            }
//        }
//
//        if (cleanResults.size() > segstarts.size()) {
//            System.err.println("bad failure on " + l.id + ": cleanResults(" + cleanResults.size() + ") > segstarts(" + segstarts.size() + ")...discarding and continuing");
//            return "";
//        }
//
//        float ti = -1;
//        float tf = -1;
//        for (int i = 0; i < segstarts.size(); i++) {
//            if (ti < 0) {
//                ti = segstarts.get(i);
//            } else {
//                tf = segstarts.get(i);
//            }
//
//            if (ti >= 0 && tf > 0) {
//                stm += "/" + l.id + "/out-received.wav 1 asrTranscript " + dec.format(ti).replace(',', '.') + " " + dec.format(tf).replace(',', '.')
//                        + " <o,F0,male> " + (!cleanResults.isEmpty() ? cleanResults.pollFirst() : "") + " \n";
//
//                ti = tf;
//                tf = -1;
//            }
//        }
//
//        if (!cleanResults.isEmpty()) {
//            System.err.println("bad failure on " + l.id + ": cleanResults(" + cleanResults.size() + ") not empty...continuing (check resulting file!)");
//        }
//
//        return stm;
//    }
    //</editor-fold>