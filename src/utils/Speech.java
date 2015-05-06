/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import speech.ASR;

/**
 *
 * @author pedrofialho
 */
public class Speech {

    public static ArrayList<ArrayList<Short>> getAllFrames(byte[] audio, int windowSizeInSamples) {
        ArrayList<ArrayList<Short>> allFrames = new ArrayList<>();

        ByteBuffer bb = ByteBuffer.wrap(audio);
        bb.order((ASR.defaultRecConf.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN));     //ByteOrder.LITTLE_ENDIAN

        
        while (bb.hasRemaining()) {
            ArrayList<Short> oneFrame = new ArrayList<>();
            
            for (int kk = 0; kk < windowSizeInSamples; kk++) {
                if (!bb.hasRemaining()) {
                    break;
                }

                short x = bb.getShort();
                oneFrame.add(x);
            }

            allFrames.add(oneFrame);

            if (!bb.hasRemaining()) {
                break;
            }
        }

        return allFrames;
    }

    public static byte[] cmn(byte[] audio) throws Exception {
        byte[] res2 = null;
        int audimusFrameSizeInShorts = (int) (ASR.defaultRecConf.getSampleRate() * ASR.frameSizeSegs);        //160

        ArrayList<ArrayList<Short>> allFrames = getAllFrames(audio, audimusFrameSizeInShorts);

        //hamming windowing
        ArrayList<Double> hammingSig = new ArrayList<>();
        for (ArrayList<Short> aframe : allFrames) {
            for (int i = 0; i < aframe.size(); i++) {
                hammingSig.add((aframe.get(i) * (0.54 - 0.46 * Math.cos((2 * Math.PI * i) / (aframe.size() - 1)))));
            }
        }

        int fftsize = 1024;     //hammingSig.size()-1
        double[] hammSigInDoublesForFFT = new double[fftsize];
        for (int f = 0; f < fftsize; f++) {
            hammSigInDoublesForFFT[f] = hammingSig.get(f);
        }

        double[] bak = Arrays.copyOf(hammSigInDoublesForFFT, fftsize);
        
//        new DoubleFFT_1D(1024).realForward(hammSigInDoublesForFFT);

        //<editor-fold defaultstate="collapsed" desc="cmn - commented">
//            double[] oneFrameInDoubles = new double[oneFrame.size()];
//            for (int f = 0; f < oneFrame.size(); f++) {
//                oneFrameInDoubles[f] = oneFrame.get(f);
//            }

        //process oneFrame
//            MFCC abc = new MFCC();
//            double[] resa = abc.extractFeature(oneFrameInDoubles, 16000, null);

        //CMN - pos amplification (if any)
        //via sphinx4
        //        String uttid = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
        //        Utterance utt = new Utterance(uttid, af);
        //        utt.add(audio);
        //
        //        BatchCMN bcmn = new BatchCMN();
        //        bcmn.

        //get samples
        //        ArrayList<Short> amplAudio = new ArrayList<>();
        //        for (int i = 0; i < audio.length;) {
        //            short low = (short) audio[i];
        //            i++;
        //            short high = (short) audio[i];
        //            i++;
        //
        //            short sampleVal = (short) ((high << 8) + (low & 0x00ff));
        //            amplAudio.add(sampleVal);
        //        }
        //
        //
        //        ByteArrayOutputStream tmpSegs2 = new ByteArrayOutputStream();
        //        for (short s : amplAudio) {
        //            short scmn = s;
        //
        //            //analyse samples
        //
        //
        //            //write bytes
        //            byte[] b = new byte[2];
        //            b[0] = (byte) (scmn & 0xff);
        //            b[1] = (byte) ((scmn >> 8) & 0xff);
        //
        //            tmpSegs2.write(b);
        //        }
        //</editor-fold>

        return res2;
    }
    
   
}
