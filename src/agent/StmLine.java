/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package agent;

/**
 *
 * @author pedrofialho
 */
public class StmLine {
//    public float sini = -1f;
//    public float sfim = -1f;
    
    public String sini = null;
    public String sfim = null;
    public String cleanTM = null;
    public String fullTM = null;
    public String nonStdSpkr = null;
    public String lang = null;

    @Override
    public String toString() {
        return cleanTM;
    }
   
    
}
