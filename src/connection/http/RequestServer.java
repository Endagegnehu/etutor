/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package connection.http;

import apps.Kiosk;
import javax.servlet.MultipartConfigElement;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 *
 * @author pfialho
 */
public class RequestServer extends Thread {

    private final String host;
    private final int port;
    private final int fileport;
    private final String fnameprefx;
    private final Kiosk af;

    public RequestServer(Kiosk af, String host, int port, int fileport, String fnameprefx) {
        super("RequestServer");
        this.af = af;
        this.port = port;
        this.fnameprefx = fnameprefx.trim();
        this.fileport = fileport;
        this.host = host.trim();
    }

    @Override
    public void run() {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);

        if (!host.isEmpty()) {
            connector.setHost(host);
        }

        connector.setPort(port);
        server.addConnector(connector);

        //server.setHandler(new RequestHandler());
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);

//        FilterHolder filterHolder;
//        filterHolder = new FilterHolder(MultiPartFilter.class);
//        filterHolder.setInitParameter("deleteFiles", "true");
//        context.addFilter(filterHolder, af.multipartDump.getAbsolutePath(), EnumSet.allOf(DispatcherType.class));
//        context.addFilter(MultiPartFilter.class, host, null)
        context.setMaxFormContentSize(1000000000);
        context.setContextPath("/");
        context.setAttribute("agent", af);
        context.setAttribute("fnameprefx", fnameprefx);
        context.setAttribute("fileport", fileport);
        server.setHandler(context);

        ServletHolder sh = new ServletHolder(new RequestServlet());
        sh.getRegistration().setMultipartConfig(new MultipartConfigElement(af.multipartDump.getAbsolutePath(), 1000000000, 1000000000, 1000000000));

        context.addServlet(sh, "/*");
        try {
            server.start();
            server.join();
        } catch (Exception ex) {
            System.err.println("request server start/join failed: " + ex.getMessage());
        }
    }
}
