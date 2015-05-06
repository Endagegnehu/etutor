/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package qa;

import agent.Agent;

/**
 *
 * @author pfialho
 */
public interface QA {
    public String ask(String id, String source, String question) throws Exception;
    public String getID();
    public void setID(String id);
}
