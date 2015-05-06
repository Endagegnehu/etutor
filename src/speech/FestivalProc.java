/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package speech;

import agent.Agent;
import agent.Agent.lang;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import utils.Common;
import utils.NLP;

/**
 *
 * @author pfialho
 */
public class FestivalProc implements TTS {

    private Process process;
    private OutputStream output;
    private final File dumpDir;
    private final String festexec;
    private final String oggenc;
    private final String oggencopt;
    private final String mp3enc;
    private final String mp3encopt;
    private final boolean remdmarks;
    private Agent.lang ln = null;

    public FestivalProc(String festexec, String dump, String oggenc, String oggencopt, String mp3enc, String mp3encopt) {
        this.festexec = festexec;
        this.dumpDir = new File(dump);
        this.oggenc = new File(oggenc).getPath();
        this.oggencopt = oggencopt;
        this.mp3enc = new File(mp3enc).getPath();
        this.mp3encopt = mp3encopt;
        remdmarks = false;
    }

    public FestivalProc(String festexec, String dump, String oggenc, String oggencopt, String mp3enc, String mp3encopt, boolean remdmarks) {
        this.festexec = festexec;
        this.dumpDir = new File(dump);
        this.oggenc = new File(oggenc).getPath();
        this.oggencopt = oggencopt;
        this.mp3enc = new File(mp3enc).getPath();
        this.mp3encopt = mp3encopt;
        this.remdmarks = remdmarks;
    }

    @Override
    public void init() throws Exception {
        process = Runtime.getRuntime().exec(festexec);
        output = process.getOutputStream();
    }

