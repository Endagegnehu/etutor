/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package speech;

import agent.Agent;
import agent.Agent.lang;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pedrofialho
 */
public class TTSRemote implements TTS {

    private final String url;
    private lang ln;

    public TTSRemote(String url, Agent.lang ln) {
        this.url = url;
        this.ln = ln;
    }

    @Override
    public void init() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getDefaultVoice() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean voiceAvailable(String voice) {
        return true;                                    // for now...
    }

    @Override
    public List<TTSResult> getAudio(String voice, String sentence, String fnameprefx, AUDIOFORMAT af, float srate) throws Exception {
        ArrayList<TTSResult> audres = new ArrayList<>();

        URL url2 = new URL(this.url);
        String recvJSON = "";

        HttpURLConnection connection = (HttpURLConnection) url2.openConnection();
        connection.setDoOutput(true);
        connection.setConnectTimeout(30 * 1000);
        connection.setReadTimeout(30 * 1000);
        try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), "UTF-8")) {
            out.write("task=echo&lang=" + this.ln.name()
                    + "&q=" + sentence
                    + (voice == null ? "" : "&v=" + voice)
                    + "&platform=" + af.name()
                    + "&srate=" + srate);
        }

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            return null;
        }


        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String decodedString;
            while ((decodedString = in.readLine()) != null) {
                recvJSON += decodedString;
            }
        }

        audres.add(new TTSResult(recvJSON, "", new String[]{}, new float[]{}));
        return audres;
    }

    @Override
    public void setLang(lang ln) {
        this.ln = ln;
    }

    @Override
    public lang getLang() {
        return ln;
    }
}
