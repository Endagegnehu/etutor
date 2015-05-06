/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package connection.http;

import agent.Agent;
import agent.Emotion;
import apps.Kiosk;
import com.alibaba.fastjson.JSON;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import speech.ASR;
import speech.ASRresult;
import speech.TTS;
import speech.TTSRemote;
import speech.TTSResult;
import utils.Common;

/**
 *
 * @author pfialho
 */
public class RequestServlet extends HttpServlet {

    private String fnameprefx;
    private int fileport;
    private Kiosk agent;
    private static final int DEFAULT_BUFFER_SIZE = 10240; // 10KB.
    private static final TTS.AUDIOFORMAT DEFAULT_TTS_AUDIOFORMAT = TTS.AUDIOFORMAT.OGG;

    @Override
    public void init() throws ServletException {
        fileport = (Integer) getServletContext().getAttribute("fileport");
        fnameprefx = (String) getServletContext().getAttribute("fnameprefx");
        if (fnameprefx.isEmpty()) {
            fnameprefx = null;
        }

        agent = (Kiosk) getServletContext().getAttribute("agent");
        if (agent == null) {
            throw new UnavailableException("Couldn't get agent.");
        }
    }

    @Override
    public void destroy() {
        fnameprefx = null;
    }

    /**
     * Returns the text value of the given part.
     */
    private String getValue(Part part) throws IOException {
        if (part == null) {
            return null;
        }

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(part.getInputStream(), "UTF-8"));
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[DEFAULT_BUFFER_SIZE];
        for (int length = 0; (length = reader.read(buffer)) > 0;) {
            value.append(buffer, 0, length);
        }
        return value.toString();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //<editor-fold defaultstate="collapsed" desc="implicit requests">
        if (request.getRequestURI().contains("favicon")) {
            return;
        }

        if (request.getRequestURI().contains("crossdomain")) {
            response.setContentType("text/xml");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().print("<?xml version='1.0'?><cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>");
            return;
        }
        //</editor-fold>

        String now = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
        String beginstr = "BEGIN: " + now + " ........... from: " + request.getRemoteAddr() + " | " + request.getSession().getId();
        String endstr = "END: " + now;
        System.out.println("\r\n" + beginstr);

        //        //<editor-fold defaultstate="collapsed" desc="comment">
//        if (request.getContentType().trim().contains("multipart/form-data")) {
//            System.out.println("--headers");
//            Enumeration headerNames = request.getHeaderNames();
//            while (headerNames.hasMoreElements()) {
//                String headerName = (String) headerNames.nextElement();
//                System.out.println(headerName + ": " + request.getHeader(headerName));
//            }
//
//            System.out.println("--contents");
//            BufferedReader reader = new BufferedReader(new InputStreamReader(
//                    request.getInputStream()));
//            StringBuilder sb = new StringBuilder();
//            for (String line; (line = reader.readLine()) != null;) {
//                if (line.contains("sbytes")) {
//                    System.out.println(line);
//                    break;
//                }
//
//                System.out.println(line);
//            }
//        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="handle non natural language inputs (ie: opinion, q, sbytes)">
        boolean isMultipart = request.getContentType().trim().contains("multipart/form-data");
        String task, voice, backgid, strLang, strPlatform, strSpeechrate, grxml;
        float speechrate = 0;

        voice = backgid = strSpeechrate = null;
        if (isMultipart) {      //audio request (only)
            task = getValue(request.getPart("task"));
            strLang = getValue(request.getPart("lang"));
            strPlatform = getValue(request.getPart("platform"));
            strSpeechrate = getValue(request.getPart("srate"));
            grxml = getValue(request.getPart("grxml"));
        } else {        //text or backg img request or deprecated audio request (such as in opinions)
            task = request.getParameter("task");

            if ((backgid = request.getParameter("getbackg")) != null) {
                //<editor-fold defaultstate="collapsed" desc="getbackg">
                //            response.setContentType("image/jpeg");      //TODO: use getServletContext().getMimeType

                File img;
                if (backgid.equalsIgnoreCase("monserrate")) {
                    img = new File("dist2/resources/monserrate.jpg");
                } else {
                    if (backgid.equalsIgnoreCase("monserratewall")) {
                        img = new File("dist2/resources/monserratewallframe.png");
                    } else {
                        if (backgid.equalsIgnoreCase("vithea")) {
                            img = new File("dist2/resources/vithea.jpg");
                        } else {
                            if (backgid.equalsIgnoreCase("android")) {
                                img = new File("dist2/resources/android.jpg");
                            } else {
                                img = new File("dist2/resources/backg.jpg");
                            }
                        }
                    }
                }

                response.setContentLength((int) img.length());
                OutputStream out;
                FileInputStream in = new FileInputStream(img);
                try {
                    out = response.getOutputStream();
                    byte[] buf = new byte[1024];
                    int count = 0;
                    while ((count = in.read(buf)) >= 0) {
                        out.write(buf, 0, count);
                    }
                } finally {
                    if (in != null) {
                        in.close();
                    }
                }
                out.close();

                System.out.println(endstr);
                return;
                //</editor-fold>
            }

            strLang = request.getParameter("lang");
            grxml = request.getParameter("grxml");
            voice = request.getParameter("v");
            strPlatform = request.getParameter("platform");
            strSpeechrate = request.getParameter("srate");
        }

