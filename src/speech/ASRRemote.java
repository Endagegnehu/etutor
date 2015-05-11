/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package speech;

import agent.Agent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.sound.sampled.AudioInputStream;

/**
 *
 * @author pedrofialho
 */
public class ASRRemote implements ASR {

    private final String url;
    private Agent.lang ln;
    private String asrID = "asrID";

    public ASRRemote(String url, Agent.lang ln) {
        this.url = url;
        this.ln = ln;
    }

    @Override
    public void init() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String setTask(String grxml) throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String addTask(String grxml, String modelsDir, float weight) throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void loadTask(String grxml, String modelsDir, String tname) throws Exception{
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ASRresult recognizeBytes(byte[] sound) throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
        //<editor-fold defaultstate="collapsed" desc="comment">
        //        String sndToSend = "[";
        //        for (int i = 0; i < sound.length; i++) {
        //            sndToSend += sound[i];
        //
        //            if (i < sound.length - 1) {
        //                sndToSend += ",";
        //            }
        //        }
        //        sndToSend += "]";
        //
        //        URL url2 = new URL(this.url);
        //        String recvJSON = "";
        //
        //        HttpURLConnection connection = (HttpURLConnection) url2.openConnection();
        //        connection.setDoOutput(true);
        //        connection.setConnectTimeout(30 * 1000);
        //        connection.setReadTimeout(30 * 1000);
        //        try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), "UTF-8")) {
        //            out.write("task=echo&lang=" + this.ln.name() + "&sbytes=" + sndToSend);
        //        }
        //
        //        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        //            return null;
        //        }
        //
        //
        //        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
        //            String decodedString;
        //            while ((decodedString = in.readLine()) != null) {
        //                recvJSON += decodedString;
        //            }
        //        }
        //
        //        return recvJSON;
        //</editor-fold>
    }

    @Override
    public void setInputStream(AudioInputStream stream) throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setID(String asrid) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getID() {
        return this.url;
    }

    @Override
    public int start() throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
