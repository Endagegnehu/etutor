package speech;

import agent.Agent;
import agent.Agent.lang;
import inesc.id.l2f.jdixi.*;
import java.io.*;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import utils.Common;

public class Dixi implements IDixiEventListener, TTS { //MergingEventListener {

    private final File dixiconf;
    private final File dixiconfSlow;
    private final File voicesDir;
    private final File dumpDir;
    private DixiResultEvent currevt = null;
    private JDixi jDixi = null;
    private JDixi jDixiSlow = null;
    private final String oggenc;
    private final String oggencopt;
    private final String mp3enc;
    private final String mp3encopt;
    private Agent.lang ln = Agent.lang.PT;
    
    public Dixi(String dixiconf, String voicesbasepath, String dump, String oggenc, String oggencopt, String mp3enc, String mp3encopt, String dixiconfSlow) {
        this.dixiconf = new File(dixiconf);
        this.dixiconfSlow = new File(dixiconfSlow);
        this.voicesDir = new File(voicesbasepath);
        this.dumpDir = new File(dump);
        this.oggenc = new File(oggenc).getPath();
        this.oggencopt = oggencopt;
        this.mp3enc = new File(mp3enc).getPath();
        this.mp3encopt = mp3encopt;
    }
    
    @Override
    public void init() throws Exception {
        if (!dixiconf.exists()) {
            System.err.println("dixi config " + dixiconf + " not found");
            return;
        }
        
        jDixi = new JDixi(dixiconf);
        jDixi.dixi_allocate();
        jDixi.dixi_registerListener(this);
        jDixi.dixi_resume();
        
        if (dixiconfSlow.exists()) {
            jDixiSlow = new JDixi(dixiconfSlow);
            jDixiSlow.dixi_allocate();
            jDixiSlow.dixi_registerListener(this);
            jDixiSlow.dixi_resume();
        }
    }

    // lazy/incomplete check
    @Override
    public boolean voiceAvailable(String voice) {
        return new File(voicesDir, voice).exists();
    }
    
    private boolean say(String voice, String sentence, float srate) {
//        jDixi.dixi_allocate();
//        sentence = sentence.replaceAll("[\\.\\?\\!\\:\\;]", "; ");

        if (voice == null || sentence == null) {
            System.err.println("null voice or sentence");
            return false;
        }
        
        if (!jDixi.dixi_getVoices().contains(voice)) {
            if (!voicesDir.exists()) {
                System.err.println("voices path " + voicesDir + " not found");
                return false;
            }
            
            VoiceDescriptor vd = new VoiceDescriptor(voice, "clunits");
            File voicedir = new File(voicesDir, voice);
            if (!voicedir.exists()) {
                System.err.println("voice " + voicedir.getPath() + " not found");
//                jDixi.dixi_finalize();
                return false;
            }
            
            vd.setBasedir(voicedir.getPath());
            
            if (srate < 0) {
                jDixiSlow.dixi_addVoice(vd);
            } else {
                jDixi.dixi_addVoice(vd);
            }
        }
        
        if (!voice.equals(jDixi.dixi_getCurrentVoice())) {
            if (srate < 0) {
                jDixiSlow.dixi_setVoice(voice);
            } else {
                jDixi.dixi_setVoice(voice);
            }
            
        }

//        if (srate != 0) {
//            System.err.println("srate: " + String.valueOf((int) srate));
//            System.err.println("srate2: " + jDixi.dixi_getParameter("speechRate"));
////            jDixi.speak(sentence, (int)srate);
//            jDixi.dixi_setParameter("speechRate", String.valueOf((int) srate));
////                vd.setProperty("speechRate", String.valueOf((int) srate));
//
//            System.err.println("srate3: " + jDixi.dixi_getParameter("speechRate"));
//        }

//        } else {
        
        if (srate < 0) {
            jDixiSlow.speak(sentence);
        }else{
            jDixi.speak(sentence);
        }
//        }
        return true;
    }
    
    private File getWav(String fname) {
        File f2 = new File(fname + ".wav");
        try {
            if (!f2.exists()) {
                ByteArrayInputStream bais = new ByteArrayInputStream(currevt.getAudioBytes());
                AudioInputStream ai = new AudioInputStream(bais, currevt.getAudioFormat(), currevt.getAudioBytes().length);
                AudioSystem.write(ai, Type.WAVE, f2);
            }
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            return null;
        }
        
        return f2;
    }
    
    private File getOgg(String fname) {
        File oggpath = new File(fname + ".ogg");
        
        System.err.print("begin wav...");
        File f2 = getWav(fname);        //absfname = abslute file name without '.<extension>'
        System.err.print("end wav\r\n");
        System.err.print("begin ogg...");
        try {
            java.lang.Runtime.getRuntime().exec(oggenc + " " + oggencopt + " " + f2.getPath() + " " + oggpath.getPath()).waitFor();
            f2.delete();
        } catch (Exception ex) {       //IOException | InterruptedException
            System.err.println(ex.getMessage());
            return null;
        }
        System.err.print("end ogg\r\n");
        
        return oggpath;
    }
    
    private File getMp3(String fname) {
        File mp3path = new File(fname + ".mp3");
        
        System.err.print("begin wav...");
        File f2 = getWav(fname);        //absfname = abslute file name without '.<extension>'
        System.err.print("end wav\r\n");
        System.err.print("begin mp3...");
        try {
            java.lang.Runtime.getRuntime().exec(mp3enc + " " + mp3encopt + " " + f2.getPath() + " " + mp3path.getPath()).waitFor();
            f2.delete();
        } catch (Exception ex) {       //IOException | InterruptedException
            System.err.println(ex.getMessage());
            return null;
        }
        System.err.print("end mp3\r\n");
        
        return mp3path;
    }
    
