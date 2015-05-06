package utils;

import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NLP {

    /**
     * contains: -remove all '!"#$%&'()*+,-./:;<=>?
     *
     * @[\]^_`{|}~' = \p{Punct}+ -lowercase -trim
     *
     * @param words: single word or sentence
     * @return single word or sentence normalized
     */
    public static String normPunctLCase(String words) {
        return words.replaceAll("\\p{Punct}+", "").toLowerCase().trim();
    }

    /**
     * contains: -remove all !"#$%&'()*+,-./:;<=>?
     *
     * @[\]^_`{|}~ = \p{Punct}+ -lowercase -trim -remove all diacritical marks
     * (´`~^, etc)
     *
     * @param words
     * @return
     */
    public static String normPunctLCaseDMarks(String words) {
        return Normalizer.normalize(NLP.normPunctLCase(words), Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * remove all diacritical marks (´`~^, etc)
     *
     * @param words
     * @return
     */
    public static String normDMarks(String words) {
        String w2 = words;
        try {
            w2 = new String(w2.getBytes("ISO-8859-1"));
        } catch (UnsupportedEncodingException ex) {
            w2 = words;
        }
        return Normalizer.normalize(w2, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
