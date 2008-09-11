package tv.bee.jdbcc;

import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.*;

/* 
 * Command line parsing for jdbcc application
 * Copyright 2008 by BeeTV http://bee.tv
 * Author Andrew Skiba <andrew@tikalk.com>
 */

public class CommandLineArgs implements ParsedCommandLine {
    String connectionString;
    String driverClassName;
    String password;
    String user;
    boolean stopOnError;
    private Boolean isStopOnError;
    private String driverPath;

    public String getDriverPath() {
        return driverPath;
    }

    enum ConfigProperties {
        CONNECTION_STRING("connection_string"),
        DRIVER_CLASS("driver_class"),
        PASSWORD("password"),
        USER("user"),
        DRIVER_PATH("driver_path");

        String name;
        ConfigProperties (String name) {this.name=name;}
        String getName () {return name;}
    }

    private enum ErrorType {
         EXTRA_ARGS, BAD_ARG, FILE_NOT_FOUND, READ_ERROR, PROPERTY_NOT_FOUND, HELP, NO_ARGS
    }

    static ResourceBundle myResources = ResourceBundle.getBundle("BadArgsErrorTypes");

    public static class BadArgsException extends Exception {


        public BadArgsException (ErrorType errorType, Object ... args) {
            super (String.format(myResources.getString(errorType.toString()), args));
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof BadArgsException))
                return false;
            BadArgsException that = (BadArgsException) obj;
            return this.getMessage().equals(that.getMessage());
        }
    }

    private InputStreamReader scriptStream;
    private List<String> args;

    public CommandLineArgs(String [] args) throws BadArgsException {
        parseArgs(args);
    }

    private void parseArgs(String[] args) throws BadArgsException {
        this.args = new LinkedList<String>(Arrays.asList(args));


            readConfigProperties(false, System.getProperty("user.home")+"/.jdbcc");

            parseOptions();

            parseInputStreamArgs();

        if (!this.args.isEmpty())
            throw new BadArgsException(ErrorType.EXTRA_ARGS, StringUtils.join(args, ' '));

    }

    private void parseInputStreamArgs() throws BadArgsException {
        if (args.size() == 0) {
            handleNoArgs(true);
            return;
        }
        if (args.size() > 1)
            throw new BadArgsException(ErrorType.EXTRA_ARGS, StringUtils.join(args, ' '));
        String arg = args.remove(0);

        if (arg.equals("-")) {
            handleNoArgs(false);
            return;
        }

        if (isStopOnError == null)
                isStopOnError = true;

        try {
            scriptStream = new FileReader(arg);
        } catch (FileNotFoundException e) {
            throw new BadArgsException(ErrorType.FILE_NOT_FOUND, arg);
        }
    }

    private void handleNoArgs(boolean printWarning) {
        if (printWarning) {
            String msg = myResources.getString(ErrorType.NO_ARGS.toString());
            //FIXME: cannot be tested. Refactor this class to print messages on given stream instead of
            //hard-coded System.err
            System.err.println(msg);
        }
        if (isStopOnError == null)
                isStopOnError = false;
        scriptStream = new InputStreamReader(System.in);
        return;
    }


    private void parseOptions() throws BadArgsException {
        int newSize=args.size();
        int oldSize = newSize+1;
                //loop until there are no more options
        while (newSize < oldSize) {
            findAndParseOption();
            oldSize=newSize;
            newSize=args.size();
        }
    }

    private void findAndParseOption() throws BadArgsException {
        Iterator<String> i = args.iterator();
        while (i.hasNext()) {
            String arg = i.next();
            if (arg.startsWith("-") && !arg.equals("-")) {
                parseAndRemoveOption(arg, i);
            }
        }
    }

    private void parseAndRemoveOption(String arg, Iterator<String> i) throws BadArgsException {
        if (arg.equals("-recover")) {
            isStopOnError = false;
            i.remove();
        }
        else if (arg.equals("-strict")) {
            isStopOnError = true;
            i.remove();
        }
        else if (arg.equals("-config")) {
            i.remove();
            String fileName = i.next();
            readConfigProperties(true, fileName);
            i.remove();            
        }
        else if (arg.equals("-url")) {
            i.remove();
            connectionString = i.next();
            i.remove();
        }
        else if (arg.equals("-driver")) {
            i.remove();
            driverClassName = i.next();
            i.remove();
        }
        else if (arg.equals("-user")) {
            i.remove();
            user = i.next();
            i.remove();
        }
        else if (arg.equals("-passwd")) {
            i.remove();
            password = i.next();
            i.remove();
        }
        else if (arg.equals("-help")) {
            throw new BadArgsException(ErrorType.HELP);
        }
        else {
            throw new BadArgsException(ErrorType.BAD_ARG, arg);
        }
    }

    private void readConfigProperties(boolean mandatory, String fileName) throws BadArgsException {
        Properties props = new Properties();
        FileInputStream fis;
        try {
            File file = new File(fileName);
            if (! file.exists() && !mandatory)
                return;
            fis = new FileInputStream(file);
        } catch (IOException e) {
            throw new BadArgsException(ErrorType.FILE_NOT_FOUND, fileName);
        }
        try {
            props.load(fis);
            fis.close();
        } catch (IOException e) {
            throw new BadArgsException(ErrorType.READ_ERROR, fileName);
        }

        this.connectionString = readConfigProperty(props, fileName, ConfigProperties.CONNECTION_STRING);
        this.driverClassName = readConfigProperty(props, fileName, ConfigProperties.DRIVER_CLASS);
        this.driverPath = readConfigProperty(props, fileName, ConfigProperties.DRIVER_PATH);
        this.user = readConfigProperty(props, fileName, ConfigProperties.USER);
        this.password = readConfigProperty(props, fileName, ConfigProperties.PASSWORD);
    }

    private String readConfigProperty(Properties props, String fileName, ConfigProperties configProperty) throws BadArgsException {
        String res = props.getProperty(configProperty.getName());
        if (res == null) {
            throw new BadArgsException(ErrorType.PROPERTY_NOT_FOUND, fileName, configProperty.getName());
        }
        return res;
    }

    public InputStreamReader getScriptStream() {
        return scriptStream;
    }

    public String getDriverClassName() {
        return this.driverClassName;
    }

    public String getConnectionString() {
        return this.connectionString;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public boolean isStopOnError() {
        return this.isStopOnError;
    }
}
