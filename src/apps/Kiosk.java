/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package apps;

import agent.Agent;
import agent.LogFile;
import agent.StmLine;
import connection.http.FileServer;
import connection.http.RequestServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import qa.QA;
import qa.SOAPclient;
import speech.*;
import utils.Common;

/**
 *
 * @author pfialho
 */
public class Kiosk implements Agent {

//    public static final String now = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
    public Properties config = null;
    public HashMap<Agent.lang, TTS> tts = null;
    public HashMap<Agent.lang, ASR> asr = null;
    public HashMap<Agent.lang, ASR> asrkws = null;
    public HashMap<Agent.lang, QA> qa = null;
    public HashMap<Agent.lang, QA> qaTalkpedia = null;
    public HashMap<Agent.lang, QA> qaLCexp = null;
    //for opinion management
    public File opinionDump = null;
    public File multipartDump = null;
    public String mp3enc = null;
    public String mp3encopt = null;
    public Set<lang> remoteASRs;
    public Set<lang> remoteTTSs;

    public Kiosk(String config1) throws Exception {
        try {
            this.loadConfig(config1);
        } catch (Exception ex) {
            throw new Exception("config loading error: " + ex.getMessage());
        }

        remoteASRs = new HashSet<>();
        remoteTTSs = new HashSet<>();
    }

    private void loadConfig(String configpath) throws Exception {
        config = new Properties();
        try {
            config.load(new FileInputStream(configpath));
        } catch (IOException ex) {
            throw new Exception("config.properties not loaded: " + ex.getMessage());
        }

        if (config.getProperty("dump", "").isEmpty()
                //                || config.getProperty("fileport", "").isEmpty()
                || config.getProperty("requestport", "").isEmpty()) {
            throw new Exception("config is missing dump folder path");
        }

        new File(config.getProperty("dump")).mkdir();
    }

    public static String getASRgrammar(String corporaDir, Agent.lang lang) throws Exception {
        HashSet<String> res = new HashSet<>();

        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true); // never forget this!
        DocumentBuilder builder = domFactory.newDocumentBuilder();

