/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package qa;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Simple client class to communicate with the ChatScript server.
 * Free to use with no need of attribution.
 */
public class ChatClient implements QA{

    /**
     * Single transaction, send a message and receive a response.
     * 
     * @param server chat script server hostname/ip
     * @param port chat script server listen port
     * @param userId user name as whom to connect to in chat (typically required except when running a 'ping') 
     * @param botname can be null (not ""), will use default bot id if so
     * @param message to send to the chat server, will be null only for initial message from a client
     * @return response from the chat server
     * @throws IOException on communication failure
     */
    public static String sendAndReceive(String server, int port, String userId, String botname, String message) throws IOException {
        final byte[] nul = new byte[]{0};
        byte[] out = new byte[0];
        
        // using default encoding for string to bytes
        // Protocol is userid, followed by botname followed by message, all three being \0-terminated
        if (userId != null) out = append(out,userId.getBytes());
        out = append(out,nul);
        if (botname != null) out = append(out,botname.getBytes());
        out = append(out,nul);
        if (message != null) out = append(out,message.getBytes());
        out = append(out,nul);

        // send, receive, close, no persistent connection
        Socket socket = new Socket(server, port);
        OutputStream oStream = socket.getOutputStream();
        oStream.write(out);//new String(out).getBytes(Charset.forName("ISO-8859-1"))
        
        InputStream iStream = socket.getInputStream();
        byte[] in = new byte[0];
        byte[] buff = new byte[1024];
        int readBytes = iStream.read(buff);
        while (readBytes > 0) {
            in = append(in, buff, readBytes);
            readBytes = iStream.read(buff);
        }
        
        socket.close();
        
        return new String(in);
    }

    /**
     * @param args
     * @throws IOException 
     */
    public static void main(String[] args) throws IOException {
        // test the above simple method
        // remember to turn on assertions when running this (using -ea VM flags) or else assertions won't kick in!!!
    	
        // use a random UUID as the user id so that the 'repeat/noerase' issue doesnt stump the test
        assert sendAndReceive("localhost", 1024, null, "1", null)
            .equals("1") : "Ping failed.";
        
        assert sendAndReceive("localhost", 1024, UUID.randomUUID().toString(), "georgia", "What is your name?")
             .equals("My name is Georgia.") : "Bot name selection failed.";
        
        assert sendAndReceive("localhost", 1024, UUID.randomUUID().toString(), null, "What is your name?")
            .equals("My name is Georgia.") : "Default bot name selection failed.";
            
        assert sendAndReceive("localhost", 1024, UUID.randomUUID().toString(), null, null)
            .equals("Welcome to ChatScript.") : "Empty input failed.";
        
        BufferedReader stdIn = new BufferedReader(
                new InputStreamReader(System.in));
        String userInput;
        
        while ((userInput = stdIn.readLine()) != null) {
            System.out.println(sendAndReceive("localhost", 1024, "sadfasasdafdasdf", "harry", userInput));
        }
    }

    private static byte[] append(byte[] out, byte[] add) {
        return append(out, add, add.length);
    }
    
    private static byte[] append(byte[] out, byte[] add, int addLen) {
        byte[] ret = new byte[out.length + addLen];
        
        int ii = 0;
        for (; ii < out.length; ++ii) ret[ii] = out[ii];
        for (int jj = 0; jj < addLen; ++jj) ret[ii+jj] = add[jj];

        return ret;
    }

    @Override
    public String ask(String id, String source, String question) throws Exception {
        return sendAndReceive("localhost", 1024, id, "edgar", question);
    }

    @Override
    public String getID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setID(String id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}