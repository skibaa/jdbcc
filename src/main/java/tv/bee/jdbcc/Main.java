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

/* 
 * Main class for jdbcc application
 * Copyright 2008 by BeeTV http://bee.tv
 * Author Andrew Skiba <andrew@tikalk.com>
 */

public class Main {
    private CommandLineArgs cla;
    private Connection conn;
    private Pattern delimitersPattern;

    public Main(CommandLineArgs cla) {
        this.cla = cla;
    }

    static public void main(String [] args) throws Exception {
        CommandLineArgs cla=null;
        try {
            cla = new CommandLineArgs(args);
        } catch (
            CommandLineArgs.BadArgsException e) {
            cla.getStderr().println(e.getMessage());
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
            line.trim();
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
        String [] parts = getDelimitersPattern().split(stringSoFar, 2);
        if (parts.length < 2)
            return null;
        stringSoFar.setLength(0);
        stringSoFar.append(parts[1]);
        return parts[0];
    }

    private void openConnection() throws ClassNotFoundException, SQLException, MalformedURLException, IllegalAccessException, InstantiationException, FileNotFoundException {
        if (cla.getDriverPath() != null) {
            File driverFile = new File(cla.getDriverPath());
            if (!driverFile.exists())
                throw new FileNotFoundException(cla.getDriverPath());
            ClassLoader cl = new URLClassLoader(new URL [] {
                    new URL("file://"+driverFile.getAbsolutePath())
            });

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
        try {
            Statement stat = conn.createStatement();
            boolean hasRes = stat.execute(s);
            if (hasRes) {
                ResultSet rs = stat.getResultSet();
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
            } else {
                cla.getStdout().println(String.format("Query OK, %d records updated.", stat.getUpdateCount()));
            }
        } catch (SQLException e) {
            cla.getStderr().println("Error " + "near line "+lineNumber+" in query "+s);

            if (cla.getStopOnError())
                throw e;
            else {
                cla.getStderr().println(e.getMessage());
                cla.getStderr().flush();
            }
        }

    }

    private String joinRegex(Iterable<String> delims) {
        StringBuilder sb = new StringBuilder("");
        boolean isFirst=true;

        for(String delim : delims) {
            if (!isFirst)
                sb.append("|");
            isFirst = false;
            sb.append("(");
            if (Character.isLetterOrDigit(delim.charAt(0)))
                sb.append("\\W");//if delimiter starts from letter, a non-letter character must preceede
            sb.append(delim);
            if (Character.isLetterOrDigit(delim.charAt(delim.length()-1)))
                sb.append("\\W");//if delimiter ends on letter, a non-letter character must follow
            sb.append(")");
        }
        return sb.toString();
    }

    public Pattern getDelimitersPattern() {
        if (delimitersPattern != null)
            return delimitersPattern;

        delimitersPattern = Pattern.compile(joinRegex(cla.getDelimiters()), Pattern.CASE_INSENSITIVE);
        return delimitersPattern;
    }
}
