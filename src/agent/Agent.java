/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package agent;

import java.io.IOException;
import speech.ASR;
import speech.TTS;

/**
 *
 * @author pfialho
 */
public interface Agent {

    enum lang {

        EN, ES, PT, BR
    };

    public ASR setASR(Agent.lang lang, boolean staticTask) throws Exception;

    public TTS setTTS(Agent.lang langs, boolean preload) throws Exception, IOException;

}
