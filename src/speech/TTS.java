/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package speech;

import agent.Agent;
import java.util.List;

/**
 *
 * @author pfialho
 */
public interface TTS {
    public enum AUDIOFORMAT{MP3, OGG};
    public String strSplitRegex = "(?<=\\.)|(?<=\\?)|(?<=\\!)|(?<=\\:)|(?<=\\;)";        //(?<=\\.)|(?<=\\?)|(?<=\\!)|(?<=\\;)
    public float waitcycle = 2000;      //2000
    public long waitcyclemillis = 10;
    
    public void init() throws Exception;
    public String getDefaultVoice() throws Exception;
    public boolean voiceAvailable(String voice);
    public List<TTSResult> getAudio(String voice, String sentence, String fnameprefx, AUDIOFORMAT af, float srate) throws Exception;
    
    public void setLang(Agent.lang ln);
    public Agent.lang getLang();
}
