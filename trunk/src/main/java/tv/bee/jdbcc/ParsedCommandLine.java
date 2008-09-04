package tv.bee.jdbcc;

import java.io.InputStreamReader;

/**
 * Created by IntelliJ IDEA.
 * User: andrew
 * Date: Sep 3, 2008
 * Time: 1:28:21 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ParsedCommandLine {
    InputStreamReader getScriptStream();

    String getDriverClassName();

    String getConnectionString();

    String getUser();

    String getPassword();

    boolean isStopOnError();
}