    @Override
    public synchronized List<TTSResult> getAudio(String voice, String sen, String fnameprefx, AUDIOFORMAT af, float srate) throws Exception {
        ArrayList<TTSResult> audres = new ArrayList<>();

        String[] splits = sen.split(TTS.strSplitRegex);
        for (String sentence : splits) {
            String atosynth = sentence.toLowerCase().trim();
//            System.err.println("atosynth: " + atosynth);
            while (!atosynth.isEmpty() && Character.toString(atosynth.charAt(atosynth.length() - 1)).matches("\\p{Punct}")) {
                atosynth = atosynth.substring(0, atosynth.length() - 1);
            }

            if (atosynth.trim().isEmpty()) {
                continue;
            }
            
            switch (ln) {
                case ES:
                    atosynth = atosynth.replaceAll("\u00BF", " ");
                    break;
                case EN:
                    atosynth = NLP.normDMarks(atosynth);
                    break;
                default:
                    ;
            }

            atosynth = atosynth.replaceAll("\u2019", "'").replaceAll("\\s+", " ").trim();
            
            String id = Common.getUniqueId(atosynth) + "_" + af;

            File voicedir = new File(dumpDir, voice);
            voicedir.mkdir();
            File resser = new File(voicedir, id);
            TTSResult res = null;

            if (resser.exists() && new File(resser.getAbsolutePath() + "." + af.name().toLowerCase()).exists()) {
                //get serialized TTSResult
                System.out.println("sentence \"" + atosynth + "\" already synthesized in " + resser.getAbsolutePath());
                FileInputStream f_in = new FileInputStream(resser);
                ObjectInputStream obj_in = new ObjectInputStream(f_in);
                res = (TTSResult) obj_in.readObject();

                res.setSentence(URLEncoder.encode(sentence, "UTF-8"));      //avoid mistyping
                audres.add(res);
                continue;
            }

            //clean
            for (File f : voicedir.listFiles()) {
                if (f.getName().startsWith(id)) {
                    try {
                        Files.delete(f.toPath());
                    } catch (Exception ex) {
                        System.err.println("--unable to delete previous/erroneous cache files for id " + id + ": " + ex.getMessage());
                        return null;
                    }
                }
            }

            //fresh start
            System.out.println("-synth started: " + atosynth);

            resser.createNewFile();
            String wavpath = resser.getAbsolutePath() + ".wav";
            String phontpath = resser.getAbsolutePath() + ".txt";

            if (remdmarks) {
                atosynth = NLP.normDMarks(atosynth);
            }

            output.write(("(voice_" + voice + ")\n").getBytes());      //JuntaDeAndalucia_es_pa_diphone

            //exceptions
            if (voice.equalsIgnoreCase("JuntaDeAndalucia_es_pa_diphone")) {
                output.write(("(Parameter.set 'Duration_Stretch 1.1)").getBytes());
            }

            output.write(("(set! utt1 (utt.synth (Utterance Text \"" + atosynth + "\")))\n").getBytes());
//        output.write("(utt.synth utt1)\n".getBytes());
            output.write(("(utt.save.segs utt1 \"" + phontpath.replaceAll("\\\\", "/") + "\")\n").getBytes());
            output.write(("(utt.save.wave utt1 \"" + wavpath.replaceAll("\\\\", "/") + "\")\n").getBytes());

            output.flush();

            for (int i = 0; i < TTS.waitcycle; i++) {
                if (new File(wavpath).exists() && new File(wavpath).canRead()) {
                    break;
                }

                try {
                    Thread.sleep(TTS.waitcyclemillis);     //1s = 1000
                } catch (InterruptedException ex) {
                }
            }

            if (!new File(wavpath).exists() || !new File(wavpath).canRead()) {
                System.err.println("--festival failed for id: " + id + ". restarting...");

                try {
                    Files.delete(resser.toPath());
                    if (new File(wavpath).exists()) {
                        Files.delete(new File(wavpath).toPath());
                    }
                    if (new File(phontpath).exists()) {
                        Files.delete(new File(phontpath).toPath());
                    }
                } catch (Exception ex) {
                    System.err.println("--temp files for id " + id + " could not be deleted: " + ex.getMessage());
                }

                process.destroy();
                //Runtime.getRuntime().exec("taskkill /F /IM <processname>.exe");
                init();

                try {
                    Thread.sleep(3000);     //1s = 1000
                } catch (InterruptedException ex) {
                }

                return null;
            }

            System.out.println("-synth ended: " + atosynth);

            String audiofpath = fnameprefx + voice + "/";
            switch (af) {
                case MP3:
                    String mp3path = resser.getAbsolutePath() + ".mp3";
                    Runtime.getRuntime().exec(mp3enc + " " + mp3encopt + " " + wavpath + " " + mp3path).waitFor();
                    new File(wavpath).delete();
                    audiofpath += new File(mp3path).getName();
                    break;
                case OGG:
                    String oggpath = resser.getAbsolutePath() + ".ogg";
                    Runtime.getRuntime().exec(oggenc + " " + oggencopt + " " + wavpath + " " + oggpath).waitFor();
                    new File(wavpath).delete();
                    audiofpath += new File(oggpath).getName();
                    break;
                default:
                    ;
            }

            String[] phones = getPhones(phontpath);
            float[] phonest = getPhonesEndTimes(phontpath);

            new File(phontpath).delete();

//        String tosend = JSON.toJSONString(new TTSResult(URLEncoder.encode(sentence, "UTF-8"), audiofpath, phones, phonest));
            TTSResult tosend = new TTSResult(URLEncoder.encode(sentence, "UTF-8"), audiofpath, phones, phonest);

            //serialize json string of TTSResult
            System.out.println("-saving \"" + atosynth + "\" as: " + resser.getAbsolutePath());
            ObjectOutputStream obj_out = new ObjectOutputStream(new FileOutputStream(resser));
            obj_out.writeObject(tosend);

            audres.add(tosend);
        }

        return audres;
    }

    private static String[] getPhones(String segspath) throws FileNotFoundException {
        ArrayList<String> res = new ArrayList<String>();

        Scanner scanner = new Scanner(new File(segspath));
        try {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("#")) {
                    continue;
                }

                String[] tks = line.split("\\s");
                res.add(tks[2]);
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }

        return res.toArray(new String[]{});
    }

    private static float[] getPhonesEndTimes(String segspath) throws FileNotFoundException {
        ArrayList<Float> res = new ArrayList<Float>();

        Scanner scanner = new Scanner(new File(segspath));
        try {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("#")) {
                    continue;
                }

                String[] tks = line.split("\\s");
                res.add(Float.parseFloat(tks[0]));
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }

        float[] res2 = new float[res.size()];
        for (int i = 0; i < res.size(); i++) {
            Float f = res.get(i);
            res2[i] = (f != null ? f : Float.NaN); // Or whatever default you want.
        }

        return res2;
    }

    //TODO!!!!!!!!!!
    @Override
    public boolean voiceAvailable(String voice) {
        return true;
    }

    @Override
    public String getDefaultVoice() throws Exception {
        String res = null;

        if (ln == null) {
            throw new Exception("TTS LANG NOT SET");
        }

        switch (ln) {
            case EN:
                res = "roger_hts2010";
                break;
            case ES:
                res = "uvigo3_hts2010";
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
}
