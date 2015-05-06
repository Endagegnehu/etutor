/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package agent;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pfialho
 */
public class LogFile implements Comparable<LogFile> {
    public String id = null;
    public String audioPath = null;
    public String resultsPath = null;
    
    //for TRS only
    public ArrayList<Float> segments = null;
    public HashMap<Map.Entry<Float,Float>, Map.Entry<String,String>> trsTimeTrans = null;
    
    private final DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'_'HH'h'mm'm'ss's'");

//    public Log(String id, String audioPath, String resultsPath) {
//        this.id = id;
//        this.audioPath = audioPath;
//        this.resultsPath = resultsPath;
//    }

    @Override
    public int compareTo(LogFile t) {
        Date date = null; 
        Date date2 = null;
        try {
            date = inputFormat.parse(this.id);
            date2 = inputFormat.parse(t.id);
        } catch (ParseException ex) {
            System.err.println(ex.getMessage());
        }
        return date.compareTo(date2);        
    }
}