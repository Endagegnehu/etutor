/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package speech;

import java.io.Serializable;

/**
 *
 * @author pfialho
 */
public class TTSResult implements Serializable{

    private String url;
    private String sentence;
    private String[] phones;
    private float[] times;
    
    static final long serialVersionUID = 5568484686017953146L;      //5568484686017953146

    public TTSResult(String sentence, String url, String[] phones, float[] times) {
        this.url = url;
        this.phones = phones;
        this.times = times;
        this.sentence = sentence;
    }
    
    public void setSentence(String s) {
        sentence = s;
    }
    
    public void setURL(String s) {
        url = s;
    }

    public String getSentence() {
        return sentence;
    }

    public String[] getPhones() {
        return phones;
    }

    public float[] getTimes() {
        return times;
    }

    public String getUrl() {
        return url;
    }
}