        if (task == null) {
            System.err.println("missing task");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "missing task");

            System.out.println(endstr);
            return;
        }

        if (strLang == null) {
            strLang = "PT";
        }

        if (strSpeechrate != null) {
            try {
                speechrate = Float.parseFloat(strSpeechrate);
            } catch (Exception iOException) {
                speechrate = 0;
            }
        } else {
            speechrate = 0;
        }

        if (voice != null && voice.endsWith("_slow")) {
            voice = voice.replaceAll("_slow", "");
            speechrate = (float) -7.0;
        }

        System.err.println(now + " request(t,l,v,p,b,r) = " + task + ", " + strLang + ", " + voice + ", " + strPlatform + ", " + backgid + ", " + speechrate);
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="parse lang and check correspondent resource availability">
        //set defaults - TODO: check availability!!!
        Agent.lang reqLang = null;
        if (strLang != null) {
            try {
                reqLang = Agent.lang.valueOf(strLang.toUpperCase().trim());
            } catch (IllegalArgumentException ex) {
                System.err.println("lang " + reqLang + " not defined");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "lang " + reqLang + " not defined");

                System.out.println(endstr);
                return;
            }
        }

        if (agent.tts != null && !agent.tts.containsKey(reqLang)) {
            System.err.println("no tts defined for lang " + reqLang);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "no tts defined for lang " + reqLang);

            System.out.println(endstr);
            return;
        }

        if (agent.asr != null && !agent.asr.containsKey(reqLang)) {
            System.err.println("no asr defined for lang " + reqLang);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "no asr defined for lang " + reqLang);

            System.out.println(endstr);
            return;
        }
        //</editor-fold>

        byte[] sound = null;
        boolean doASR = false;
        String source = "textinput";
        String question = request.getParameter("q");
        if (question == null) {
            //<editor-fold defaultstate="collapsed" desc="handle binary parameter">
//            if (agent.remoteASRs.contains(l)) {
//            } else {
            if (isMultipart) {
                try {
                    Part audiopart = request.getPart("sbytes");
                    InputStream is = audiopart.getInputStream();
                    sound = new byte[is.available()];
                    is.read(sound, 0, is.available());
//                audiopart.delete();
                } catch (IOException | ServletException ex) {
                    System.err.println("erroneous bytes in 'sbytes' parameter: " + ex.getLocalizedMessage());
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "erroneous bytes in 'sbytes' parameter.");

                    System.out.println(endstr);
                    return;
                }
            } else {
                question = request.getParameter("sbytes");
                if (question == null) {
                    System.err.println("'sbytes' missing");
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "'sbytes' missing");

                    System.out.println(endstr);
                    return;
                } else {
                    //<editor-fold defaultstate="collapsed" desc="OLD: parse array of bytes from string">
                    System.err.println("warn: parsing array of bytes from string");

                    List<Byte> tmp;
                    try {
                        tmp = JSON.parseArray(question, Byte.class);
                    } catch (Exception ex1) {
                        System.err.println("JSON.parseArray: " + ex1.getMessage());
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "wrong audio encoding");

                        System.out.println(endstr);
                        return;
                    }

                    sound = new byte[tmp.size()];
                    for (int i = 0; i < tmp.size(); i++) {
                        sound[i] = tmp.get(i);
                    }
                    //</editor-fold>
                }
            }

            source = "speechinput";
            doASR = true;
            //</editor-fold>
        }

        //exception...(only for echo ?)
        boolean asrOnly = false;
        boolean kws = false;

        if (task.endsWith("-asr")) {
            asrOnly = true;
            task = task.replace("-asr", "");

            if (task.endsWith("-kws") && grxml != null) {

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                InputSource sourcea = new InputSource(new StringReader(grxml));
                try {
                    Document document = factory.newDocumentBuilder().parse(sourcea);
                } catch (ParserConfigurationException | SAXException ex) {
                    System.err.println("error in grammar XML: " + ex);
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "error in grammar XML: " + ex);
                    System.out.println(endstr);
                    return;
                }

                kws = true;
                task = task.replace("-kws", "");
            }
        }

        //use parsed question according to task
        String asrresult = null;
        String answer = null;
        switch (task) {
            case "noctivago":
                //<editor-fold defaultstate="collapsed" desc="noctivago">
                String url = agent.config.getProperty("taskNotctivagoURL");

                try {
                    if (!doASR) {
                        answer = Common.getHTML(url + "?asrresult=" + question + "&lang=" + reqLang.name());     //for _WELCOME_
                    } else {
                        if (agent.asr == null || !agent.asr.containsKey(reqLang)) {
                            System.err.println("error: asr unavailable");
                            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "asr unavailable");
                            System.out.println(endstr);
                            return;
                        }

                        ASR currasr = agent.asr.get(reqLang);

                        String asrtaskname = Common.getHTML(url + "?lang=" + reqLang.name());
                        if (new File(asrtaskname).exists()) {
                            currasr.loadTask(
                                    asrtaskname,
                                    agent.config.getProperty("modelsDir" + (reqLang.equals(Agent.lang.PT) ? "" : reqLang.name().toUpperCase())), //HACK!!! instead, attach modelsDir to ASR (in Kiosk)
                                    new File(asrtaskname).getName());
                        } else {
                            currasr.setTask(asrtaskname);
                        }

                        ASRresult ares = currasr.recognizeBytes(sound);
                        answer = Common.getHTML(url + "?asrresult=" + URLEncoder.encode(ares.getRecog(), "ISO-8859-1") + "&conf=" + ares.getConf() + "&lang=" + reqLang.name());
                    }
                } catch (Exception ex) {
                    System.err.println("error invoking noctivago: " + ex);
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "error invoking noctivago");
                    System.out.println(endstr);
                    return;
                }

                break;
            //</editor-fold>
            case "monserrate":
                //<editor-fold defaultstate="collapsed" desc="monserrate">
                if (agent.qa == null) {
                    System.err.println("error: qa unavailable");
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "qa unavailable");
                    System.out.println(endstr);
                    return;
                }

                if (doASR) {
                    if (agent.asr == null) {
                        System.err.println("error: asr unavailable");
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "asr unavailable");
                        System.out.println(endstr);
                        return;
                    }

                    System.err.println(now + ": asr " + reqLang.name() + " started");
                    try {
                        if (!agent.remoteASRs.contains(reqLang)) {
                            asrresult = agent.asr.get(reqLang).recognizeBytes(sound).getRecog();
                            question = asrresult;
                        } else {
                            //<editor-fold defaultstate="collapsed" desc="remote ASR recognizeBytes">

                            URL url2 = new URL(agent.asr.get(reqLang).getID());           //hack to get URL
                            String recvJSON = "";
                            String boundary = "*****";
                            String lineEnd = "\r\n";
                            String twoHyphens = "--";

                            HttpURLConnection connection = (HttpURLConnection) url2.openConnection();
                            connection.setDoOutput(true);
                            connection.setConnectTimeout(30 * 1000);
                            connection.setReadTimeout(30 * 1000);
                            connection.setRequestMethod("POST");
                            connection.setRequestProperty("Connection", "Keep-Alive");
                            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                            try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream())) {
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"task\"" + lineEnd + lineEnd);
                                dos.writeBytes("echo" + lineEnd);

                                // Send parameter #2
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"lang\"" + lineEnd + lineEnd);
                                dos.writeBytes(reqLang.name() + lineEnd);

                                // Send a binary file
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"sbytes\"" + lineEnd + lineEnd);      //;filename=\"" + temp_file.getName() +"\"" + lineEnd
                                dos.write(sound);
                                dos.writeBytes(lineEnd);

                                // send multipart form data necesssary after file data... 

                                dos.writeBytes(lineEnd);
                                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                                // close streams 
                                dos.flush();
                            }

