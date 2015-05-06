/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package connection.http;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;

/**
 *
 * @author pfialho
 */
public class FileServer extends Thread{
    private final String host;
    private final int port;
    private final String servedDir;

    public FileServer(String host, int port, String servedDir) {
        super("FileServer");
        this.servedDir = servedDir;
        this.port = port;
        this.host = host.trim();
    }

    @Override
    public void run() {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        
        if(!host.isEmpty()){
            connector.setHost(host);
        }
        
        connector.setPort(port);
        server.addConnector(connector);
 
        ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setDirectoriesListed(true);
        resource_handler.setWelcomeFiles(new String[]{ "index.html" }); 
        resource_handler.setResourceBase(servedDir);
 
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { resource_handler, new DefaultHandler() });
        server.setHandler(handlers);
 
        try {
            server.start();
            server.join();
        } catch (Exception ex) {
            System.err.println("file server start/join failed: " + ex.getMessage());
        }
    }
}
