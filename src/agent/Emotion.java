/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package agent;

/**
 *
 * @author pfialho
 */
public class Emotion {
    private String text;
    private String emotion;
    private float duration;

    public Emotion(String text, String emotion, float duration) {
        this.text = text;
        this.duration = duration;
        this.emotion = emotion;
    }
  
    public String getEmotion() {
        return emotion;
    }

    public float getDuration() {
        return duration;
    }

    public String getText() {
        return text;
    }
    
    @Override
    public String toString(){
        return "|text="+text+";emotion="+emotion+";duration="+duration+"|";        
    }
}