//                            try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), "UTF-8")) {
//                                out.write("task=echo&lang=" + l.name() + "&sbytes=" + question);
//                            }

                            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                                throw new Exception(connection.getResponseMessage());
                            }

                            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                                String decodedString;
                                while ((decodedString = in.readLine()) != null) {
                                    recvJSON += decodedString;
                                }
                            }

                            question = recvJSON;
                            asrresult = recvJSON;

                            // BR exception
//                            if (l.equals(Agent.lang.PT) && agent.asr.containsKey(Agent.lang.BR)) {
//                                String asrpt = question;
//                                question = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><questions><q>" + asrpt + "</q><q>";
//
//                                System.err.println(now + ": asr BR started");
//                                try {
//                                    String asrbr = agent.asr.get(Agent.lang.BR).recognizeBytes(sound);
//                                    question += asrbr + "</q></questions>";
//                                } catch (Exception ex) {
//                                    System.err.println("asr BR failed: " + ex.getMessage() + "; sending PT only");
//
//                                    question = asrpt;
//                                }
//                            }
                            //</editor-fold>
                        }
                    } catch (Exception ex) {
                        System.err.println("asr failed: " + ex.getMessage());
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "asr failed");

                        System.out.println(endstr);
                        return;
                    }

                    //<editor-fold defaultstate="collapsed" desc="BR exception, for local PT ASR">
                    if (reqLang.equals(Agent.lang.PT) && agent.asr.containsKey(Agent.lang.BR)) {
                        String asrpt = question;
                        question = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><questions><q>" + asrpt + "</q><q>";

                        System.err.println(now + ": asr BR started");
                        try {
                            String asrbr = agent.asr.get(Agent.lang.BR).recognizeBytes(sound).getRecog();
                            question += asrbr + "</q></questions>";
                        } catch (Exception ex) {
                            System.err.println("asr BR failed: " + ex.getMessage() + "; sending PT only");

                            question = asrpt;
                        }
                    }
                    //</editor-fold>

                    if (question.trim().isEmpty()) {
                        System.err.println("not responding due to conf < minc");
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "not responding due to conf < minc");
                        System.out.println(endstr);
                        return;
                    }
                }

                System.err.println(now + ": monserrate QA started");
                System.err.println("--question: " + question);

                try {
                    answer = agent.qa.get(reqLang).ask("unity", source, question);   //request.getSession().getId()
                } catch (Exception ex) {
                    System.err.println("monserrate failed :" + ex.getMessage());
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "monserrate failed");
                    System.out.println(endstr);
                    return;
                }
                break;
            //</editor-fold>
            case "talkpedia":
                //<editor-fold defaultstate="collapsed" desc="talkpedia - PT and EN">
                if (agent.qaTalkpedia == null) {
                    System.err.println("error: talkpedia qa unavailable");
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "talkpedia qa unavailable");
                    System.out.println(endstr);
                    return;
                }

                if (doASR) {
                    if (agent.asr == null) {
                        System.err.println("error: asr unavailable");
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "asr unavailable");
                        System.out.println(endstr);
                        return;
                    }

                    System.err.println(now + ": asr " + reqLang.name() + " started");
                    try {
                        if (!agent.remoteASRs.contains(reqLang)) {
                            asrresult = agent.asr.get(reqLang).recognizeBytes(sound).getRecog();
                            question = asrresult;
                        } else {
                            //<editor-fold defaultstate="collapsed" desc="remote ASR recognizeBytes">

                            URL url2 = new URL(agent.asr.get(reqLang).getID());           //hack to get URL
                            String recvJSON = "";
                            String boundary = "*****";
                            String lineEnd = "\r\n";
                            String twoHyphens = "--";

                            HttpURLConnection connection = (HttpURLConnection) url2.openConnection();
                            connection.setDoOutput(true);
                            connection.setConnectTimeout(30 * 1000);
                            connection.setReadTimeout(30 * 1000);
                            connection.setRequestMethod("POST");
                            connection.setRequestProperty("Connection", "Keep-Alive");
                            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                            try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream())) {
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"task\"" + lineEnd + lineEnd);
                                dos.writeBytes("echo" + lineEnd);

                                // Send parameter #2
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"lang\"" + lineEnd + lineEnd);
                                dos.writeBytes(reqLang.name() + lineEnd);

                                // Send a binary file
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"sbytes\"" + lineEnd + lineEnd);      //;filename=\"" + temp_file.getName() +"\"" + lineEnd
                                dos.write(sound);
                                dos.writeBytes(lineEnd);

                                // send multipart form data necesssary after file data... 

                                dos.writeBytes(lineEnd);
                                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                                // close streams 
                                dos.flush();
                            }

