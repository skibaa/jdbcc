package tv.bee.jdbcc;

import java.io.*;
import java.sql.*;
import java.net.URLClassLoader;
import java.net.URL;
import java.net.MalformedURLException;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/* 
 * Main class for jdbcc application
 * Copyright 2008 by BeeTV http://bee.tv
 * Author Andrew Skiba <andrew@tikalk.com>
 */

public class Main {
    private Reader scriptReader;
    private String driverClassName;
    private String connectionString;
    private Connection conn;
    private String user;
    private String password;
    private boolean stopOnError;
    private String driverPath;
    private PrintWriter stdout;

    public Main(String scriptFileName, String driverClassName, String connectionString, String user, String password) {

    }

    public Main(CommandLineArgs cla) {
        this.scriptReader = cla.getScriptStream();
        this.driverClassName = cla.getDriverClassName();
        this.connectionString = cla.getConnectionString();
        this.user = cla.getUser();
        this.password = cla.getPassword();
        this.stopOnError = cla.getStopOnError();
        this.driverPath = cla.getDriverPath();
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
            lnr = new LineNumberReader(scriptReader);
            String line;
            StringBuffer sb = new StringBuffer();
            while ((line=lnr.readLine())!=null) {
                line.trim();
                sb.append("\n");
                sb.append(line);
                String stringSoFar = sb.toString();
                if (!stringSoFar.contains(";")) {
                    continue;
                }

                int semicolonIdx = stringSoFar.indexOf(';');

                executeQuery(stringSoFar.substring(0, semicolonIdx), lnr.getLineNumber());
                sb.setLength(0);
                sb.append(stringSoFar.substring(semicolonIdx+1));
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

    private void openConnection() throws ClassNotFoundException, SQLException, MalformedURLException, IllegalAccessException, InstantiationException, FileNotFoundException {
        if (driverPath != null) {
            File driverFile = new File(driverPath);
            if (!driverFile.exists())
                throw new FileNotFoundException(driverPath);
            ClassLoader cl = new URLClassLoader(new URL [] {
                    new URL("file://"+driverFile.getAbsolutePath())
            });

            final Class dc = Class.forName(driverClassName, true, cl);
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
            getClass().forName(driverClassName);
        }

        if (user != null) {
            this.conn = DriverManager.getConnection(connectionString, user, password);
        }
        else {
            this.conn = DriverManager.getConnection(connectionString);
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

            if (stopOnError)
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
}
