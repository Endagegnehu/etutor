/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package speech;

/**
 *
 * @author pedrofialho
 */
public class ASRresult {
    private String recog;
    private float conf;

    public ASRresult(String recog, float conf) {
        this.recog = recog;
        this.conf = conf;
    }

    public String getRecog() {
        return recog;
    }

    public void setRecog(String recog) {
        this.recog = recog;
    }

    public void setConf(float conf) {
        this.conf = conf;
    }

    public float getConf() {
        return conf;
    }
    
    
}