//                            try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), "UTF-8")) {
//                                out.write("task=echo&lang=" + l.name() + "&sbytes=" + question);
//                            }

                            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                                throw new Exception(connection.getResponseMessage());
                            }

                            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                                String decodedString;
                                while ((decodedString = in.readLine()) != null) {
                                    recvJSON += decodedString;
                                }
                            }

                            question = recvJSON;
                            asrresult = recvJSON;

                            // BR exception
//                            if (l.equals(Agent.lang.PT) && agent.asr.containsKey(Agent.lang.BR)) {
//                                String asrpt = question;
//                                question = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><questions><q>" + asrpt + "</q><q>";
//
//                                System.err.println(now + ": asr BR started");
//                                try {
//                                    String asrbr = agent.asr.get(Agent.lang.BR).recognizeBytes(sound);
//                                    question += asrbr + "</q></questions>";
//                                } catch (Exception ex) {
//                                    System.err.println("asr BR failed: " + ex.getMessage() + "; sending PT only");
//
//                                    question = asrpt;
//                                }
//                            }
                            //</editor-fold>
                        }
                    } catch (Exception ex) {
                        System.err.println("asr failed: " + ex.getMessage());
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "asr failed");

                        System.out.println(endstr);
                        return;
                    }

                    //<editor-fold defaultstate="collapsed" desc="BR exception, for local PT ASR">
                    if (reqLang.equals(Agent.lang.PT) && agent.asr.containsKey(Agent.lang.BR)) {
                        String asrpt = question;
                        question = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><questions><q>" + asrpt + "</q><q>";

                        System.err.println(now + ": asr BR started");
                        try {
                            String asrbr = agent.asr.get(Agent.lang.BR).recognizeBytes(sound).getRecog();
                            question += asrbr + "</q></questions>";
                        } catch (Exception ex) {
                            System.err.println("asr BR failed: " + ex.getMessage() + "; sending PT only");

                            question = asrpt;
                        }
                    }
                    //</editor-fold>

                    if (question.trim().isEmpty()) {
                        System.err.println("not responding due to conf < minc");
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "not responding due to conf < minc");
                        System.out.println(endstr);
                        return;
                    }
                }

                System.err.println(now + ": talkpedia QA started");
                System.err.println("--question: " + question);

                try {
                    answer = agent.qaTalkpedia.get(reqLang).ask("unity", source, question);   //request.getSession().getId()
                } catch (Exception ex) {
                    System.err.println("talkpedia failed :" + ex.getMessage());
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "talkpedia failed");
                    System.out.println(endstr);
                    return;
                }
                break;
            //</editor-fold>
            case "lcexp":
                //<editor-fold defaultstate="collapsed" desc="lcexp - PT and EN">
                if (agent.qaLCexp == null) {
                    System.err.println("error: lcexp qa unavailable");
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "lcexp qa unavailable");
                    System.out.println(endstr);
                    return;
                }

                if (doASR) {
                    if (agent.asr == null) {
                        System.err.println("error: asr unavailable");
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "asr unavailable");
                        System.out.println(endstr);
                        return;
                    }

                    System.err.println(now + ": asr " + reqLang.name() + " started");
                    try {
                        if (!agent.remoteASRs.contains(reqLang)) {
                            asrresult = agent.asr.get(reqLang).recognizeBytes(sound).getRecog();
                            question = asrresult;
                        } else {
                            //<editor-fold defaultstate="collapsed" desc="remote ASR recognizeBytes">

                            URL url2 = new URL(agent.asr.get(reqLang).getID());           //hack to get URL
                            String recvJSON = "";
                            String boundary = "*****";
                            String lineEnd = "\r\n";
                            String twoHyphens = "--";

                            HttpURLConnection connection = (HttpURLConnection) url2.openConnection();
                            connection.setDoOutput(true);
                            connection.setConnectTimeout(30 * 1000);
                            connection.setReadTimeout(30 * 1000);
                            connection.setRequestMethod("POST");
                            connection.setRequestProperty("Connection", "Keep-Alive");
                            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                            try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream())) {
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"task\"" + lineEnd + lineEnd);
                                dos.writeBytes("echo" + lineEnd);

                                // Send parameter #2
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"lang\"" + lineEnd + lineEnd);
                                dos.writeBytes(reqLang.name() + lineEnd);

                                // Send a binary file
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"sbytes\"" + lineEnd + lineEnd);      //;filename=\"" + temp_file.getName() +"\"" + lineEnd
                                dos.write(sound);
                                dos.writeBytes(lineEnd);

                                // send multipart form data necesssary after file data... 

                                dos.writeBytes(lineEnd);
                                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                                // close streams 
                                dos.flush();
                            }

