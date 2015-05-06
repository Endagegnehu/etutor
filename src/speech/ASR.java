/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package speech;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

/**
 *
 * @author pfialho
 */
public interface ASR {
    public float waitcycle = 300;        //500
    public long waitcyclemillis = 10;      //3
    public AudioFormat defaultRecConf = new AudioFormat(16000, 16, 1, true, false);
    public float minSegLenSamples = 3000;    //to be: defaultRecConf.getSampleRate() / 4;              //as in client
    public float frameSizeSegs = 0.01f;
    
    public void init() throws Exception;
    public String setTask(String grxml) throws Exception;
    public String addTask(String grxml, String modelsDir, float weight) throws Exception;
    public void loadTask(String grxml, String modelsDir, String tname) throws Exception;
    public ASRresult recognizeBytes(byte[] sound) throws Exception;
    public void setInputStream(AudioInputStream stream) throws Exception;
    
    public void setID(String asrid);
    public String getID();
}
