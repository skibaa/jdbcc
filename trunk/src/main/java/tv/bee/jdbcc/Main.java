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
    private PrintWriter stdout;
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
            System.err.println(e.getMessage());
            System.exit(1);
        }
        new Main(cla).run();
    }

    public void run() throws Exception {

        openConnection();
        LineNumberReader lnr = null;
        try {
            lnr = new LineNumberReader(cla.getScriptStream());
            String line;
            StringBuffer sb = new StringBuffer();
            while ((line=lnr.readLine())!=null) {
                line.trim();
                sb.append("\n");
                sb.append(line);
                String query;

                while ((query=extractOneQuery(sb)) != null)
                    executeQuery(query, lnr.getLineNumber());

            }
            if (sb.length() > 0)
                executeQuery(sb.toString(), lnr.getLineNumber());
        } finally {
            if (lnr != null) {
                lnr.close();
            }
            conn.close();
        }
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

                stdout.print("|");
                for (int i=1; i<=rsMd.getColumnCount(); i++) {
                    stdout.print(" ");
                    stdout.print(rsMd.getColumnName(i));
                    stdout.print(" |");
                }
                stdout.println("");

                int count=0;
                rs.next();
                while (!rs.isAfterLast()) {
                    count ++;
                    stdout.print("|");
                    for (int i=1; i<=rsMd.getColumnCount(); i++) {
                        stdout.print(" ");
                        stdout.print(rs.getObject(i));
                        stdout.print(" |");
                    }
                    stdout.println("");
                    rs.next();
                }
                stdout.println("Query OK, "+count+" row(s) returned.");
            } else {
                stdout.println(String.format("Query OK, %d records updated.", stat.getUpdateCount()));
            }
        } catch (SQLException e) {
            System.err.println("Error " + "near line "+lineNumber+" in query "+s);

            if (cla.getStopOnError())
                throw e;
            else
                System.err.println(e.getMessage());
        }

    }

    public void setStdout(PrintWriter stdout) {
        if (stdout != null)
            this.stdout = stdout;
        stdout = new PrintWriter(System.out);
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
