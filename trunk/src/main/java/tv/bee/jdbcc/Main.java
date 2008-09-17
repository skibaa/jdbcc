package tv.bee.jdbcc;

import java.io.*;
import java.sql.*;
import java.net.URLClassLoader;
import java.net.URL;
import java.net.MalformedURLException;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;

/* 
 * Main class for jdbcc application
 * Copyright 2008 by BeeTV http://bee.tv
 * Author Andrew Skiba <andrew@tikalk.com>
 */

public class Main {
    private CommandLineArgs cla;
    private Connection conn;
    private Pattern delimitersPattern;
    private List<Pattern> remarks;
    ArrayList<Integer> quotesPos;
    Pattern quote = Pattern.compile("'");
    long queriesComplete=0;
    long lastCompleteReportedClock=0;

    public Main(CommandLineArgs cla) {
        this.cla = cla;
    }

    static public void main(String [] args) throws Exception {
        CommandLineArgs cla=null;
        try {
            cla = new CommandLineArgs(args);
        } catch (CommandLineArgs.BadArgsException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        PrintWriter pw = new PrintWriter(System.out);
        try {
            Main m = new Main(cla); m.run();
        } finally {
            pw.close();
        }
    }

    public void run() throws Exception {

        openConnection();
        LineNumberReader lnr = null;
        try {
            for(Reader r: cla.getScriptReaders()) {
                lnr = executeOneScriptFile(r);
            }
        } finally {
            if (lnr != null) {
                lnr.close();
            }
            conn.close();
        }
    }

    private LineNumberReader executeOneScriptFile(Reader r) throws IOException, SQLException {
        LineNumberReader lnr;
        lnr = new LineNumberReader(r);
        String line;
        StringBuffer sb = new StringBuffer();
        while ((line=lnr.readLine())!=null) {

            sb.append("\n");
            sb.append(line);
            String query;

            while ((query=extractOneQuery(sb)) != null)
                executeQuery(query, lnr.getLineNumber());
            cla.getStdout().flush();
        }
        if (sb.length() > 0)
            executeQuery(sb.toString(), lnr.getLineNumber());
        return lnr;
    }

    private String extractOneQuery(StringBuffer stringSoFar) {
        String [] parts = quote.split(stringSoFar);
        int partOffset=0;
        int start=-1;
        int end=-1;

        for (int i=0; i<parts.length; i+=2) {
            if (i>0)
                partOffset += parts[i-1].length()+quote.pattern().length(); //FIXME: will not work with complex patterns
            Matcher msemicolon = getDelimitersPattern().matcher(parts[i]);
            if (msemicolon.find()) {
                start=partOffset+msemicolon.start();
                end=partOffset+msemicolon.end();
                break;
            }
            partOffset += parts[i].length()+quote.pattern().length(); //FIXME: will not work with complex patterns
        }
        if (start==-1)
            return null;
        String nextQuery = stringSoFar.substring(end);
        String thisQuery = stringSoFar.substring(0, start);
        stringSoFar.setLength(0);
        stringSoFar.append(nextQuery);
        return thisQuery;
    }

    private void openConnection() throws ClassNotFoundException, SQLException, MalformedURLException, IllegalAccessException, InstantiationException, FileNotFoundException {
        if (cla.getDriverPath() != null) {
            File driverFile = new File(cla.getDriverPath());
            if (!driverFile.exists())
                throw new FileNotFoundException(cla.getDriverPath());
            ClassLoader cl = new URLClassLoader(new URL [] { driverFile.toURI().toURL() });

            final Class dc = Class.forName(cla.getDriverClassName(), true, cl);
            final Driver d = (Driver)dc.newInstance();

            Driver proxy = (Driver)Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{Driver.class},
                    new InvocationHandler(){
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                           Method m = dc.getMethod(method.getName(), method.getParameterTypes());
                           return m.invoke(d, args);
                        }
                    });

            DriverManager.registerDriver(proxy);
        }
        else {
            getClass().forName(cla.getDriverClassName());
        }

        if (cla.getUser() != null) {
            this.conn = DriverManager.getConnection(cla.getConnectionString(), cla.getUser(), cla.getPassword());
        }
        else {
            this.conn = DriverManager.getConnection(cla.getConnectionString());
        }
    }

    private void executeQuery(String s, int lineNumber) throws SQLException {
        s = removeRemarks(s);
        if (s.trim().length()==0)
            return;
        Statement stat=null;
        try {
            stat = conn.createStatement();
            if (cla.isVerbose()) {
                cla.getStdout().println(s);
            }
            boolean hasRes = stat.execute(s);
            queriesComplete ++;
            if (!cla.isInteractive()) {
                if (!cla.isQuiet()) {
                    long newClock = System.nanoTime();
                    if (newClock - lastCompleteReportedClock > 1000000000L) {
                        lastCompleteReportedClock = newClock;
                        cla.getStdout().print("Queries complete: "+queriesComplete+"\r");
                    }
                }
                return;
            }
            if (hasRes) {
                ResultSet rs = stat.getResultSet();
                try {
                    ResultSetMetaData rsMd = rs.getMetaData();

                    cla.getStdout().print("|");
                    for (int i=1; i<=rsMd.getColumnCount(); i++) {
                        cla.getStdout().print(" ");
                        cla.getStdout().print(rsMd.getColumnName(i));
                        cla.getStdout().print(" |");
                    }
                    cla.getStdout().println("");

                    int count=0;
                    rs.next();
                    while (!rs.isAfterLast()) {
                        count ++;
                        cla.getStdout().print("|");
                        for (int i=1; i<=rsMd.getColumnCount(); i++) {
                            cla.getStdout().print(" ");
                            cla.getStdout().print(rs.getObject(i));
                            cla.getStdout().print(" |");
                        }
                        cla.getStdout().println("");
                        rs.next();
                    }
                    cla.getStdout().println("Query OK, "+count+" row(s) returned.");
                } finally {
                    rs.close();
                }
            } else {
                cla.getStdout().println(String.format("Query OK, %d records updated.", stat.getUpdateCount()));
            }
        } catch (SQLException e) {
            cla.getStderr().println("Error " + "near line "+lineNumber+" in query "+s);
            cla.getStderr().flush();
            if (cla.getStopOnError())
                throw e;
            else {
                cla.getStderr().println(e.getMessage());
                cla.getStderr().flush();
            }
        } finally {
            if (stat != null)
                stat.close();
        }

    }

    private String removeRemarks(String s) {
        for (Pattern remark: getRemarks()) {
            Matcher m = remark.matcher(s);
            s = m.replaceAll("").trim(); // replace remark with empty string
            if (s.length() == 0)
                break;
        }
        return s;
    }

    private String joinRegex(Iterable<String> delims) {
        StringBuilder sb = new StringBuilder("");
        boolean isFirst=true;

        for(String delim : delims) {
            if (!isFirst)
                sb.append("|");
            isFirst = false;
            sb.append("(");
            sb.append(delim);
            sb.append(")");
        }
        return sb.toString();
    }

    public Pattern getDelimitersPattern() {
        if (delimitersPattern != null)
            return delimitersPattern;

        delimitersPattern = Pattern.compile(joinRegex(cla.getDelimiters()), Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);
        return delimitersPattern;
    }

    public Iterable<Pattern> getRemarks() {
        if (remarks != null)
            return remarks;
        remarks = new ArrayList<Pattern>();
        for (String remark: cla.getRemarks()) {
            remarks.add(Pattern.compile(remark, Pattern.CASE_INSENSITIVE|Pattern.MULTILINE));
        }
        return remarks;
    }
}
