package tv.bee.jdbcc;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.Test;
import org.junit.Before;
import static org.junit.matchers.JUnitMatchers.containsString;
import static org.junit.Assert.assertThat;

import java.util.Collection;
import java.util.Map;
import java.io.*;

import com.thoughtworks.xstream.XStream;

/*
 * JUnit tests for CommandLineArgs class
 * Copyright 2008 by BeeTV http://bee.tv
 * Author Andrew Skiba <andrew@tikalk.com>
 */

@RunWith(Parameterized.class)
public class MainTest {
    private String testName;
    private String sql;
    private String expectedOutputSubstr;
    private StringWriter stdout;
    private String [] commandLine;

    @Parameterized.Parameters
    public static Collection data() {
        InputStream is = CommandLineArgsTest.class.getResourceAsStream("/"+MainTest.class.getName()+".xml");

        Collection c = (Collection) new XStream().fromXML(is);
        try {
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return c;
    }

    public MainTest (String testName, String commandLine, String sql, String expectedOutputSubstr) {

        this.testName = testName;
        this.commandLine = commandLine.split(" ");
        this.sql = sql;
        this.expectedOutputSubstr = expectedOutputSubstr;
    }

    @Before
    public void setUp() throws Exception {
        StringReader sr = new StringReader(sql);
        stdout = new StringWriter();

        CommandLineArgs cla = new CommandLineArgs(sr, new PrintWriter(stdout), null, commandLine);
        Main main = new Main(cla);
        main.setStdout(new PrintWriter(stdout));
        main.run();
    }

    @Test
    public void testContent () throws IOException {
        if (expectedOutputSubstr == null || expectedOutputSubstr.length() == 0)
            return;

        assertThat(testName, stdout.toString(), containsString(expectedOutputSubstr));
    }
}
