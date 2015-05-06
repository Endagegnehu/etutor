package speech;

import inesc.id.l2f.asr.ASRResult;
import inesc.id.l2f.asr.IAudimusEventListener;
import inesc.id.l2f.asr.JAudimus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class Audimus implements IAudimusEventListener, ASR {

    private JAudimus recognizer;
    private ASRResult currevt = null;
    private final String xmlconfig;
    private String modDir;
    private final String asrRepeat;
    private final float minc;
    private final float surec;
    private String asrID = "asrID";
    private byte[] silToAdd = null;
    private final short maxsamplethresh = 31000;
    private final short maxdeltathresh = 30;
    private boolean hasSNS = false;
    private ASRresult fullResult = null;     //for SNS
//    private float globMaxConf = 0;      //for SNS
    //tmp flags        
    private int cnt = 0;
    private final boolean dumpSegs = false;

    public Audimus(String xmlconfig) {
        this.xmlconfig = xmlconfig;
        this.asrRepeat = "_REPEAT_";
        this.minc = 0;
        this.surec = 0;
    }

    public Audimus(String xmlconfig, String asrRepeat) {
        this.xmlconfig = xmlconfig;
        this.asrRepeat = asrRepeat;
        this.minc = 0;
        this.surec = 0;
    }

    public Audimus(String xmlconfig, String asrRepeat, float minc, float surec) {
        this.xmlconfig = xmlconfig;
        this.asrRepeat = asrRepeat;
        this.minc = minc;
        this.surec = surec;
    }
    
    public Audimus(String xmlconfig, String asrRepeat, float minc, float surec, String modDir) {
        this.xmlconfig = xmlconfig;
        this.asrRepeat = asrRepeat;
        this.minc = minc;
        this.surec = surec;
        this.modDir=modDir;
    }

    @Override
    public void init() throws Exception {
        recognizer = new JAudimus();
        int result = recognizer.audimus_setArchitectureFilePath(new File(xmlconfig));
        if (result < 0) {
            throw new Exception("RECOGNIZER SET ARQUITECTURE FILE FAILED [code=" + result + "]");
        }

        result = recognizer.audimus_allocate();
        if (result == -3027) {
            System.out.println("ERROR : " + result);
            throw new Exception("A Licenca do motor de reconhecimento (Audimus) expirou.");
        }
        if (result < 0) {
            throw new Exception("RECOGNIZER ALLOCATION FAILED [code=" + result + "]");
        }

        result = recognizer.audimus_registerRecognitionListener(this);
        if (result < 0) {
            throw new Exception("RECOGNIZER LISTENER REGISTRATION FAILED [code=" + result + "]");
        }

        for (String um : recognizer.audimus_getUserModels()) {
            if (um.toLowerCase().contains("sns")) {
                hasSNS = true;
                break;
            }
        }

        //silence preps
        int silSize = 16000;        //1s
        ByteArrayOutputStream s1 = new ByteArrayOutputStream();
        for (int i = 0; i < silSize; i++) {
            short sil = (short) (0.0001 * Math.random());
            byte[] b = new byte[2];
            b[0] = (byte) (sil & 0xff);
            b[1] = (byte) ((sil >> 8) & 0xff);

            s1.write(b);
        }
        silToAdd = s1.toByteArray();
    }

    @Override
    public synchronized ASRresult recognizeBytes(byte[] audio) throws Exception {
        //<editor-fold defaultstate="collapsed" desc="dump segment pre proc">
        if (dumpSegs) {
            new File("dump\\segs\\").mkdir();
            File out = new File("dump\\segs\\" + new File(cnt + "") + ".wav");
            ByteArrayInputStream bais = new ByteArrayInputStream(audio);
            AudioInputStream ai = new AudioInputStream(bais, ASR.defaultRecConf, audio.length);
            AudioSystem.write(ai, AudioFileFormat.Type.WAVE, out);
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="amplify, if needed. TODO: disregard short boundaries (due to clicks on mobile)">
        boolean needAmpl = true;
        ArrayList<Short> tmp = new ArrayList<>();
        for (int i = 0; i < audio.length;) {
            short low = (short) audio[i];
            i++;
            short high = (short) audio[i];
            i++;

            short sampleVal = (short) ((high << 8) + (low & 0x00ff));
            tmp.add(sampleVal);

            if (sampleVal >= maxsamplethresh) {
                needAmpl = false;
                tmp.clear();
                break;
            }
        }

        if (needAmpl) {
            int maxsample = Math.max(Math.abs(Collections.min(tmp)), Math.abs(Collections.max(tmp)));
            int delta = maxsamplethresh / maxsample;

            if (delta < maxdeltathresh) {       //avoid amplifying silence
//                System.err.println("ampl: " + cnt);
                ByteArrayOutputStream tmpSegs = new ByteArrayOutputStream();
                for (short s : tmp) {
                    short sAmpl = (short) (s * delta);

                    byte[] b = new byte[2];
                    b[0] = (byte) (sAmpl & 0xff);
                    b[1] = (byte) ((sAmpl >> 8) & 0xff);

                    tmpSegs.write(b);
                }

                audio = tmpSegs.toByteArray();
            }
        }
        //</editor-fold>

//        audio = SpeechDSP.cmn(audio);

        //add silence on both ends
        ByteArrayOutputStream s2 = new ByteArrayOutputStream();
        s2.write(silToAdd);     //at the beginning
        s2.write(audio);
        s2.write(silToAdd);     //at the end
        byte[] sound = s2.toByteArray();

        //<editor-fold defaultstate="collapsed" desc="dump segment post proc">
        if (dumpSegs) {
            File out2 = new File("dump\\segs\\" + new File(cnt + "_afterpad") + ".wav");
            ByteArrayInputStream bais2 = new ByteArrayInputStream(sound);
            AudioInputStream ai2 = new AudioInputStream(bais2, ASR.defaultRecConf, sound.length);
            AudioSystem.write(ai2, AudioFileFormat.Type.WAVE, out2);
            cnt++;
        }
        //</editor-fold>

        currevt = null;
        fullResult = new ASRresult("", 0);
        ASRresult res = new ASRresult("", 0);
        int result = 0;

        result = recognizer.audimus_start();
        if (result < 0) {
            throw new Exception("RECOGNIZER START FAILED [code=" + result + "]");
        }

        recognizer.write(sound, 0, sound.length);

        result = recognizer.audimus_pause();
        if (result < 0) {
            throw new Exception("Audimus error while pausing Recognizer (" + result + ")");
        }
        
        //wait for event
        for (int i = 0; i < ASR.waitcycle; i++) {
            if (!hasSNS) {
                if (currevt != null) {
                    break;
                }
            } else {
                if (!fullResult.getRecog().trim().isEmpty()) {
                    break;
                }
            }

            try {
                Thread.sleep(ASR.waitcyclemillis);     //1s = 1000
            } catch (InterruptedException ex) {
            }
        }

        if (!hasSNS) {
            //<editor-fold defaultstate="collapsed" desc="no SNS">
            if (currevt == null) {   //|| (currevt != null && currevt.getCleanText().isEmpty())
                res.setRecog(asrRepeat);
            } else {
                //System.err.println("from task: " + currevt.getTaskName());
                if (minc > 0 && currevt.getConfidence() < minc) {
                    res = new ASRresult("", 0);       //discard
                } else {
                    res = new ASRresult(currevt.getCleanText(), currevt.getConfidence());
                }

                //<editor-fold defaultstate="collapsed" desc="comment">
                //            if (currevt.getConfidence() < 0.7) {
                //                if (this.switchTask(currevt.getTaskName()) == null) {
                //                    System.err.println("task switch failed; using previous task's result.");
                //                    return res;
                //                }
                //
                //                res = this.recognizeBytes(sound);
                //                this.setTask("grxml");
                //            }
                //</editor-fold>
            }
            //</editor-fold>
        } else {
            res = fullResult;
            res.setRecog(res.getRecog().trim());
            if (res.getRecog().isEmpty()) {
                res.setRecog(asrRepeat);
            }

            fullResult = null;
        }

        return res;
    }

    @Override
    public void eventDecoderResult(ASRResult result) {
//        System.err.println("Result1: " + result.getText());
        ////////////////////////////////////////   System.gc();
        if (!result.getCleanText().trim().isEmpty()) {

//            System.err.println("Result: " + result.getText() + " ............ confidence: " + result.getConfidence());

//            for (ASRResult a : result.getNBests()) {
//                System.err.println("\t" + a.getCleanText());
//            }

            if (minc > 0 && surec > 0) {
                float conf = result.getConfidence();
                if (minc < conf && conf < surec) {        //repeat
                    return;
                }
            }

            currevt = result;
            fullResult.setRecog(fullResult.getRecog() + currevt.getCleanText().trim() + " ");
            fullResult.setConf(fullResult.getConf() == 0 ? result.getConfidence() : ((fullResult.getConf() + result.getConfidence()) / 2));
        }
    }

    @Override
    public String getID() {
        return asrID;
    }

    @Override
    public void setID(String asrid) {
        asrID = asrid;
    }

    public void dispose() throws Exception {
        if (recognizer != null) {
            int result = recognizer.audimus_interrupt();
            if (result < 1) {
                System.err.println("ERROR : " + result);
                throw new Exception("Ocorreu um erro com o motor de reconhecimento de fala.");
            }

            result = recognizer.audimus_deallocate();
            if (result < 1) {
                System.err.println("ERROR : " + result);
                throw new Exception("Ocorreu um erro com o motor de reconhecimento de fala.");
            }
            //force free resource
            recognizer.audimus_finalize();
            recognizer = null;
        }
    }

    private String switchTask(String currtask) throws Exception {
        String task = null;
//        for(String tss : recognizer.getActiveTasks()){
//            if(!tss.equalsIgnoreCase(currtask)){
//                task = tss;
//            }
//        }

        if (currtask.contains("grxml")) {
            task = "day-H0";
        } else {
            for (String tss : recognizer.audimus_getTasknames()) {
                if (tss.contains("grxml")) {
                    task = tss;
                }
            }
        }

        return this.setTask(task);
    }

    @Override
    public void loadTask(String grxml, String modelsDir, String tname) throws Exception {
        int result = 0;
//        String tname = new File(grxml).getName();
//        System.out.println("registering task: " + tname);

        if (recognizer.audimus_isTaskRegistered(tname)
                && recognizer.audimus_isTaskActivated(tname)
                && recognizer.audimus_isTaskLoaded(tname)) {
//            System.err.println("task already ready: " + tname);
            return;
        }

//        this.unloadCurrentTasks();

        result = recognizer.audimus_interrupt();
        if (result < 0) {
            throw new Exception("Failed to audimus_interrupt: " + result);
        }

        System.err.println("registering task: " + tname);

        if (!recognizer.audimus_isTaskRegistered(tname)) {
            result = recognizer.audimus_registerTask(tname, grxml, (modelsDir == null ? modDir : modelsDir));
            if (result < 0) {
                throw new Exception("audimus_registerTask FAILED [code=" + result + "]");
//            throw new Exception("CODE : " + result);
            }
        }

        if (!recognizer.audimus_isTaskLoaded(tname)) {
            result = recognizer.audimus_loadTask(tname);
            if (result < 0) {
                throw new Exception("audimus_loadTask FAILED [code=" + result + "]");
//            throw new Exception("CODE : " + result);
            }
        }

        result = recognizer.audimus_activateTask(tname);
        if (result < 0) {
            throw new Exception("audimus_activateTask FAILED [code=" + result + "]");
//            throw new Exception("CODE : " + result);
        }

        if (!recognizer.audimus_isTaskRegistered(tname)) {
            throw new Exception("!audimus_isTaskRegistered");
        }
        if (!recognizer.audimus_isTaskLoaded(tname)) {
            throw new Exception("!audimus_isTaskLoaded");
        }
        if (!recognizer.audimus_isTaskActivated(tname)) {
            throw new Exception("!audimus_isTaskActivated " + tname);
        }
        
        System.err.println("active tasks: " + Arrays.toString(recognizer.getActiveTasks().toArray()));
    }

    @Override
    public String setTask(String tname) throws Exception {
        String task = null;
        for (String tss : recognizer.audimus_getTasknames()) {
            if (tss.trim().equalsIgnoreCase(tname.trim())
                    && recognizer.audimus_isTaskRegistered(tss)
                    && recognizer.audimus_isTaskLoaded(tss)) {
                task = tss;
            }
        }

        if (task == null) {
            throw new Exception("static task " + tname + " not found/registered/loaded.");
        }

        int result = 0;
        result = recognizer.audimus_activateTask(task);
        if (result < 0) {
            throw new Exception("audimus_activateTask " + task + " FAILED [code=" + result + "]");
        }

        if (!recognizer.audimus_isTaskActivated(task)) {
            throw new Exception("!audimus_isTaskActivated: " + tname);
        }

        System.err.println("active tasks: " + Arrays.toString(recognizer.getActiveTasks().toArray()));
        return task;
    }

    /**
     * *
     * Adds a language model (task) with weight. Remaining weight (assuming
     * max=1) is equally distributed by existing tasks.
     *
     * @param grxml
     * @param modelsDir
     * @param weight
     * @return new task's name
     * @throws Exception
     */
    @Override
    public String addTask(String grxml, String modelsDir, float weight) throws Exception {
        String tname = new File(grxml).getName();
        if (recognizer.getActiveTasks().contains(tname)) {
            System.err.println("task: " + tname + " :already activated; use setTask() instead.");
            return null;
        }

        int result = 0;

        //setup existing tasks
        int ntasks = recognizer.getActiveTasks().size();
        float[] weights = new float[ntasks + 1];
        for (int i = 0; i < weights.length - 1; i++) {
            weights[i] = (1 - weight) / ntasks;      //same weight for all existing tasks!
        }

        HashSet<String> tss = new HashSet<String>();
        for (String ts : recognizer.getActiveTasks()) {
            tss.add(ts);
        }

        //setup new task
        System.out.println("registering task: " + tname);

        result = recognizer.audimus_registerTask(tname, grxml, modelsDir);
        if (result < 0) {
            throw new Exception("audimus_registerTask FAILED [code=" + result + "]");
        }
        result = recognizer.audimus_loadTask(tname);
        if (result < 0) {
            throw new Exception("audimus_loadTask FAILED [code=" + result + "]");
        }


        if (!recognizer.audimus_isTaskRegistered(tname)) {
            throw new Exception("!audimus_isTaskRegistered: " + tname);
        }
        if (!recognizer.audimus_isTaskLoaded(tname)) {
            throw new Exception("!audimus_isTaskLoaded: " + tname);
        }

        tss.add(tname);
        weights[weights.length - 1] = weight;

        //weight = 0 ignores weight setting
        if (weight > 0) {
            result = recognizer.audimus_activateTasks(new ArrayList<String>(tss), weights);
        } else {
            result = recognizer.audimus_activateTasks(new ArrayList<String>(tss));
        }

        if (result < 0) {
            throw new Exception("audimus_activateTask FAILED [code=" + result + "]");
        }

        System.err.println("active tasks: " + Arrays.toString(recognizer.getActiveTasks().toArray()));
        return tname;
    }

    @Override
    public void setInputStream(AudioInputStream stream) throws Exception {
        recognizer.recognize(stream, ASR.defaultRecConf, true);
        int result = 0;

        result = recognizer.audimus_start();
        if (result < 0) {
            System.out.println("RECOGNIZER START FAILED [code=" + result + "]");
            throw new Exception("CODE : " + result);
        }
    }

    @Override
    public void eventEndpointEOS(int streamTime, float confidence) {
    }

    @Override
    public void eventEndpointSOS(int streamTime, float confidence) {
    }

    @Override
    public void eventEndpointEnergy(float energy) {
    }

    @Override
    public void eventDTWFound() {
    }

    @Override
    public void eventDecoderHypothesis(ASRResult result) {
//        System.err.println();
    }

    @Override
    public void eventAverager(int sentenceId, int typeId, int classId, float confidence) {
    }

    @Override
    public void eventJingleEOS(int streamTime) {
    }

    @Override
    public void eventJingleSOS(int streamTime) {
    }

    @Override
    public void eventNNSEpochTime(int type, float time) {
    }

    @Override
    public void eventAngle(float angle) {
    }

    @Override
    public void eventStory(String story) {
    }

    @Override
    public void eventAED(int windowNumber, int localMinWindow, int i2, float f, float localMin, float hitTime, String referenceId, String referenceDesc) {
    }
}
//<editor-fold defaultstate="collapsed" desc="comment">
//    private void recognize(final File audioFile) throws Exception {
//
//        int result = recognizer.audimus_start();
//        if (result < 0) {
//            System.out.println("RECOGNIZER START FAILED [code=" + result + "]");
//            throw new Exception("CODE : " + result);
//        }
//
//        if (1 == 0) {
//            InputStream inputStream;
//            if (audioFile.getName().endsWith("raw")) {
//                inputStream = new FileInputStream(audioFile);
//            } else {
//                final AudioInputStream ais = AudioSystem.getAudioInputStream(new FileInputStream(audioFile));
//
//                System.out.println("Open audio file with format: "
//                        + ais.getFormat().toString());
//
//                inputStream = ais;
//            }
//
//            final byte[] buff = new byte[320]; //frame size
//            int cnt = 0;
//            while ((cnt = inputStream.read(buff)) != -1) {
//                recognizer.write(buff, 0, cnt);
//
//                //Thread.currentThread().sleep(1);
//
//            }
//
//            System.out.println("Done writing... will wait for finish");
//            result = recognizer.audimus_pause();
//            if (result < 0) {
//                System.out.println("Audimus error while pausing Recognizer (" + result
//                        + ")");
//                return;
//            }
//        } else {
//            recognizer.recognize(audioFile, new AudioFormat(16000, 16, 1, true, false));
//        }
//    }
//
//
//
//
//    public static void main(String[] args) {
//        try {
//
//            Audimus recognizefile = new Audimus("resources\\audimus\\other\\asr_mic_pt-star.java.xml");  //recognize16k
//            recognizefile.init();
//            recognizefile.recognizeFile("E:\\test1.wav");
//            recognizefile.dispose();
//            System.exit(1);
//
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//
//    public void recognizeRaw(String audioFile) throws Exception {
//        int result = recognizer.audimus_start();
//        if (result < 0) {
//            System.out.println("RECOGNIZER START FAILED [code=" + result + "]");
//            throw new Exception("CODE : " + result);
//        }
//
//        InputStream inputStream = new FileInputStream(new File(audioFile));
////inputStream.re
////        AudioInputStream ais = AudioSystem.getAudioInputStream(new FileInputStream(new File(audioFile)));
//
////        byte[] buff = Files.readAllBytes(FileSystems.getDefault().getPath("logs", audioFile)); //frame size
//        byte[] buff = new byte[320];
//        int cnt = 0;
//        while ((cnt = inputStream.read(buff)) != -1) {
//            recognizer.write(buff, 0, cnt);
//        }
//
//        result = recognizer.audimus_pause();
//        if (result < 0) {
//            System.out.println("Audimus error while pausing Recognizer (" + result
//                    + ")");
//            return;
//
//        }
//
////        recognizer.recognize(new File(audioFile), ais.getFormat());
//    }
//
//    public void recognizeWav(String audioFile) throws Exception {
//        int result = recognizer.audimus_start();
//        if (result < 0) {
//            System.out.println("RECOGNIZER START FAILED [code=" + result + "]");
//            throw new Exception("CODE : " + result);
//        }
//
//        recognizer.audimus_recognizeFile(new File(audioFile));
//    }
//
//    public void recognizeOgg(String audioFile) throws Exception {
//        String now = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
//
//        int result = recognizer.audimus_start();
//        if (result < 0) {
//            System.out.println("RECOGNIZER START FAILED [code=" + result + "]");
//            throw new Exception("CODE : " + result);
//        }
//
//        File f2 = new File(asrRepeat, now + ".wav");
//        try {
//            java.lang.Runtime.getRuntime().exec(".\\bin\\oggdec.exe " + audioFile + " -w " + f2.getPath()).waitFor();
//        } catch (Exception ex) {
//            System.err.println(ex.getMessage());
////            return null;
//        }
//
//        recognizer.audimus_recognizeFile(f2);
//    }
//</editor-fold>
