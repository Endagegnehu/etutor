package qa;

import soapdust.Client;
import soapdust.ComposedValue;

public class SOAPclient implements QA {

    private final String endpoint;
    private final String fcall;
    private String id = "";

    public SOAPclient(String wsdl, String fcall) {
        this.endpoint = wsdl;
        this.fcall = fcall;
    }

    @Override
    public String ask(String userid, String source, String interaction) throws Exception {
        Client client = new Client();
        client.setWsdlUrl(endpoint + "?wsdl");
        client.setEndPoint(endpoint);

        ComposedValue query = new ComposedValue();
        query.put("userId", userid);
        query.put("source", source);
        query.put("interaction", interaction);
//        query.put("question", new String(interaction.getBytes(), "UTF-8"));

        ComposedValue result = client.call(fcall, query);
        return result.getComposedValue(fcall + "Response").getStringValue("return");      //as in CXF
    }

    @Override
    public String getID() {
        return id;
    }

    @Override
    public void setID(String id) {
        this.id = id;
    }
}