//                            try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), "UTF-8")) {
//                                out.write("task=echo&lang=" + l.name() + "&sbytes=" + question);
//                            }

                            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                                throw new Exception(connection.getResponseMessage());
                            }

                            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                                String decodedString;
                                while ((decodedString = in.readLine()) != null) {
                                    recvJSON += decodedString;
                                }
                            }

                            question = recvJSON;
                            asrresult = recvJSON;

                            // BR exception
//                            if (l.equals(Agent.lang.PT) && agent.asr.containsKey(Agent.lang.BR)) {
//                                String asrpt = question;
//                                question = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><questions><q>" + asrpt + "</q><q>";
//
//                                System.err.println(now + ": asr BR started");
//                                try {
//                                    String asrbr = agent.asr.get(Agent.lang.BR).recognizeBytes(sound);
//                                    question += asrbr + "</q></questions>";
//                                } catch (Exception ex) {
//                                    System.err.println("asr BR failed: " + ex.getMessage() + "; sending PT only");
//
//                                    question = asrpt;
//                                }
//                            }
                            //</editor-fold>
                        }
                    } catch (Exception ex) {
                        System.err.println("asr failed: " + ex.getMessage());
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "asr failed");

                        System.out.println(endstr);
                        return;
                    }

                    //<editor-fold defaultstate="collapsed" desc="BR exception, for local PT ASR">
                    if (reqLang.equals(Agent.lang.PT) && agent.asr.containsKey(Agent.lang.BR)) {
                        String asrpt = question;
                        question = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><questions><q>" + asrpt + "</q><q>";

                        System.err.println(now + ": asr BR started");
                        try {
                            String asrbr = agent.asr.get(Agent.lang.BR).recognizeBytes(sound).getRecog();
                            question += asrbr + "</q></questions>";
                        } catch (Exception ex) {
                            System.err.println("asr BR failed: " + ex.getMessage() + "; sending PT only");

                            question = asrpt;
                        }
                    }
                    //</editor-fold>

                    if (question.trim().isEmpty()) {
                        System.err.println("not responding due to conf < minc");
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "not responding due to conf < minc");
                        System.out.println(endstr);
                        return;
                    }
                }

                System.err.println(now + ": lcexp QA started");
                System.err.println("--question: " + question);

                try {
                    answer = agent.qaLCexp.get(reqLang).ask("unity", source, question);   //request.getSession().getId()
                } catch (Exception ex) {
                    System.err.println("lcexp failed :" + ex.getMessage());
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "lcexp failed");
                    System.out.println(endstr);
                    return;
                }
                break;
            //</editor-fold>
            case "opinion":
                //<editor-fold defaultstate="collapsed" desc="opinion - ONLY WITHOUT MULTIPART FORM">
                if (agent.opinionDump == null || agent.mp3enc == null || agent.mp3encopt == null) {
                    System.err.println("error: opinions not fully set");
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "opinions unavailable");
                    System.out.println(endstr);
                    return;
                }

                String name = request.getParameter("name");
                String phone = request.getParameter("phone");
                String email = request.getParameter("email");

                Common.stringToFile(
                        "name: " + (name != null ? name : "non")
                        + "\r\nemail: " + (email != null ? email : "non")
                        + "\r\nphone: " + (phone != null ? phone : "non")
                        + (sound == null ? "\r\ntext: " + question : ""),
                        new File(agent.opinionDump, "opinion" + now + ".txt").getAbsolutePath());

                if (sound != null) {
                    File wavpath = new File(agent.opinionDump, "opinion" + now + ".wav");
                    File mp3path = new File(agent.opinionDump, "opinion" + now + ".mp3");

                    ByteArrayInputStream bais = new ByteArrayInputStream(sound);
                    AudioInputStream ai = new AudioInputStream(bais, new AudioFormat(16000, 16, 1, true, false), sound.length);
                    AudioSystem.write(ai, AudioFileFormat.Type.WAVE, wavpath);

                    try {
                        java.lang.Runtime.getRuntime().exec(agent.mp3enc + " " + agent.mp3encopt + " " + wavpath.getPath() + " " + mp3path.getPath()).waitFor();
                        wavpath.delete();
                    } catch (Exception ex) {       //IOException | InterruptedException
                        System.err.println("opinion audio not registered due to mp3enc: " + ex.getMessage());
                        System.out.println(endstr);
                        return;
                    }
                }
                System.out.println(endstr);
                return;
            //</editor-fold>
            case "echo":
                //<editor-fold defaultstate="collapsed" desc="echo (-kws)(-asr)">
                if (doASR) {
                    if (agent.asr == null) {
                        System.err.println("error: asr unavailable");
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "asr unavailable");
                        System.out.println(endstr);
                        return;
                    }

                    System.err.println(now + ": asr started");
                    try {
                        if (!agent.remoteASRs.contains(reqLang)) {
                            if (kws) {
                                //write grammar
                                final File sgrsFile = File.createTempFile(now + "_", ".grxml");
                                sgrsFile.deleteOnExit();
                                try (FileWriter writer = new FileWriter(sgrsFile)) {
                                    writer.write(grxml);
                                }
//                                System.err.println("kws");
                                agent.asrkws.get(reqLang).loadTask(sgrsFile.getPath(), null, utils.Common.getUniqueId(grxml));
                                question = agent.asrkws.get(reqLang).recognizeBytes(sound).getRecog();
                            } else {
                                question = agent.asr.get(reqLang).recognizeBytes(sound).getRecog();
                            }
                            asrresult = question;
                        } else {
                            //<editor-fold defaultstate="collapsed" desc="remote ASR recognizeBytes">
                            URL url2 = new URL(agent.asr.get(reqLang).getID());           //hack to get URL
                            String recvJSON = "";
                            String boundary = "*****";
                            String lineEnd = "\r\n";
                            String twoHyphens = "--";

                            HttpURLConnection connection = (HttpURLConnection) url2.openConnection();
                            connection.setDoOutput(true);
                            connection.setConnectTimeout(30 * 1000);
                            connection.setReadTimeout(30 * 1000);
                            connection.setRequestMethod("POST");
                            connection.setRequestProperty("Connection", "Keep-Alive");
                            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                            try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream())) {
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"task\"" + lineEnd + lineEnd);
                                dos.writeBytes("echo" + lineEnd);

                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"lang\"" + lineEnd + lineEnd);
                                dos.writeBytes(reqLang.name() + lineEnd);

                                // Send a binary file
                                dos.writeBytes(twoHyphens + boundary + lineEnd);
                                dos.writeBytes("Content-Disposition: form-data; name=\"sbytes\"" + lineEnd + lineEnd);      //;filename=\"" + temp_file.getName() +"\"" + lineEnd
                                dos.write(sound);
                                dos.writeBytes(lineEnd);

                                // send multipart form data necesssary after file data... 
                                dos.writeBytes(lineEnd);
                                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                                // close streams 
                                dos.flush();
                            }

