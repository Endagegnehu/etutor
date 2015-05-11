/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package speech;

import agent.Agent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import utils.Common;

/**
 *
 * @author pedrofialho
 */
public class ForceAlign implements TTS {

    private final String oggenc;
    private final String oggencopt;
    private final String mp3enc;
    private final String mp3encopt;
    private final File dumpDir;
    private Audimus asr;
    private Agent.lang ln = Agent.lang.PT;

    public ForceAlign(String dump, String oggenc, String oggencopt, String mp3enc, String mp3encopt, Audimus asr, Agent.lang ln) {
        this.dumpDir = new File(dump);
        this.oggenc = new File(oggenc).getPath();
        this.oggencopt = oggencopt;
        this.mp3enc = new File(mp3enc).getPath();
        this.mp3encopt = mp3encopt;
        this.asr = asr;
        this.ln = ln;
    }

    @Override
    public void init() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean voiceAvailable(String voice) {
        return true;
    }

    private File getWav(String fname, byte[] sound) {
        File f2 = new File(fname + ".wav");
        try {
            if (!f2.exists()) {
                ByteArrayInputStream bais = new ByteArrayInputStream(sound);
                AudioInputStream ai = new AudioInputStream(bais, ASR.defaultRecConf, sound.length);
                AudioSystem.write(ai, AudioFileFormat.Type.WAVE, f2);
            }
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            return null;
        }

        return f2;
    }

    @Override
    public synchronized List<TTSResult> getAudio(String voice, String sen, String fnameprefx, AUDIOFORMAT af, float srate, byte[] prerecspeech, boolean usecache) throws Exception {
        String stmBasename = "s" + ln.name().toUpperCase();

        if (voice.isEmpty()) {      //enable per user cache
            voice = stmBasename;
        }

        ArrayList<TTSResult> audres = new ArrayList<TTSResult>();
        String atosynth = sen.trim().toLowerCase().replaceAll("[\\p{Punct}&&[^'-]]+", "").trim();

        if ((atosynth = atosynth.replaceAll("\\s+", " ").trim()).isEmpty()) {
            throw new Exception("no text defined, corresponding to synthesized speech audio");
        }

        String id = Common.getUniqueId(atosynth);
        File voicedir = new File(dumpDir, voice);
        voicedir.mkdir();
        File resser = new File(voicedir, id);
        TTSResult res = null;

        if (!resser.createNewFile()) {
            if (usecache) {
                System.err.println("sentence \"" + atosynth + "\" already synthesized in " + resser.getAbsolutePath());
                FileInputStream f_in = new FileInputStream(resser);
                ObjectInputStream obj_in = new ObjectInputStream(f_in);
                res = (TTSResult) obj_in.readObject();

                res.setSentence(URLEncoder.encode(atosynth, "UTF-8"));      //avoid mistyping

                audres.add(res);
                return audres;
            }
            // else: remove/replace all occurrences of a user's sentence
        }

        System.err.println("synth started: " + atosynth);
        final String fsid = id;
                final String fslnid = stmBasename;
                for (File p : voicedir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File f, String s) {
                        return s.startsWith(fsid) || s.startsWith(fslnid);       //s.endsWith(".xml") &&
                    }
                })) {
                    p.delete();
                }
        //create STM     
        File wavpath = getWav(resser.getAbsolutePath(), prerecspeech);      //is a .wav

        if (!wavpath.exists()) {
            throw new Exception("unable to find WAV file ./" + wavpath);
        }

        File stm = new File(voicedir, stmBasename + ".stm");
        stm.delete();
        String stmcont = wavpath.getName() + " 1 speaker 0.0 -1 <o,F0,X> " + atosynth;
        utils.Common.stringToFile(stmcont, stm.getAbsolutePath());

        asr.init();
        //<editor-fold defaultstate="collapsed" desc="comment">
//        String bakAudimusXML = asr.xmlconfig;
        //        String bakgrammar = asr.grammar;
        //        String bakmodels = asr.modelsdir;
        //        lang baklang = asr.lang;

        //        asr.recognizer.audimus_interrupt();
        //        asr.dispose();
        //        asr = new Audimus(audimusXML);
        //        asr.init();
//</editor-fold>
        asr.start();
        //<editor-fold defaultstate="collapsed" desc="comment">
//        asr.recognizer.audimus_pause();

        //        asr.dispose();
        //
        //        asr = new Audimus(bakAudimusXML);
        //        asr.init();
        //        asr.loadTask(bakgrammar, bakmodels, baklang.name());
        //        asr.start();
//</editor-fold>
        System.err.println("synth ended");
        
        File wfslog = new File(voicedir, stmBasename + ".wfst.log");
//        while (wfslog.length() < 3000) {
        try {
            Thread.sleep(5000);     //1s = 1000
        } catch (InterruptedException ex) {
        }
//        }

        //<editor-fold defaultstate="collapsed" desc="get phones, durations">
        String[] phones = null;
        float[] phonest = null;
        ArrayList<String> ph = new ArrayList<>();
        ArrayList<Float> pht = new ArrayList<>();

        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true); // never forget this!
        DocumentBuilder builder = domFactory.newDocumentBuilder();

        Document doc = builder.parse(wfslog);

        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();

        XPathExpression expr = xpath.compile("//Phone");       // /@text
        Object result = expr.evaluate(doc, XPathConstants.NODESET);
        NodeList nodes = (NodeList) result;
        for (int i = 0; i < nodes.getLength(); i++) {
            String phon = nodes.item(i).getAttributes().getNamedItem("string").getTextContent().trim(); //getNodeValue().trim();
            String dur = nodes.item(i).getAttributes().getNamedItem("final_time").getTextContent().trim(); //getNodeValue().trim();

            ph.add(phon);
            pht.add(Float.parseFloat(dur) / 100);
        }

        phones = ph.toArray(new String[0]);
        phonest = new float[pht.size()];
        for (int i = 0; i < pht.size(); i++) {
            phonest[i] = pht.get(i);
        }
        //</editor-fold>

        String audiofpath = fnameprefx + voice + "/";
        switch (af) {
            case MP3:
                String mp3path = resser.getAbsolutePath() + ".mp3";
                Runtime.getRuntime().exec(mp3enc + " " + mp3encopt + " " + wavpath.getAbsolutePath() + " " + mp3path).waitFor();
                wavpath.delete();
                audiofpath += new File(mp3path).getName();
                break;
            case OGG:
                String oggpath = resser.getAbsolutePath() + ".ogg";
                Runtime.getRuntime().exec(oggenc + " " + oggencopt + " " + wavpath.getAbsolutePath() + " " + oggpath).waitFor();
                wavpath.delete();
                audiofpath += new File(oggpath).getName();
                break;
            default:
                    ;
        }

        TTSResult tosend = new TTSResult(URLEncoder.encode(atosynth, "UTF-8"), audiofpath, phones, phonest);

        //serialize
        System.out.println("saving \"" + atosynth + "\" as: " + resser.getAbsolutePath());
        ObjectOutputStream obj_out = new ObjectOutputStream(new FileOutputStream(resser));
        obj_out.writeObject(tosend);

        audres.add(tosend);

        return audres;
    }

    @Override
    public String getDefaultVoice() throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setLang(Agent.lang ln) {
        this.ln = ln;
    }

    @Override
    public Agent.lang getLang() {
        return ln;
    }

    @Override
    public List<TTSResult> getAudio(String voice, String sentence, String fnameprefx, AUDIOFORMAT af, float srate) throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