    @Override
    public synchronized List<TTSResult> getAudio(String voice, String sen, String fnameprefx, AUDIOFORMAT platform, float srate) throws Exception {
        ArrayList<TTSResult> audres = new ArrayList<>();
        
        String[] splits = sen.split(TTS.strSplitRegex);
        for (String sentence : splits) {
            String atosynth = sentence.toLowerCase().trim();
            while (!atosynth.isEmpty() && Character.toString(atosynth.charAt(atosynth.length() - 1)).matches("\\p{Punct}")) {
                atosynth = atosynth.substring(0, atosynth.length() - 1);
            }
            
            atosynth = atosynth.replaceAll("\u2019", "'");
            
            if ((atosynth = atosynth.replaceAll("\\s+", " ").trim()).isEmpty()) {
                continue;
            }
            
            String id = Common.getUniqueId(atosynth) + "_" + platform
                    + (srate != 0 ? "_" + String.valueOf(srate).replaceAll("[,.]", "_") : "");
            
            File voicedir = new File(dumpDir, voice);
            voicedir.mkdir();
            File resser = new File(voicedir, id);
            TTSResult res = null;
            
            if (!resser.createNewFile()) {
                //get serialized DixiResult
                System.out.println("sentence \"" + sentence + "\" already synthesized in " + resser.getAbsolutePath());
                FileInputStream f_in = new FileInputStream(resser);
                ObjectInputStream obj_in = new ObjectInputStream(f_in);
                res = (TTSResult) obj_in.readObject();
                
                res.setSentence(URLEncoder.encode(sentence, "UTF-8"));      //avoid mistyping
                audres.add(res);
                continue;
            }
            
            System.out.println("--synth started: " + atosynth);
            
            currevt = null;
            if (!say(voice, atosynth, srate)) {
                return null;
            }

//            System.out.println("--say done");

            for (int i = 0; i < TTS.waitcycle; i++) {     //wait for event
                if (currevt != null) {
//                    System.out.println("--currevt != null");
                    break;
                }
                
                try {
                    Thread.sleep((TTS.waitcyclemillis * 2));     //1s = 1000
                } catch (InterruptedException ex) {
                }
            }
            
            if (currevt == null) {
//                System.out.println("--synth error: currevt == null");
                return null;
            }
            
            System.out.println("--synth ended: " + atosynth);
            
            String[] phones = currevt.getPhones();
            float[] phonest = currevt.getPhonesEndTimes();
            
            String audiofpath = fnameprefx + voice + "/";
            switch (platform) {
                case MP3:
                    audiofpath += getMp3(resser.getAbsolutePath()).getName();
                    break;
                case OGG:
                    audiofpath += getOgg(resser.getAbsolutePath()).getName();
                    break;
                default:
                    ;
            }

//        String tosend = JSON.toJSONString(new TTSResult(URLEncoder.encode(sentence, "UTF-8"), audiofpath, phones, phonest));
            TTSResult tosend = new TTSResult(URLEncoder.encode(sentence, "UTF-8"), audiofpath, phones, phonest);

            //serialize
            System.out.println("--saving \"" + atosynth + "\" as: " + resser.getAbsolutePath());
            ObjectOutputStream obj_out = new ObjectOutputStream(new FileOutputStream(resser));
            obj_out.writeObject(tosend);
            
            audres.add(tosend);
        }
        
        return audres;
    }
    
    @Override
    public void event(DixiEvent event) {
//        System.out.println(event);
        switch (event.getID()) {
            case DixiEvent.EVENT_RESULT:
                currevt = (DixiResultEvent) event;
                break;
        }
    }
    
    @Override
    public String getDefaultVoice() throws Exception {
        String res = null;
        
        if (ln == null) {
            throw new Exception("TTS LANG NOT SET");
        }
        
        switch (ln) {
            case PT:
                res = "Vicente";
                break;
            default:
                ;
        }
        
        return res;
    }
    
    @Override
    public void setLang(lang ln) {
        this.ln = ln;
    }
    
    @Override
    public Agent.lang getLang() {
        return ln;
    }

    @Override
    public List<TTSResult> getAudio(String voice, String sentence, String fnameprefx, AUDIOFORMAT af, float srate, byte[] prerecspeech, boolean usecache) throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
//
//
//    public void mergedEvent(DixiEvent event) {
//        if (event.getID() == DixiEvent.EVENT_RESULT) {
//            DixiResultEvent dre = (DixiResultEvent) event;
//            System.out.println(dre);
//        }
//    }
//    
//    public String getWav(String voice, String sentence, String fnameprefx) {
//        //clean
//        currevt = null;
////        jDixi.dixi_deallocate();
//
//        while (currevt == null) {      //|| !tts.getSentence().equals(question)
//            if (!say(voice, sentence)) {
//                return null;
//            };
//
//            //wait for event
//            for (int i = 0; currevt == null && i < 3000; i++) {
//                try {
//                    Thread.sleep(1);     //1s = 1000
//                } catch (InterruptedException ex) {
//                }
//            }
//        }
//
//        return fnameprefx + getWav().getName();
//    }