//                            try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), "UTF-8")) {
//                                out.write("task=echo&lang=" + l.name() + "&sbytes=" + question);
//                            }

                            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                                throw new Exception(connection.getResponseMessage());
                            }

                            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                                String decodedString;
                                while ((decodedString = in.readLine()) != null) {
                                    recvJSON += decodedString;
                                }
                            }

                            question = recvJSON;
                            asrresult = recvJSON;
                            //</editor-fold>
                        }
                    } catch (Exception ex) {
                        System.err.println("asr failed: " + ex.getMessage());
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "asr failed");

                        System.out.println(endstr);
                        return;
                    }
                }

                answer = question;
                break;
            //</editor-fold>
            default:
                System.err.println("unknown or disabled task: " + task);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "unknown or disabled task: " + task);

                System.out.println(endstr);
                return;
        }

        if (answer == null || (answer = answer.trim()).isEmpty()) {
            System.err.println("no answer");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "no answer");

            System.out.println(endstr);
            return;
        }

        //parse xml answer
        List<Emotion> textemotionduration = new ArrayList<Emotion>();
        //<editor-fold defaultstate="collapsed" desc="xml answer">
        if (answer.startsWith("<")) {
//            answer = answer.replaceAll("<", " <");

            DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(true); // never forget this!

            try {
                DocumentBuilder builder = domFactory.newDocumentBuilder();
                InputSource is = new InputSource();
                is.setCharacterStream(new StringReader(answer));
                Document doc = builder.parse(is);
                XPathFactory factory = XPathFactory.newInstance();
                XPath xpath = factory.newXPath();

                XPathExpression expr = xpath.compile("//text");
                Object result = expr.evaluate(doc, XPathConstants.NODESET);
                NodeList nodes = (NodeList) result;
                if (nodes.getLength() < 0) {
                    throw new Exception("no text nodes to parse");
                }

                //main emotion exists
                answer = "";
                for (int i = 0; i < nodes.getLength(); i++) {
                    String text = nodes.item(i).getTextContent().trim();

                    Node etypenode = null;
                    if ((etypenode = nodes.item(i).getAttributes().getNamedItem("emotion")) != null) {
                        float eint2 = 0;

                        Node eintnode = null;
                        if ((eintnode = nodes.item(i).getAttributes().getNamedItem("duration")) != null) {
                            try {
                                eint2 = Float.parseFloat(eintnode.getTextContent().trim());
                            } catch (Exception ex) {
                                System.err.println("unable to parse float, defaulting to 0: " + eintnode.getTextContent());
                                eint2 = 0;
                            }
                        }

                        textemotionduration.add(new Emotion(text, etypenode.getTextContent(), eint2));
                    } else {
                        textemotionduration.add(new Emotion(text, "", 0));
                    }

                    answer += text + " ";
                }
                answer = answer.replaceAll("\\s+", " ").trim();
            } catch (Exception ex) {
                System.err.println("xml answer parsing failed :" + ex.getMessage());
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "xml answer parsing failed");

                System.out.println(endstr);
                return;
            }
        }
        //</editor-fold>

