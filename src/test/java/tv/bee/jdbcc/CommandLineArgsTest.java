package tv.bee.jdbcc;

import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;
import static org.junit.matchers.JUnitMatchers.containsString;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assert.assertThat;
import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import static org.hamcrest.CoreMatchers.*;

import java.util.Collection;
import java.util.Map;
import java.io.*;
import java.lang.reflect.Field;

import com.thoughtworks.xstream.XStream;
import tv.bee.jdbcc.CommandLineArgs;

/* 
 * JUnit tests for CommandLineArgs class
 * Copyright 2008 by BeeTV http://bee.tv
 * Author Andrew Skiba <andrew@tikalk.com>
 */

@RunWith(Parameterized.class)
public class CommandLineArgsTest {
    //test input
    private String [] args;

    //expected test data
    private Map<String, Object> expectedFields;
    private CommandLineArgs.BadArgsException expectedException;

    //test subject
    CommandLineArgs cla;

    //actual data
    private CommandLineArgs.BadArgsException actualException;
    private String testName;
    private String expectedContentSubstr;


    @Parameterized.Parameters
    public static Collection data() {
        InputStream is = CommandLineArgsTest.class.getResourceAsStream("/"+CommandLineArgsTest.class.getName()+".xml");

        Collection c = (Collection) new XStream().fromXML(is);
        try {
            is.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return c;
    }

    public CommandLineArgsTest (String testName, String args, CommandLineArgs.BadArgsException expectedException,
                                Map<String, Object> expectedFields, String expectedContentSubstr) {
        this.testName = testName;
        this.expectedContentSubstr = expectedContentSubstr;
        this.args = splitArgs(args);
        this.expectedException = expectedException;
        this.expectedFields = expectedFields;
    }

    private String [] splitArgs(String args) {
        if (args == null || args.trim().length()==0)
            return new String [] {};
        else
            return args.split(" ");
    }

    @Before
    public void setup () {
        //FIXME: this split is simplistic, should handle backslashes and quotes
        try {
            cla = new CommandLineArgs(args);
        } catch (CommandLineArgs.BadArgsException e) {
            this.actualException = e;
        }
    }



    @Test
    public void testSyserr () {
        if (actualException==null && expectedException == null
                || actualException.equals(expectedException))
            return;

        actualException.printStackTrace();
        assertThat(testName, actualException, is(expectedException));
    }

    @Test
    public void testFields () throws NoSuchFieldException, IllegalAccessException {
        if (expectedFields == null)
            return;
        assumeNotNull(cla);

        for(String fieldName: expectedFields.keySet()) {
            try {
                Field field = cla.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                assertThat(testName, field.get(cla), is(expectedFields.get(fieldName)));
            } catch (NoSuchFieldException e){
                Assert.fail(testName+" - no such field: "+fieldName);
            }
        }
    }

    @Test
    public void testContent () throws IOException {
        if (expectedContentSubstr == null || expectedContentSubstr.length() == 0)
            return;

        InputStreamReader isr = cla.getScriptStream();
        BufferedReader br = new BufferedReader(isr);
        StringBuffer content = new StringBuffer();

        String line = br.readLine();
        while (line != null) {
            content.append(line);
            line = br.readLine();
        }
        assertThat(content.toString(), containsString(expectedContentSubstr));
    }

}