        final Agent.lang l = lang;
        for (File p : new File(corporaDir).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File f, String s) {
                return s.endsWith(".xml") && s.startsWith(l.toString().toLowerCase());
            }
        })) {
            System.err.println("parsing: " + p.getPath());
            Document doc = builder.parse(new File(p.getPath()));

            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();
            XPathExpression expr = xpath.compile("//q/text()");
            Object result = expr.evaluate(doc, XPathConstants.NODESET);
            NodeList nodes = (NodeList) result;
            for (int i = 0; i < nodes.getLength(); i++) {
                String question = nodes.item(i).getNodeValue();
                if (!question.startsWith("_")) {
                    String q2 = "";

                    switch (lang) {
                        case EN:
                            q2 = question.replaceAll("[?!,.;:]", " ").replaceAll("\\s+", " ").toLowerCase().trim();
                            break;
                        case ES:
                            q2 = question.replaceAll("[?!,.;:]", " ").replaceAll("\u00BF", " ").replaceAll("\\s+", " ").toLowerCase().trim();
                            break;
                        case PT:
                            q2 = question.replaceAll("[?!,.;:]", " ").replaceAll("\\s+", " ").toLowerCase().trim();
                            break;
                        default:
                            break;
                    }

                    if (!q2.isEmpty()) {
                        res.add(q2);
                    }
                }
            }
        }

        //<editor-fold defaultstate="collapsed" desc="grxml">
        String flang = "pt-PT";
        switch (lang) {
            case EN:
                flang = "en-US";
                break;
            case ES:
                flang = "es-ES";
                break;
            case PT:
                flang = "pt-PT";
                break;
            default:
                break;
        }

        //write grammar
        final File sgrsFile = File.createTempFile("grammar_full_" + lang.toString() + "_", ".grxml");
        sgrsFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(sgrsFile)) {
            writer.write("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n");
            writer.write("<grammar root=\"main\" version=\"1.0\" xmlns=\"http://www.w3.org/2001/06/grammar\" xml:lang=\"" + flang + "\">\n");
            writer.write("\t<rule id=\"main\">\n");
            writer.write("\t\t<item repeat=\"0-\">\n");
            writer.write("\t\t\t<one-of>\n");
            //grammar 1
            for (String asrkey : res) {
                writer.write("<item><ruleref special=\"GARBAGE\"/>");
                writer.write(asrkey);
                writer.write("<ruleref special=\"GARBAGE\"/></item>\n");
            }

            //grammar 2
//            HashSet<String> keyws = new HashSet<>();
//            for (String asrkey : res) {
//                for (String a : asrkey.split(" ")) {
//                    keyws.add(a);
//                }
//            }
//
//            for (String asrkey : keyws) {
////                if (asrkey.length() < 10) {
////                    writer.write("<item weight=\"." + asrkey.length() + "\">");
////                } else {
//                    writer.write("<item>");
////                }
//                writer.write(asrkey);
//                writer.write("</item>\n");
//            }

            writer.write("<item><ruleref special=\"GARBAGE\"/></item>\n");
            writer.write("\t\t\t</one-of>\n");
            writer.write("\t\t</item>\n");
            writer.write("\t</rule>\n");
            writer.write("</grammar>\n");
        }
        //</editor-fold>

        return sgrsFile.getPath();
    }

    private TreeSet<String> getCacheSources(String corporaDir, Agent.lang l) {
        TreeSet<String> res = new TreeSet<String>();
        try {
            DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(true); // never forget this!
            DocumentBuilder builder = domFactory.newDocumentBuilder();

            final Agent.lang l2 = l;
            for (File p : new File(corporaDir).listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File f, String s) {
                    return s.startsWith(l2.toString().toLowerCase());       //s.endsWith(".xml") && 
                }
            })) {
                System.err.println("parsing: " + p.getPath());

                //txt cache - 1 sentence per line (UTF-8/Linux)
                if (!p.getName().endsWith(".xml")) {
                    String sents = Common.fileToString(p.getAbsolutePath());
                    res.addAll(Arrays.asList(sents.split("\r\n")));

                    continue;
                }

                //when file is XML
                Document doc = builder.parse(p.getPath());

                XPathFactory factory = XPathFactory.newInstance();
                XPath xpath = factory.newXPath();

                //get xml answer
                // XPathExpression expr = xpath.compile("//q/text()");
                // Object result = expr.evaluate(doc, XPathConstants.NODESET);
                // NodeList nodes = (NodeList) result;
                // for (int i = 0; i < nodes.getLength(); i++) {
                // 	String question = nodes.item(i).getNodeValue();
                // 	if(!question.startsWith("_"))
                // 	    System.out.println(question.replaceAll("\\p{Punct}+", "").toLowerCase().trim());
                // }

                XPathExpression expr = null;
//                if (l.equals(Agent.lang.PT)) {
                expr = xpath.compile("//a");        //[not(fillWith)]
//                }
//                if (l.equals(Agent.lang.EN)) {
//                    expr = xpath.compile("//a/@en");
//                }
//                if (l.equals(Agent.lang.ES)) {
//                    expr = xpath.compile("//a/@es");
//                }

                Object result = expr.evaluate(doc, XPathConstants.NODESET);
                NodeList nodes = (NodeList) result;
                for (int i = 0; i < nodes.getLength(); i++) {
                    String answer = nodes.item(i).getTextContent();
                    res.add(answer.trim());
                }
            }
        } catch (Exception ex) { //ParserConfigurationException | SAXException | IOException | XPathExpressionException | DOMException
            System.err.println(ex.getMessage());
        }

        return res;
    }

    @Override
    public ASR setASR(Agent.lang ln, boolean isStatictask) throws Exception {
        ASR asrlocal = null;

        //<editor-fold defaultstate="collapsed" desc="validate asr config">
        //check remote config - 1 for all langs
        //        if (config.containsKey("audimusRemoteURL") && !config.getProperty("audimusRemoteURL", "").isEmpty()) {
        //            AudimusRemote asrr = new AudimusRemote(config.getProperty("audimusRemoteURL"));
        //            asrr.init();
        //
        //            asr.put(Agent.lang.PT, asrr);
        //            asr.put(Agent.lang.EN, asrr);
        //            asr.put(Agent.lang.ES, asrr);
        //
        //            return;
        //        }

        //else, local config - 1 for each lang
        if (config.getProperty("asrRepeat", "").isEmpty()
//                || (!isStatictask && config.getProperty("corpora", "").isEmpty())
                ) {
            throw new Exception("missing config: asrRepeat");
        }
        //</editor-fold>

        switch (ln) {
            case EN:
                //<editor-fold defaultstate="collapsed" desc="en">
                try {
                    if (config.containsKey("MinConfidenceEN")) {
                        Float.parseFloat(config.getProperty("MinConfidenceEN", "0"));
                    }
                    if (config.containsKey("SureConfidenceEN")) {
                        Float.parseFloat(config.getProperty("SureConfidenceEN", "0"));
                    }
                } catch (Exception ex) {
                    throw new Exception("EN wrong value type in config: " + ex.getMessage());
                }

                if (isStatictask) {
                    //<editor-fold defaultstate="collapsed" desc="static">
                    if (config.getProperty("audimusConfigEN", "").isEmpty()) {
                        throw new Exception("EN missing config");
                    }

                    if (!new File(config.getProperty("audimusConfigEN")).exists()) {
                        throw new Exception("EN unable to find path in config");
                    }

                    Audimus asren = new Audimus(
                            config.getProperty("audimusConfigEN"),
                            config.getProperty("asrRepeat"),
                            Float.parseFloat(config.getProperty("MinConfidenceEN", "0")),
                            Float.parseFloat(config.getProperty("SureConfidenceEN", "0")));

                    asren.init();
                    asrlocal = asren;
                    //</editor-fold>
                } else {
                    //<editor-fold defaultstate="collapsed" desc="grammar">
                    if (config.getProperty("audimusConfigGramEN", "").isEmpty()
                            || config.getProperty("modelsDirEN", "").isEmpty()) {
                        throw new Exception("EN missing config");
                    }

                    if (!new File(config.getProperty("audimusConfigGramEN")).exists()) {
                        throw new Exception("EN unable to find path in config");
                    }

                    Audimus asrpt = new Audimus(
                            config.getProperty("audimusConfigGramEN"),
                            config.getProperty("asrRepeat"),
                            Float.parseFloat(config.getProperty("MinConfidenceEN", "0")),
                            Float.parseFloat(config.getProperty("SureConfidenceEN", "0")),
                            config.getProperty("modelsDirEN"));

                    asrpt.init();

                    asrlocal = asrpt;
//                    asrlocal.loadTask(
//                            getASRgrammar(config.getProperty("corpora"), ln),
//                            config.getProperty("modelsDirEN"),
//                            ln.toString());
                    //</editor-fold>
                }
                //</editor-fold>
                break;
            case ES:
                //<editor-fold defaultstate="collapsed" desc="es">
                try {
                    if (config.containsKey("MinConfidenceES")) {
                        Float.parseFloat(config.getProperty("MinConfidenceES", "0"));
                    }
                    if (config.containsKey("SureConfidenceES")) {
                        Float.parseFloat(config.getProperty("SureConfidenceES", "0"));
                    }
                } catch (Exception ex) {
                    throw new Exception("ES wrong value type in config: " + ex.getMessage());
                }

                if (isStatictask) {
                    //<editor-fold defaultstate="collapsed" desc="static">
                    if (config.getProperty("audimusConfigES", "").isEmpty()) {
                        throw new Exception("ES missing config");
                    }

                    if (!new File(config.getProperty("audimusConfigES")).exists()) {
                        throw new Exception("ES unable to find path in config");
                    }

                    Audimus asres = new Audimus(
                            config.getProperty("audimusConfigES"),
                            config.getProperty("asrRepeat"),
                            Float.parseFloat(config.getProperty("MinConfidenceES", "0")),
                            Float.parseFloat(config.getProperty("SureConfidenceES", "0")));

                    asres.init();
                    asrlocal = asres;
                    //</editor-fold>
                } else {
                    //<editor-fold defaultstate="collapsed" desc="grammar">
                    if (config.getProperty("audimusConfigGramES", "").isEmpty()
                            || config.getProperty("modelsDirES", "").isEmpty()) {
                        throw new Exception("ES missing config");
                    }

                    if (!new File(config.getProperty("audimusConfigGramES")).exists()) {
                        throw new Exception("ES unable to find path in config");
                    }

                    Audimus asrpt = new Audimus(
                            config.getProperty("audimusConfigGramES"),
                            config.getProperty("asrRepeat"),
                            Float.parseFloat(config.getProperty("MinConfidenceES", "0")),
                            Float.parseFloat(config.getProperty("SureConfidenceES", "0")),
                            config.getProperty("modelsDirES"));

                    asrpt.init();

                    asrlocal = asrpt;
//                    asrlocal.loadTask(
//                            getASRgrammar(config.getProperty("corpora"), ln),
//                            config.getProperty("modelsDirES"),
//                            ln.toString());
                    //</editor-fold>
                }
                //</editor-fold>
                break;
            case PT:
                //<editor-fold defaultstate="collapsed" desc="pt">
                try {
                    if (config.containsKey("MinConfidence")) {
                        Float.parseFloat(config.getProperty("MinConfidence", "0"));
                    }
                    if (config.containsKey("SureConfidence")) {
                        Float.parseFloat(config.getProperty("SureConfidence", "0"));
                    }
                } catch (Exception ex) {
                    throw new Exception("PT wrong value type in config: " + ex.getMessage());
                }

                if (isStatictask) {
                    //<editor-fold defaultstate="collapsed" desc="static">
                    if (config.getProperty("audimusConfig", "").isEmpty()) {
                        throw new Exception("PT missing config");
                    }

                    if (!new File(config.getProperty("audimusConfig")).exists()) {
                        throw new Exception("PT unable to find path in config");
                    }

                    Audimus asrpt = new Audimus(
                            config.getProperty("audimusConfig"),
                            config.getProperty("asrRepeat"),
                            Float.parseFloat(config.getProperty("MinConfidence", "0")),
                            Float.parseFloat(config.getProperty("SureConfidence", "0")));

                    asrpt.init();
                    asrlocal = asrpt;
                    //</editor-fold>
                } else {
                    //<editor-fold defaultstate="collapsed" desc="grammar">
                    if (config.getProperty("audimusConfigGram", "").isEmpty()
                            || config.getProperty("modelsDir", "").isEmpty()) {
                        throw new Exception("PT missing config");
                    }

                    if (!new File(config.getProperty("audimusConfigGram")).exists()) {
                        throw new Exception("PT unable to find path in config");
                    }

                    Audimus asrpt = new Audimus(
                            config.getProperty("audimusConfigGram"),
                            config.getProperty("asrRepeat"),
                            Float.parseFloat(config.getProperty("MinConfidence", "0")),
                            Float.parseFloat(config.getProperty("SureConfidence", "0")),
                            config.getProperty("modelsDir"));

                    asrpt.init();

                    asrlocal = asrpt;
//                    asrlocal.loadTask(
//                            getASRgrammar(config.getProperty("corpora"), ln),
//                            config.getProperty("modelsDir"),
//                            ln.toString());

                    //<editor-fold defaultstate="collapsed" desc="other">
                    //        asr.setTask(
                    //                asr.addTask(
                    //                getSRGSpath(config.getProperty("corpora_pt")), config.getProperty("modelsDir"), 0));
                    //</editor-fold>
                    //</editor-fold>
                }
                //</editor-fold>
                break;
            case BR:
                if (isStatictask) {
                    Audimus asrpt = new Audimus(config.getProperty("audimusConfigBR"));
                    asrpt.init();
                    asrlocal = asrpt;
                }
                break;
            default:
                break;
        }

        return asrlocal;
    }

    @Override
    public TTS setTTS(Agent.lang ln, boolean preload) throws Exception, IOException {
        TTS res = null;

        //<editor-fold defaultstate="collapsed" desc="validate configs">
        if (config.getProperty("oggenc", "").isEmpty()
                || config.getProperty("mp3enc", "").isEmpty()) {
            throw new Exception("missing config");
        }

        if (!new File(config.getProperty("mp3enc")).exists()
                || !new File(config.getProperty("oggenc")).exists()) {
            throw new Exception("unable to find path in config");
        }
        //</editor-fold>

        switch (ln) {
            case EN:
                //<editor-fold defaultstate="collapsed" desc="en">
                if (!config.containsKey("festexec") || config.getProperty("festexec", "").isEmpty()) {
                    throw new Exception("en: missing config: festexec");
                }

                FestivalProc tts_en = new FestivalProc(
                        config.getProperty("festexec"),
                        config.getProperty("dump"),
                        config.getProperty("oggenc"),
                        config.getProperty("oggencopt", ""),
                        config.getProperty("mp3enc"),
                        config.getProperty("mp3encopt", ""),
                        true);
                tts_en.init();
                tts_en.setLang(ln);
                res = tts_en;
                //</editor-fold>
                break;
            case ES:
                //<editor-fold defaultstate="collapsed" desc="es">
                if (!config.containsKey("festexec") || config.getProperty("festexec", "").isEmpty()) {
                    throw new Exception("es: missing config: festexec");
                }

                FestivalProc tts_es = new FestivalProc(
                        config.getProperty("festexec"),
                        config.getProperty("dump"),
                        config.getProperty("oggenc"),
                        config.getProperty("oggencopt", ""),
                        config.getProperty("mp3enc"),
                        config.getProperty("mp3encopt", ""));
                tts_es.init();
                tts_es.setLang(ln);
                res = tts_es;
                //</editor-fold>
                break;
            case PT:
                //<editor-fold defaultstate="collapsed" desc="pt">
                if (config.getProperty("dixiConfig", "").isEmpty()
                        || config.getProperty("dixiVoices", "").isEmpty()) {
                    throw new Exception("pt: missing config");
                }

                if (!new File(config.getProperty("dixiConfig")).exists()
                        || !new File(config.getProperty("dixiVoices")).exists()) {
                    throw new Exception("pt: unable to find path in config");
                }

                TTS tts_pt = null;
                if (!config.containsKey("dixiRemoteURL") || config.getProperty("dixiRemoteURL", "").isEmpty()) {
                    tts_pt = new Dixi(
                            config.getProperty("dixiConfig"),
                            config.getProperty("dixiVoices"),
                            config.getProperty("dump"),
                            config.getProperty("oggenc"),
                            config.getProperty("oggencopt", ""),
                            config.getProperty("mp3enc"),
                            config.getProperty("mp3encopt", ""),
                            config.getProperty("dixiConfigSlow", ""));
                }
//                else {
//                    tts_pt = new DixiRemote(config.getProperty("dixiRemoteURL", ""));
//                }

                tts_pt.init();
                tts_pt.setLang(ln);
                res = tts_pt;
                //</editor-fold>
                break;
            default:
                break;
        }

        if (preload) {
            //<editor-fold defaultstate="collapsed" desc="preload">
            if (!config.containsKey("corpora") || config.getProperty("corpora", "").isEmpty()) {
                throw new Exception("preload: missing XML QA 'corpora' key or value");
            }
            if (!new File(config.getProperty("corpora")).exists()) {
                throw new Exception("preload: unable to find XML QA corpora path in config");
            }

            String fnameprefx = config.getProperty("fnameprefx", "");
            if (fnameprefx.isEmpty()) {      //set file (audio) server location
                fnameprefx = "http://localhost:8080/";
            }

            //start preload
            for (String a : this.getCacheSources(config.getProperty("corpora"), ln)) {
                if (res.getAudio(res.getDefaultVoice(), a, fnameprefx, TTS.AUDIOFORMAT.OGG, 0) == null) {
                    System.err.println("-failed loading cache for: " + a + ". retrying...");

                    if (res.getAudio(res.getDefaultVoice(), a, fnameprefx, TTS.AUDIOFORMAT.OGG, 0) == null) {
                        System.err.println("\r\n-----2o failed loading cache for: " + a + ".\r\n");
                    }
                }
            }
            //</editor-fold>
        }

        return res;
    }

    public static void main(String[] args) {
        String config1;
        boolean autoclean = false;
        boolean enabletts = true;
        boolean enableasr;
        boolean enableasreval = false;
        boolean enableqa = true;
        Set<Agent.lang> preloads = new HashSet<>();
        Set<Agent.lang> langs;

        // defaults
        langs = EnumSet.allOf(Agent.lang.class);
        langs.remove(Agent.lang.BR);            //exception

        Set<Agent.lang> staticTasks = EnumSet.allOf(Agent.lang.class);
        staticTasks.remove(Agent.lang.BR);      //exception

        if (args.length == 0 && System.getProperty("user.dir").startsWith("E:\\FalaComigo\\FCapps\\Kiosk")) {
//            args = "dist2\\config\\local.properties lang=en asr-eval withSTMdumpIsolated".split(" ");       //eval mode
//            args = "dist2\\config\\local.properties lang=pt asr".split(" ");       //server mode; preload=es,en,pt staticTask=en,es,pt
//            args = "dist2\\config\\local.properties autoclean lang=es staticTask=es asr tts qa".split(" ");       //server mode; autoclean
            
            args = "dist2\\config\\tts.properties lang=pt tts".split(" ");
        }

        //<editor-fold defaultstate="collapsed" desc="cmd line parse">
        ArrayList<String> argsl = new ArrayList<>(Arrays.asList(args));
        String argss = Arrays.toString(args);
        if (args.length > 0) {
            config1 = args[0];

            autoclean = argsl.contains("autoclean");
            enabletts = argsl.contains("tts");
            enableasr = argsl.contains("asr");
            enableqa = argsl.contains("qa");
            enableasreval = argsl.contains("asr-eval");

            for (String arg : argsl) {
                if (arg.startsWith("lang")) {
                    langs.clear();

                    for (String l : Arrays.asList(arg.split("=")[1].split(","))) {
                        try {
                            langs.add(Agent.lang.valueOf(l.toUpperCase().trim()));
                        } catch (IllegalArgumentException e) {
                            System.err.println("Error langs: undefined language " + l);
                            return;
                        }
                    }
                }
                if (arg.startsWith("preload")) {
                    for (String l : Arrays.asList(arg.split("=")[1].split(","))) {
                        try {
                            preloads.add(Agent.lang.valueOf(l.toUpperCase().trim()));
                        } catch (IllegalArgumentException e) {
                            System.err.println("Error preload: undefined language " + l);
                            return;
                        }
                    }
                }
                if (arg.startsWith("staticTask")) {
                    staticTasks.clear();

                    for (String l : Arrays.asList(arg.split("=")[1].split(","))) {
                        try {
                            staticTasks.add(Agent.lang.valueOf(l.toUpperCase().trim()));
                        } catch (IllegalArgumentException e) {
                            System.err.println("Error staticTask: undefined language " + l);
                            return;
                        }
                    }
                }
            }
        } else {
            System.err.println("ERROR: arguments required\n\nUsage: java -jar eTutor.jar <config.txt> lang=[en,es,pt] [asr-eval] [<options>]"
                    + "\n\nWhere options WITH asr-eval are AT MOST one of:"
                    + "\n\taudimusConfig=<filepath>.xml\tan Audimus configuration XML, with all information required for ASR"
                    + "\n\tgrxml=<filepath>.xml\tan SRGS 1.0 grammar for ASR (more at http://www.w3.org/TR/speech-grammar/)"
                    + "\nWhere options WITHOUT asr-eval (server mode) may include:\n\t...[tts [preload=[en,es,pt]]] [asr] [qa]"
                    + "\n\nalso consider java options: -Djava.library.path=<jAudimus folder> and -Dfile.encoding=ISO-8859-1"
                    + "\n\ncontact pedro.fialho@l2f.inesc-id.pt");

            return;
        }

        if (enableasreval && langs.size() > 1) {
            System.err.println("ERROR: only 1 language allowed per evaluation (i.e., ... lang=[pt | en | es] ...)");
            return;
        }

        //for ctrl+c in linux
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            @Override
//            public void run() {
//                Runtime.getRuntime().halt(-1);
//                System.exit(-1);
//                for (Thread t : Thread.getAllStackTraces().keySet()) {
//                    t.interrupt();
//                }
//
//            }
//        });
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="on Linux, start enter/return listener thread, which forces quit">
        String OS = System.getProperty("os.name").toLowerCase();
        if (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0) {
            new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Scanner reader = new Scanner(System.in);
                                reader.nextLine();

                                String pid = ManagementFactory.getRuntimeMXBean().getName();
                                pid = pid.substring(0, pid.indexOf("@"));

                                System.err.println("killing pid: " + pid);
                                try {
                                    Runtime.getRuntime().exec("kill -9 " + pid);       //CLibrary.INSTANCE.getpid()
                                } catch (IOException ex) {
                                }

                            } catch (Exception ex) {
//                                System.err.println(" error: " + ex);
                            }
                        }
                    }).start();
        }
        //</editor-fold>

        final Kiosk af;
        try {
            af = new Kiosk(config1);
        } catch (Exception ex) {
            System.err.println("startup error: " + ex.getMessage());
            return;
        }

        String host = af.config.getProperty("exthost", "");
        int fileport = -1;

        //<editor-fold defaultstate="collapsed" desc="setup file server (or fail before ASR loading)">
        if (!enableasreval && enabletts
                && ((langs.contains(lang.PT) && af.config.getProperty("ttsRemoteURL", "").isEmpty())
                || (langs.contains(lang.EN) && af.config.getProperty("ttsRemoteURLEN", "").isEmpty())
                || (langs.contains(lang.ES) && af.config.getProperty("ttsRemoteURLES", "").isEmpty()))) {
            //then: server mode and some tts is locally active. TODO: add mode switcher
            if (af.config.getProperty("fileport", "").isEmpty()) {
                System.err.println("config is missing file server port");
                return;
            }

            fileport = Integer.parseInt(af.config.getProperty("fileport"));
            new FileServer(host, fileport, af.config.getProperty("dump")).start();
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="comment: stm count">
                /*
         * 
         * 
         * 
         * /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
         * 
         * 
         * 
         * 
         */
//        ASReval enEval = new ASReval(
//                af.config.getProperty("debugFilesFolder"),
//                af.config.getProperty("audioFilenameEndsWith"),
//                af.config.getProperty("asrTransEndsWith"),
//                af.config.getProperty("dump"));
//
//        try {
//            int totalstmlines = 0;
//            String stmdir = af.config.getProperty("tmStmFolder");
//            TreeSet<String> stms = new TreeSet<>();
//            for (String stm1 : new File(stmdir).list()) {
//                stms.add(stm1.replaceAll(".stm", "").trim());
//            }
//
//            String refs = "";
//            String hyps = "";
//            DateFormat outputFormat = new SimpleDateFormat("yy'Y'MM'M'dd'D'HH'h'mm'm'ss's'");
//            DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'_'HH'h'mm'm'ss's'");
//            HashMap<String, Integer> finished = new HashMap<>();
//
//            boolean dumpOnly = true;
//            TreeSet<LogFile> logs = enEval.getDebugFiles();
//            for (String stm : stms) {
////                                        if (!stm.startsWith("2012-08-01")) {
////                                            continue;
////                                        }
//
//                ArrayList<StmLine> interacts = enEval.getCleanSTM(stmdir + "/" + stm + ".stm");
//
//                //<editor-fold defaultstate="collapsed" desc="dumpOnly ?">
//                //                    refs += txtonlystm;
//                //                    String[] interacts = txtonlystm.split("\n");
//
//                if (dumpOnly) {
//                    refs = "";
//                    for (int k = 0; k < interacts.size(); k++) {
//                        StmLine it = interacts.get(k);
//
//                        refs += it.cleanTM + "\n";      //(k != (interacts.size() - 1) ? "\n" : "")
//                        totalstmlines++;
//                    }
//                    Common.stringToFile(refs, af.config.getProperty("dump") + "segs/" + stm + ".txt", "UTF-8");
//
//                    continue;
//                }
//                //</editor-fold>
//
//                System.err.println("- log: " + stm);
//                System.err.println("stm line num: " + interacts.size());
//
//                for (LogFile l : logs) {
//                    if (l.id.equalsIgnoreCase(stm)) {
//                        Date date = inputFormat.parse(l.id);
//                        final String outDate = outputFormat.format(date);
//                        int logAdvance2in1 = 0;
//                        if (finished.containsKey(outDate)) {
//                            logAdvance2in1 = finished.get(outDate) + 1;
//                        }
//
//                        ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> segs = ASReval.getAudioSegments(l.audioPath, null, null);     //                            File d = new File("dump\\segs\\" + stm + "\\");       //                            d.mkdir();
//                        System.err.println("adv segs num: " + segs.size());
//
//                        int j2 = 0;
//                        for (int i = 0; i < interacts.size(); i++) {
//                            StmLine inter = interacts.get(i);
//
//                            final int jj = i + logAdvance2in1;
//                            j2 = jj;
//
//                            //<editor-fold defaultstate="collapsed" desc="discard speakers">
//                            //                                if (inter.nonStdSpkr != null) {
//                            //                                    refs += "\n";
//                            //                                    hyps += "\n";
//                            //                                    continue;
//                            //                                }
//                            //</editor-fold>
//
//                            //                                refs += inter.cleanTM + "\n";
//                            //                                                String recog = asr2013.recognizeBytes(segs.get(i).getValue());
//                            //                                                String recogBR = asr2013br.recognizeBytes(segs.get(i).getValue());
//
//                            //                                                String toSend = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><questions>"
//                            //                                                        + "<q>" + recog + "</q>"
//                            //                                                        + "<q>" + recogBR + "</q>"
//                            //                                                        + "</questions>";
//
//                            //<editor-fold defaultstate="collapsed" desc="for dump only">
//                            //                                if (!recog.equals("_REPEAT_")) {
//                            //                                    hyps += recog + "\n";
//                            //                                } else {
//                            //                                    hyps += "\n";
//                            //                                }
//                            //</editor-fold>
//
//                            String id = "asr2013pt+br" + "_" + "fullTM";
//                            String src = "speech_" + outDate + "_#" + jj + "_" + inter.sfim + "s";
//                            //                                                qaeval.ask(id, src, toSend);
//
//                            //                                                System.out.println("sent: " + toSend + " | id=" + id + " | source=" + src);
//
//                            totalstmlines++;
//                        }
//
//                        finished.put(outDate, j2);
//                        System.gc();
//
//                        break;
//                    }
//                }
//            }
//
//            System.err.println("totalstmlines: " + totalstmlines);
//
//            try {
//                System.err.println("waiting...");
//                Thread.sleep(100000);     //1s = 1000
//            } catch (InterruptedException ex) {
//            }
//        } catch (Exception ex) {
//            System.err.println(ex);
//        }
        /*
         * 
         * 
         * 
         * /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
         * 
         * 
         * 
         * 
         */
//                //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="enable asr">
        if (enableasr || enableasreval) {        // 
            af.asr = new HashMap<>();
            af.asrkws = new HashMap<>();

            if (!argss.contains("audimusConf") && !argss.contains("grxml")) {
                System.err.println("asr (INFO): using default ASR (as per config)");

                try {
                    for (Agent.lang l : langs) {
                        if (l.equals(Agent.lang.PT) && !af.config.getProperty("asrRemoteURL", "").isEmpty()) {
                            af.asr.put(l, new ASRRemote(af.config.getProperty("asrRemoteURL"), l));
                            af.remoteASRs.add(l);
                            continue;
                        }
                        if (l.equals(Agent.lang.EN) && !af.config.getProperty("asrRemoteURLEN", "").isEmpty()) {
                            af.asr.put(l, new ASRRemote(af.config.getProperty("asrRemoteURLEN"), l));
                            af.remoteASRs.add(l);
                            continue;
                        }
                        if (l.equals(Agent.lang.ES) && !af.config.getProperty("asrRemoteURLES", "").isEmpty()) {
                            af.asr.put(l, new ASRRemote(af.config.getProperty("asrRemoteURLES"), l));
                            af.remoteASRs.add(l);
                            continue;
                        }

                        System.err.println("--loading asr for " + l.name());
                        af.asr.put(l, af.setASR(l, staticTasks.contains(l)));
                                                
                        af.asrkws.put(l, af.setASR(l, false));      // !!!!!!!!!!!!!!!!  to fix/code better...
                        
                        /*
                         *      BR exception
                         */
                        if (l.equals(Agent.lang.PT)
                                && !af.config.getProperty("audimusConfigBR", "").isEmpty()
                                && new File(af.config.getProperty("audimusConfigBR")).exists()) {
                            System.err.println("--loading asr for BR");
                            af.asr.put(Agent.lang.BR, af.setASR(Agent.lang.BR, true));
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("asr loading error: ");
                    ex.printStackTrace();

                    if (OS.indexOf("windows") >= 0 && !System.getProperty("user.dir").startsWith("E:\\FalaComigo\\FCapps\\")) {      //
                        String pid = ManagementFactory.getRuntimeMXBean().getName();
                        pid = pid.substring(0, pid.indexOf("@"));

                        try {
                            Runtime.getRuntime().exec("Taskkill /PID " + pid + " /F");       //CLibrary.INSTANCE.getpid()
                        } catch (IOException ex2) {
                            System.err.println("taskkill exec failed: " + ex2);
                        }
                    }

                    //send mail with EX
                    //                return;
                }
            } else {
                //<editor-fold defaultstate="collapsed" desc="parse cmd line args">
                if (langs.size() > 1) {
                    System.err.println("ERROR: only 1 language allowed per evaluation (i.e., ... lang=[pt | en | es] ...) YET");
                    return;
                }
                try {
                    for (Agent.lang l : langs) {
                        if (argss.contains("audimusConf")) {
                            for (String a : argsl) {
                                if (a.startsWith("audimusConf")) {
                                    ASR a1 = new Audimus(a.split("=")[1]);
                                    a1.init();
                                    af.asr.put(l, a1);
                                    break;
                                }
                            }
                        } else {
                            if (argss.contains("grxml")) {
                                for (String a : argsl) {
                                    if (a.startsWith("grxml")) {
                                        for (Agent.lang ln : langs) {
                                            ASR asrpt = null;
                                            switch (ln) {
                                                case EN:
                                                    //<editor-fold defaultstate="collapsed" desc="en">
                                                    if (af.config.getProperty("audimusConfigGramEN", "").isEmpty()
                                                            || af.config.getProperty("modelsDirEN", "").isEmpty()) {
                                                        throw new Exception("EN missing config");
                                                    }

                                                    if (!new File(af.config.getProperty("audimusConfigGramEN")).exists()) {
                                                        throw new Exception("EN unable to find path in config");
                                                    }

                                                    asrpt = new Audimus(
                                                            af.config.getProperty("audimusConfigGramEN"));

                                                    asrpt.init();
                                                    asrpt.loadTask(a.split("=")[1],
                                                            af.config.getProperty("modelsDirEN"),
                                                            ln.toString());
                                                    //</editor-fold>
                                                    break;
                                                case ES:
                                                    //<editor-fold defaultstate="collapsed" desc="es">
                                                    if (af.config.getProperty("audimusConfigGramES", "").isEmpty()
                                                            || af.config.getProperty("modelsDirES", "").isEmpty()) {
                                                        throw new Exception("ES missing config");
                                                    }

                                                    if (!new File(af.config.getProperty("audimusConfigGramES")).exists()) {
                                                        throw new Exception("ES unable to find path in config");
                                                    }

                                                    asrpt = new Audimus(
                                                            af.config.getProperty("audimusConfigGramES"));

                                                    asrpt.init();
                                                    asrpt.loadTask(a.split("=")[1],
                                                            af.config.getProperty("modelsDirES"),
                                                            ln.toString());
                                                    //</editor-fold>
                                                    break;
                                                case PT:
                                                    //<editor-fold defaultstate="collapsed" desc="pt">
                                                    if (af.config.getProperty("audimusConfigGram", "").isEmpty()
                                                            || af.config.getProperty("modelsDir", "").isEmpty()) {
                                                        throw new Exception("PT missing config");
                                                    }

                                                    if (!new File(af.config.getProperty("audimusConfigGram")).exists()) {
                                                        throw new Exception("PT unable to find path in config");
                                                    }

                                                    asrpt = new Audimus(
                                                            af.config.getProperty("audimusConfigGram"));

                                                    asrpt.init();
                                                    asrpt.loadTask(a.split("=")[1],
                                                            af.config.getProperty("modelsDir"),
                                                            ln.toString());
                                                    //</editor-fold>
                                                    break;
                                                default:
                                                    break;
                                            }

                                            af.asr.put(l, asrpt);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("asr loading error: " + ex.getMessage());
                    return;
                }
                //</editor-fold>
            }
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="enable qa">
        if (enableqa && !af.config.getProperty("QAfunc", "").isEmpty()) {
            if (enableasreval && langs.size() > 1) {
                System.err.println("ERROR: only 1 language allowed per evaluation (i.e., ... lang=[pt | en | es] ...) YET");
                return;
            }

            af.qa = new HashMap<>();

            //non dependent on the cmd line lang parameter...PT and EN
            if (!af.config.getProperty("QAfuncTP", "").isEmpty()) {
                //<editor-fold defaultstate="collapsed" desc="talkpedia+sss (ie, PT+EN)">
                af.qaTalkpedia = new HashMap<>();
                
                if (!af.config.getProperty("QAendPointTP", "").isEmpty()) {
                    af.qaTalkpedia.put(Agent.lang.PT, new SOAPclient(af.config.getProperty("QAendPointTP"), af.config.getProperty("QAfuncTP")));
                }
                if (!af.config.getProperty("QAendPointTPEN", "").isEmpty()) {
                    af.qaTalkpedia.put(Agent.lang.EN, new SOAPclient(af.config.getProperty("QAendPointTPEN"), af.config.getProperty("QAfuncTP")));
                }
                //</editor-fold>
                
                //<editor-fold defaultstate="collapsed" desc="lcoheur exps">
                af.qaLCexp = new HashMap<>();
                
                if (!af.config.getProperty("QAendPointLCexp", "").isEmpty()) {
                    af.qaLCexp.put(Agent.lang.PT, new SOAPclient(af.config.getProperty("QAendPointLCexp"), af.config.getProperty("QAfuncTP")));
                }
                if (!af.config.getProperty("QAendPointLCexpEN", "").isEmpty()) {
                    af.qaLCexp.put(Agent.lang.EN, new SOAPclient(af.config.getProperty("QAendPointLCexpEN"), af.config.getProperty("QAfuncTP")));
                }
                //</editor-fold>
            }

            for (Agent.lang l : langs) {
                if (l.equals(Agent.lang.PT) && !af.config.getProperty("QAendPoint", "").isEmpty()) {
                    af.qa.put(Agent.lang.PT, new SOAPclient(af.config.getProperty("QAendPoint"), af.config.getProperty("QAfunc")));
                }
                if (l.equals(Agent.lang.EN) && !af.config.getProperty("QAendPointEN", "").isEmpty()) {
                    af.qa.put(Agent.lang.EN, new SOAPclient(af.config.getProperty("QAendPointEN"), af.config.getProperty("QAfunc")));
                }
                if (l.equals(Agent.lang.ES) && !af.config.getProperty("QAendPointES", "").isEmpty()) {
                    af.qa.put(Agent.lang.ES, new SOAPclient(af.config.getProperty("QAendPointES"), af.config.getProperty("QAfunc")));
                }
            }
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="eval mode">
        if (enableasreval) {
            ASReval anEval = new ASReval(
                    af.config.getProperty("debugFilesFolder"),
                    af.config.getProperty("audioFilenameEndsWith"),
                    af.config.getProperty("asrTransEndsWith"),
                    af.config.getProperty("dump"));

            try {
                //<editor-fold defaultstate="collapsed" desc="cmd line usable dumpASR">
                for (Agent.lang l : af.asr.keySet()) {
                    if (argss.contains("withSTM")
                            && !af.config.getProperty("tmStmFolder", "").isEmpty()
                            && new File(af.config.getProperty("tmStmFolder")).exists()) {
                        System.err.println("asreval (INFO): using STM based segments (only for total count)");

                        if (enableqa) {
                            QA qaeval = af.qa.get(l);
                            qaeval.setID(af.config.getProperty("QAid", ""));
                            anEval.dumpASRandSendToQA(af.asr.get(l), af.config.getProperty("tmStmFolder"), argss.contains("withSTMdumpIsolated"), qaeval);
                        } else {
                            anEval.dumpASR(af.asr.get(l), af.config.getProperty("tmStmFolder"), argss.contains("withSTMdumpIsolated"));
                        }
                    } else {
                        if (enableqa) {
                            QA qaeval = af.qa.get(l);
                            qaeval.setID(af.config.getProperty("QAid", ""));
                            anEval.sendASRtoQA(af.asr.get(l), qaeval);
                        } else {
                            anEval.dumpASR(af.asr.get(l));
                        }
                    }
                }
                //</editor-fold>
            } catch (Exception ex) {
                System.err.println("asreval: " + ex);
            }

            if (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0) {
                String pid = ManagementFactory.getRuntimeMXBean().getName();
                pid = pid.substring(0, pid.indexOf("@"));

                try {
                    Runtime.getRuntime().exec("kill -9 " + pid);       //CLibrary.INSTANCE.getpid()
                } catch (IOException ex) {
                }
            }

            return;             //avoid multiple usages in a single execution
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="server mode">
        //<editor-fold defaultstate="collapsed" desc="autoclean">
        if (autoclean) {
            //clear inconsistent caches (del 0b files)
//            for (File f : new File(af.config.getProperty("dump")).listFiles()) {
//                if (f.isDirectory()) {
//                    for (File f1 : f.listFiles()) {
//                        if (f1.length() == 0) {
//                            f1.delete();
//                        }
//                    }
//                }
//            }

            // to config ?!
            // X GB, , as shown in Win Explorer + .YWZ for 100s, 10s and 1s of MB (ie, X.YWZ)
            Float cleanGBWarnLimit = new Float(108.9);  //1.1
            Float cleanGBLimit = new Float(108.9);      //1.0
            String[] cleanRelFoldersCSV = new String[]{"dump/opinions/", "debugFiles/"};
            String[] emailDest = new String[]{"falacomigo.edgar@l2f.inesc-id.pt"};
            String emailSourceUser = "edgar.l2f.inescid.pt";
            String emailSourcePass = "falacomigo";

            try {
                FileStore store = Files.getFileStore(new File(System.getProperty("user.dir")).toPath());
                Float freespace = Float.parseFloat(new DecimalFormat("###.###").format(store.getUsableSpace() / Math.pow(1024, 3)).replace(',', '.'));

                if (freespace.equals(cleanGBWarnLimit)) {
                    //send email
                }

                if (freespace.equals(cleanGBLimit)) {
                    for (String f : cleanRelFoldersCSV) {
                        Common.deleteDirectory(new File(f));
                    }

                    //send email
                }
            } catch (Exception e) {
                System.err.println("error autocleaning: " + e.toString());

            }
        }
        //</editor-fold>

        //opinion management
        (af.opinionDump = new File(af.config.getProperty("dump"), "opinions")).mkdir();

        af.multipartDump = new File(af.config.getProperty("dump"), "multiparts");
        if (af.multipartDump.exists()) {
            Common.deleteDirectory(af.multipartDump);
        }
//        af.multipartDump.mkdir();

        af.mp3enc = af.config.getProperty("mp3enc");
        af.mp3encopt = af.config.getProperty("mp3encopt");

        //<editor-fold defaultstate="collapsed" desc="enable tts">
        if (enabletts) {
            //prepare tts
            af.tts = new HashMap<>();

            try {
                for (final Agent.lang l : langs) {
                    if (l.equals(Agent.lang.PT) && !af.config.getProperty("ttsRemoteURL", "").isEmpty()) {
                        af.tts.put(l, new TTSRemote(af.config.getProperty("ttsRemoteURL"), l));
                        af.remoteTTSs.add(l);
                        continue;
                    }
                    if (l.equals(Agent.lang.EN) && !af.config.getProperty("ttsRemoteURLEN", "").isEmpty()) {
                        af.tts.put(l, new TTSRemote(af.config.getProperty("ttsRemoteURLEN"), l));
                        af.remoteTTSs.add(l);
                        continue;
                    }
                    if (l.equals(Agent.lang.ES) && !af.config.getProperty("ttsRemoteURLES", "").isEmpty()) {
                        af.tts.put(l, new TTSRemote(af.config.getProperty("ttsRemoteURLES"), l));
                        af.remoteTTSs.add(l);
                        continue;
                    }

                    //else:
                    //<editor-fold defaultstate="collapsed" desc="commented: thread start">
                    //in new thread, for speed
                    //                        new Thread(
                    //                                new Runnable() {
                    //                                    @Override
                    //                                    public void run() {
                    //                                        try {
                    //</editor-fold>
                    af.tts.put(l, af.setTTS(l, preloads.contains(l)));
                    //<editor-fold defaultstate="collapsed" desc="commented: thread end">
                    //                                        } catch (Exception ex) {
                    //                                            System.err.println(l.name() + " preload error: " + ex.getMessage());
                    //                                        }
                    //                                    }
                    //                                }).start();
                    //</editor-fold>
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                System.err.println("tts loading error: " + ex);

                //send mail with EX
                //                return;
            }
        }
        //</editor-fold>

//        try {
//            af.tts.get(lang.EN).getAudio("", "I know the history of Monserrate like no one else does. Francis Cook, an English millionaire, built the palace between eighteen fifty-eight and eighteen sixty-four, on the structure of another palace. The name Monserrate comes from to the first chapel built here by Father Gaspar Black, after a pilgrimage he made to the shrine of Montserrat in Catalonia. Monserrate Palace's architectural style is mainly neo-Gothic revivalist, characterised by eclecticism, much in vogue in the Romantic period. The palace was built to be a summer residence.", "", TTS.AUDIOFORMAT.OGG);
//        } catch (Exception ex) {
//            Logger.getLogger(Kiosk.class.getName()).log(Level.SEVERE, null, ex);
//        }

        String fnameprefx = af.config.getProperty("fnameprefx", "");        //empty allowed, servlet handled

        int reqport = Integer.parseInt(af.config.getProperty("requestport"));
        new RequestServer(af, host, reqport, fileport, fnameprefx).start();
        //</editor-fold>
    }
}
//<editor-fold defaultstate="collapsed" desc="comment">
//
//    public String getSRGSpath(String corporaDir) throws Exception {
//        HashSet<String> res = new HashSet<String>();
//
//        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
//        domFactory.setNamespaceAware(true); // never forget this!
//        DocumentBuilder builder = domFactory.newDocumentBuilder();
//
//        for (File p : new File(corporaDir).listFiles(new FilenameFilter() {
//
//            @Override
//            public boolean accept(File f, String s) {
//                return s.endsWith(".xml");
//            }
//        })) {
//            System.err.println("parsing: " + p.getPath());
//            Document doc = builder.parse(p.getPath());
//
//            XPathFactory factory = XPathFactory.newInstance();
//            XPath xpath = factory.newXPath();
//            XPathExpression expr = xpath.compile("//q/text()");
//            Object result = expr.evaluate(doc, XPathConstants.NODESET);
//            NodeList nodes = (NodeList) result;
//            for (int i = 0; i < nodes.getLength(); i++) {
//                String asrkey = nodes.item(i).getNodeValue();
//                res.add(NLP.normPunctLCase(asrkey));           //also remove diacritics?
//            }
//        }
//        //write grammar
//        final File sgrsFile = File.createTempFile("grammar_full", ".grxml");
//        sgrsFile.deleteOnExit();
//        try (FileWriter writer = new FileWriter(sgrsFile)) {
//            writer.write("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n");
//            writer.write("<grammar root=\"main\" version=\"1.0\" xmlns=\"http://www.w3.org/2001/06/grammar\" xml:lang=\"pt-PT\">\n");
//            writer.write("\t<rule id=\"main\">\n");
//            writer.write("\t\t<item repeat=\"0-1\">\n");
//            writer.write("\t\t\t<one-of>\n");
//            for (String asrkey : res) {
//                writer.write("<item>");
//                writer.write(asrkey);
//                writer.write("</item>\n");
//            }
//            writer.write("\t\t\t</one-of>\n");
//            writer.write("\t\t</item>\n");
//            writer.write("\t</rule>\n");
//            writer.write("</grammar>\n");
//        }
//
//        return sgrsFile.getPath();
//    }
//
//
//
//
//clean tts cache
//            for (File f : new File(config.getProperty("dump")).listFiles()) {
//                System.out.println("deleting: " + f.getAbsolutePath());
//                if (f.isDirectory()) {
//                    Common.deleteDirectory(f);
//                } else {
//                    Files.delete(f.toPath());
//                }
//            }
//</editor-fold>
//<editor-fold defaultstate="collapsed" desc="preload tests">
//        try {
////            File file = new File("dump\\logPRELOAD" + new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime()) + ".txt");
////            PrintStream printStream = new PrintStream(new FileOutputStream(file));
////            System.setOut(printStream);
//
////            new Thread(
////                    new Runnable() {
////                        @Override
////                        public void run() {
////                            try {
////                                af.setTTS(Agent.lang.EN, true);
////                            } catch (Exception ex) {
////                                System.err.println("EN preload error: " + ex.getMessage());
////                            }
////                        }
////                    }).start();
//
////            af.setTTS(Agent.lang.EN, true);
//        } catch (Exception ex) {
//            System.err.println("preload error: " + ex.getMessage());
//        }
//
//        System.exit(1);
//</editor-fold>
//<editor-fold defaultstate="collapsed" desc="old, redundant">
//                if (!argss.contains("audimusConf") && !argss.contains("grxml")) {
//                    for (Agent.lang l : af.asr.keySet()) {
//                        System.err.println("asreval (INFO): evaluating default ASR (as per config)");
//                        if (argss.contains("withSTM") && !af.config.getProperty("tmStmFolder", "").isEmpty() && new File(af.config.getProperty("tmStmFolder")).exists()) {
//                            System.err.println("asreval (INFO): using STMs");
//                            enEval.dumpASR(af.asr.get(l), af.config.getProperty("tmStmFolder"), argss.contains("withSTMdumpIsolated"));
//                        } else {
//                            enEval.dumpASR(af.asr.get(l));
//                        }
//                    }
//                } else {
//                    if (argss.contains("audimusConf")) {
//                        for (String a : argsl) {
//                            if (a.startsWith("audimusConf")) {
//                                ASR a1 = new Audimus(a.split("=")[1]);
//                                a1.init();
//                                
//                                if (argss.contains("withSTM") && !af.config.getProperty("tmStmFolder", "").isEmpty() && new File(af.config.getProperty("tmStmFolder")).exists()) {
//                                    System.err.println("asreval (INFO): using STMs");
//                                    enEval.dumpASR(a1, af.config.getProperty("tmStmFolder"), argss.contains("withSTMdumpIsolated"));
//                                } else {
//                                    enEval.dumpASR(a1);
//                                }
//                                //                                enEval.dumpASR(a1);
//                                break;
//                            }
//                        }
//                    } else {
//                        if (argss.contains("grxml")) {
//                            for (String a : argsl) {
//                                if (a.startsWith("grxml")) {
//                                    for (Agent.lang ln : langs) {
//                                        ASR asrpt = null;
//                                        switch (ln) {
//                                            case EN:
//                                                //<editor-fold defaultstate="collapsed" desc="en">
//                                                if (af.config.getProperty("audimusConfigGramEN", "").isEmpty()
//                                                        || af.config.getProperty("modelsDirEN", "").isEmpty()) {
//                                                    throw new Exception("EN missing config");
//                                                }
//                                                
//                                                if (!new File(af.config.getProperty("audimusConfigGramEN")).exists()) {
//                                                    throw new Exception("EN unable to find path in config");
//                                                }
//                                                
//                                                asrpt = new Audimus(
//                                                        af.config.getProperty("audimusConfigGramEN"));
//                                                
//                                                asrpt.init();
//                                                asrpt.loadTask(a.split("=")[1],
//                                                        af.config.getProperty("modelsDirEN"),
//                                                        ln.toString());
//                                                //</editor-fold>
//                                                break;
//                                            case ES:
//                                                //<editor-fold defaultstate="collapsed" desc="es">
//                                                if (af.config.getProperty("audimusConfigGramES", "").isEmpty()
//                                                        || af.config.getProperty("modelsDirES", "").isEmpty()) {
//                                                    throw new Exception("ES missing config");
//                                                }
//                                                
//                                                if (!new File(af.config.getProperty("audimusConfigGramES")).exists()) {
//                                                    throw new Exception("ES unable to find path in config");
//                                                }
//                                                
//                                                asrpt = new Audimus(
//                                                        af.config.getProperty("audimusConfigGramES"));
//                                                
//                                                asrpt.init();
//                                                asrpt.loadTask(a.split("=")[1],
//                                                        af.config.getProperty("modelsDirES"),
//                                                        ln.toString());
//                                                //</editor-fold>
//                                                break;
//                                            case PT:
//                                                //<editor-fold defaultstate="collapsed" desc="pt">
//                                                if (af.config.getProperty("audimusConfigGram", "").isEmpty()
//                                                        || af.config.getProperty("modelsDir", "").isEmpty()) {
//                                                    throw new Exception("PT missing config");
//                                                }
//                                                
//                                                if (!new File(af.config.getProperty("audimusConfigGram")).exists()) {
//                                                    throw new Exception("PT unable to find path in config");
//                                                }
//                                                
//                                                asrpt = new Audimus(
//                                                        af.config.getProperty("audimusConfigGram"));
//                                                
//                                                asrpt.init();
//                                                asrpt.loadTask(a.split("=")[1],
//                                                        af.config.getProperty("modelsDir"),
//                                                        ln.toString());
//                                                //</editor-fold>
//                                                break;
//                                            default:
//                                                break;
//                                        }
//                                        
//                                        enEval.dumpASR(asrpt);
//                                    }
//                                    break;
//                                }
//                            }
//                        }
//                    }
//                }
//</editor-fold>
//<editor-fold defaultstate="collapsed" desc="comment">
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            @Override
//            public void run() {
//                System.err.println("exiting, by killing pid " + CLibrary.INSTANCE.getpid());
//                String OS = System.getProperty("os.name").toLowerCase();
//                if (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0) {
//                    try {
//                        Runtime.getRuntime().exec("kill -9 " + CLibrary.INSTANCE.getpid());
//                    } catch (IOException ex) {
//                    }
//                }
//            }
//        });
//</editor-fold>
//<editor-fold defaultstate="collapsed" desc="tmp">
//<editor-fold defaultstate="collapsed" desc="comment">
//                TreeSet<Log> logs = enEval.getDebugFiles();
//                for (Log l : logs) {
//                    System.err.println("-starting: " + l.id);
//                    ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> segs = ASReval.getAudioSegments(l.audioPath, null);
//                    System.out.print(segs.get(0).getValue().length + " | ");
//                    System.out.print(segs.get(1).getValue().length + " | ");
//                    System.out.print(segs.get(segs.size()-2).getValue().length + " | ");
//                    System.out.print(segs.get(segs.size()-1).getValue().length + "\n");
//                }
//</editor-fold>
                /*
 **************************redirect .out to file
 */
//1sSil_ampl_cSNSsAGC_precomp
//                File file = new File("dump\\qaDump_" + new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime()) + ".txt");
//                PrintStream printStream = new PrintStream(new FileOutputStream(file));
//                System.setOut(printStream);
//                System.setErr(printStream);
                /*
 **************************prepare ASRs
 */
//<editor-fold defaultstate="collapsed" desc="comment">
//                ASR asr2012 = new Audimus("dist2/resources/audimus/asr_en_fc.xml");
//                asr2012.init();
//                asr2012.setID("asr2012");
//                ASR grammar1 = new Audimus("dist2/resources/audimus/grammarBased/asr_en_fc_keyspot.xml");
//                grammar1.init();
//                grammar1.setID("grammar2");
//                grammar1.loadTask(
//                        getASRgrammar(af.config.getProperty("corpora"), Agent.lang.EN),
//                        af.config.getProperty("modelsDir"),
//                        "eneval");
//                ASR generic = new Audimus("dist2/resources/audimus/generic/asr_pt_fc_cSNSsAGC.xml");
//                generic.init();
//                generic.setID("generic");
//</editor-fold>
//                ASR asr2013 = new Audimus("dist2/resources/audimus/asr2013/asr_pt_fc_cSNSsAGC_precomp.xml");        //asr_pt_fc_cSNSsAGC_precomp.xml        asr_en_fc_cSNSsAGC.xml
//                asr2013.init();
//                asr2013.setID("asr2013");
//                
//                ASR asr2013br = new Audimus("dist2/resources/audimus/asr2013/asr_ptbr_fc.xml");        //asr_pt_fc_cSNSsAGC_precomp.xml        asr_en_fc_cSNSsAGC.xml
//                asr2013br.init();
//                asr2013br.setID("asr2013br");
//
//                //<editor-fold defaultstate="collapsed" desc="comment">
//                //                ArrayList<ASR> asrs = new ArrayList<>();
//                //                asrs.add(asr2013);
//                //                asrs.add(asr2012);
//                //                asrs.add(generic);
//                //</editor-fold>
//
//                final QA qaeval = new SOAPclient(af.config.getProperty("QAendPoint"), af.config.getProperty("QAfunc"));
//<editor-fold defaultstate="collapsed" desc="testing">
//
//                //<editor-fold defaultstate="collapsed" desc="comment">
//                //                ArrayList<QA> qas = new ArrayList<>();
//                //                qas.add(qaeval);
//                //                enEval.sendASRtoQA(asrs, qas);
//                //                enEval.organizeDebugFiles();
//                //                enEval.getSTMs(asr2013);
//                //                enEval.dumpASR(asrs, "comTudo");
//                //</editor-fold>
//
//                //<editor-fold defaultstate="collapsed" desc="comment">
//                //                enEval.getSTMs(asr2013);
//
//                //                TreeSet<Log> logs1 = enEval.getDebugFiles();
//                //                for (Log l : logs1) {
//                //                    if (!l.id.startsWith("2012-08-20")) {
//                //                        continue;
//                //                    }
//                //
//                //                    ArrayList<AbstractMap.SimpleEntry<AbstractMap.SimpleEntry<Float, Float>, byte[]>> segs = ASReval.getAudioSegments(l.audioPath, null, null);
//                //                    System.err.println();
//                //                }
//
//                //                File d1 = new File("dump\\segs\\tmp123\\");
//                //                            d1.mkdir();
//                //                ASReval.getAudioSegments("e:\\out-received.raw", d1.getAbsolutePath());
//                //                System.exit(0);
//                //</editor-fold>
//
//
//                //<editor-fold defaultstate="collapsed" desc="comment">
//                //                System.err.println("refs num: " + refs.split("\n").length);
//                //                System.err.println("hyps num: " + hyps.split("\n").length);
//                //
//                //                if (refs.split("\n").length != hyps.split("\n").length) {
//                //                    System.err.println("-------------------------#refs != #hyps");
//                //                    try {
//                //                        Thread.sleep(10000);     //1s = 1000
//                //                    } catch (InterruptedException ex) {
//                //                    }
//                //                }
//                //
//                //                String id = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
//                //                Common.stringToFile(refs, af.config.getProperty("dump") + "results/refs_" + id + ".txt", "UTF-8");
//                //                Common.stringToFile(hyps, af.config.getProperty("dump") + "results/hyps_asr2013_" + id + ".txt", "UTF-8");
//
//                //                System.out.println("---------------------------------------");
//                //                enEval.dumpASR(asr2013);
//                //                System.out.println("---------------------------------------");
//                //                enEval.sendASRtoQA(asr2013, qaeval);
//
//                //                try {
//                //                    System.err.println("done. sleeping until exit...");
//                //                    Thread.sleep(100000);     //1s = 1000
//                //                } catch (InterruptedException ex) {
//                //                }
//                //</editor-fold>
//                //</editor-fold>
//
////                enEval.dumpASR(asr2013);
//
//                try {
//                    System.err.println("waiting...");
//                    Thread.sleep(100000);     //1s = 1000
//                } catch (InterruptedException ex) {
//                }
//                System.exit(1);
                //</editor-fold>