//        if (!agent.remoteASRs.contains(l)) {
        System.err.println("--answer: " + answer + "; emotion info: " + textemotionduration);
//        }

        String jsontosend = "";
        if (agent.tts == null || asrOnly) {
//            System.err.println("warn: tts unavailable; sending text only.");
            jsontosend = answer; //"[" + JSON.toJSONString(new TTSResult(answer, "", new String[]{}, new float[]{})) + "]";
        } else {
            //<editor-fold defaultstate="collapsed" desc="tts">
            TTS localtts = agent.tts.get(reqLang);
            boolean remoteTTS = agent.remoteTTSs.contains(reqLang);

            if (voice == null || !localtts.voiceAvailable(voice)) {     // if (localtts instanceof TTSRemote) voiceAvailable = true
                if (!remoteTTS) {
                    try {
                        voice = localtts.getDefaultVoice();      //fallback
                    } catch (Exception ex) {
                        System.err.println("tts not properly configured: " + ex.getMessage());
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "tts not properly configured: " + ex.getMessage());

                        System.out.println(endstr);
                        return;
                    }
                }
            }

            //parse platform: default to OGG
            TTS.AUDIOFORMAT platformAF = DEFAULT_TTS_AUDIOFORMAT;
            if (strPlatform != null) {
                try {
                    platformAF = TTS.AUDIOFORMAT.valueOf(strPlatform);
                } catch (IllegalArgumentException ex) {
                    platformAF = DEFAULT_TTS_AUDIOFORMAT;
                }
            }

            if (!remoteTTS) {       //set file (audio) server location
                if (fnameprefx == null) {
                    fnameprefx = new URL("http", request.getServerName(), fileport, "/").toString();
                }
//                else {
//                    if (fnameprefx.contains("?") && !fnameprefx.contains("url")) {     //for 'fnameprefx = downloadAudioProxy.php?filename='
//                        int qmark = fnameprefx.indexOf("?") + 1;
//                        String tmp = fnameprefx.substring(0, qmark) + "url=" + new URL("http", request.getServerName(), fileport, "/").toString() + "&";
//
//                        fnameprefx = tmp + fnameprefx.substring(qmark, fnameprefx.length());
//                    } 
//                    else {
//                        fnameprefx += "url=" + new URL("http", request.getServerName(), fileport, "/").toString();      //unused, for now
//                    }
//                }
            }

            try {
                List<TTSResult> ttsr = localtts.getAudio(voice, answer, fnameprefx, platformAF, speechrate);
                if (ttsr == null) {
                    System.err.println("--tts failed. no files created: " + reqLang + "; " + voice + "; " + answer + "; " + fnameprefx + "; " + platformAF);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "tts failed: " + reqLang + "; " + answer + "; " + platformAF);

                    System.out.println(endstr);
                    return;
                }

//                System.err.println(ttsr.get(0).getUrl());

                if (remoteTTS) {
                    String ttsonly = ttsr.get(0).getSentence();

                    if (doASR) {
                        jsontosend = ttsonly.substring(0, ttsonly.length() - 12) + ",\"orig\":\"" + asrresult + "\"}],\"ted\":" + JSON.toJSONString(textemotionduration) + "}";     //hack; TODO: fix
                    } else {
                        jsontosend = ttsonly.substring(0, ttsonly.length() - 3) + JSON.toJSONString(textemotionduration) + "}";     //hack; TODO: fix
                    }
                } else {
                    //localhost preloaded cache fix
                    if (fnameprefx != null) {
                        for (TTSResult t1 : ttsr) {
                            if (!t1.getUrl().contains(fnameprefx)) {
                                for (TTSResult t : ttsr) {
                                    t.setURL(t.getUrl().replace("http://localhost:8080/", fnameprefx));     //TODO: apply regex for port num
                                }

                                break;
                            }
                        }

//                        if (!ttsr.get(0).getUrl().contains(fnameprefx)) {
//                            for (TTSResult t : ttsr) {
//                                t.setURL(t.getUrl().replace("http://localhost:8080/", fnameprefx));     //TODO: apply regex for port num
//                            }
//                        }
                    }

                    if (doASR) {
                        jsontosend = JSON.toJSONString(ttsr);
                        jsontosend = jsontosend.substring(0, jsontosend.length() - 2) + ",\"orig\":\"" + asrresult + "\"}]";
                    } else {
                        jsontosend = JSON.toJSONString(ttsr);
                    }
                    jsontosend = "{\"ttsresults\":" + jsontosend + ",\"ted\":" + JSON.toJSONString(textemotionduration) + "}";       //.substring(0, jsontosend.length() - 1) + ",\"eint\":" + eint + ",\"etype\":\"" + etype + "\"}";
                }
            } catch (Exception ex) {
                System.err.println("tts error: " + ex.getMessage());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "tts error");

                System.out.println(endstr);
                return;
            }
            //</editor-fold>   
        }

//        jsontosend += ",\"ted\":[";
//        for (int i = 0; i < textemotionduration.size(); i++) {
//            Map.Entry<String, Map.Entry<String, Float>> ted = textemotionduration.get(i);
//            jsontosend += "{\"text\":\"" + URLEncoder.encode(ted.getKey(), "UTF-8") + "\",\"emotion\":\"" + ted.getValue().getKey() + "\",\"duration\":" + ted.getValue().getValue() + "}";
//
//            if (i < textemotionduration.size() - 1) {
//                jsontosend += ",";
//            }
//        }
//        jsontosend += "]}";

        System.out.println(endstr);

//        response.setHeader("Access-Control-Allow-Origin", "*");
        if (jsontosend.trim().startsWith("{")) {
            response.setContentType("application/json");
        } else {
            response.setContentType("text/plain");
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().print(jsontosend);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getRequestURI().contains("favicon")) {
            return;
        }

        if (request.getRequestURI().contains("crossdomain")) {
            response.setContentType("text/xml");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().print("<?xml version='1.0'?><cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>");
            return;
        }
    }
}
//<editor-fold defaultstate="collapsed" desc="comment">
//        if (answer.startsWith("<")) {
//                    //prepare xml parser
//                    DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
//                    domFactory.setNamespaceAware(true); // never forget this!
//                    DocumentBuilder builder = domFactory.newDocumentBuilder();
//
//                    InputSource is = new InputSource();
//                    is.setCharacterStream(new StringReader(answer));
//                    Document doc = builder.parse(is);
//
//                    XPathFactory factory = XPathFactory.newInstance();
//                    XPath xpath = factory.newXPath();
//
//                    //get xml answer
//                    XPathExpression expr = xpath.compile("//text/text()");
//                    answer = expr.evaluate(doc);
//
//                    //get xml answer's emotion
//                    try {
//                        expr = xpath.compile("//emotion/@type");
//                        etype = expr.evaluate(doc);
//                        expr = xpath.compile("//emotion/@intensity");
//                        eint = Float.parseFloat(expr.evaluate(doc));
//
//                    } catch (NumberFormatException ex) {
//                        eint = 0;
//                    } catch (XPathExpressionException ex) {
//                        eint = 0;
//                    }
//                }
//</editor-fold>
