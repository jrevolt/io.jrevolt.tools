package io.jrevolt.tools.tomcat;

import io.jrevolt.launcher.RepositorySupport;
import io.jrevolt.launcher.mvn.Artifact;
import io.jrevolt.launcher.util.Log;
import io.jrevolt.launcher.vault.VaultConfiguration;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
@SpringBootApplication
public class Main {

    @Autowired
    ApplicationContext ctx;

    @Value("${tomcat.port}")
    int port;

    @Value("${tomcat.basedir}")
    File basedir;

    @Value("${tomcat.deploydir}")
    File deploydir;

    @Value("${webapp:}")
    String[] webapps;

    @Autowired
    Tomcat tomcat;

    List<StandardContext> contexts = new LinkedList<StandardContext>();

    @Bean
    Tomcat tomcat() throws ServletException {
        Assert.isTrue(basedir.exists() || basedir.mkdirs(), basedir.getAbsolutePath());
        Assert.isTrue(deploydir.exists() || deploydir.mkdirs(), deploydir.getAbsolutePath());

        final Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(basedir.getAbsolutePath());
        tomcat.getHost().setStartStopThreads(3);
        tomcat.getHost().setDeployOnStartup(false);

        tomcat.addUser("admin", "admin");
        tomcat.addRole("admin", "manager-gui");
        tomcat.addRole("admin", "admin-gui");

        RemoteIpValve v = new RemoteIpValve();
        v.setProtocolHeader("X-Forwarded-Proto");
        ((StandardEngine) tomcat.getEngine()).addValve(v);

        Context ctx = tomcat.addContext("", null);
        tomcat.addServlet("", "Versions", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                if (!req.getRequestURI().equals("/")) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                resp.setContentType("text/html");
                Formatter f = new Formatter(resp.getWriter());
                f.format("<html><body><table border=1>");
                f.format("<tr><th>Context</th><th>Artifact</th><th>Deployed</th><th>Available</th></tr>");
                for (StandardContext c : contexts) {
                    String gav = (String) c.getServletContext().getAttribute("mvn.uri");
                    Artifact artifact = (Artifact) c.getServletContext().getAttribute("mvn.artifact");
                    Artifact update = gav != null ? RepositorySupport.resolve(gav) : null;
                    f.format("<tr><td><a href='%s'>%1$s</a></td><td>%s</td><td>%s</td><td>%s</td></tr>",
                            c.getPath(), gav,
                            artifact.getResolvedVersion(),
                            update != null ? update.getResolvedVersion() : null
                    );
                }
                f.format("</table>");
                f.format("<a href='/update'>Update all</a>");
                f.format("</body></html>");
            }
        });
        ctx.addServletMapping("/", "Versions");

        tomcat.addServlet("", "Update", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                for (StandardContext c : contexts) {
                    try {
                        String gav = (String) c.getServletContext().getAttribute("mvn.uri");
                        Artifact update = gav != null ? RepositorySupport.resolve(gav) : null;
                        if (update == null) { continue; }
                        c.stop();
                        c.setDocBase(update.getFile().getAbsolutePath());
                        c.getServletContext().setAttribute("mvn.uri", gav);
                        c.getServletContext().setAttribute("mvn.artifact", update);
                        c.start();
                        System.out.println(update.getFile());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                resp.sendRedirect("/");
            }
        });
        ctx.addServletMapping("/update", "Update");

        Pattern parser = Pattern.compile("(?:([^=]+)=)?(.*)");
        for (String s : webapps) {
            Matcher m = parser.matcher(s);
            Assert.isTrue(m.matches());
            String path = m.group(1);
            String gav = m.group(2);
            Artifact artifact = RepositorySupport.resolve(gav);
            if (artifact == null || artifact.getFile() == null) {
                Log.error(null, "Cannot resolve artifact %s", gav);
                continue;
            }
            try {
                StandardContext c = (StandardContext) tomcat.addWebapp(
                        path != null ? path : artifact.getArtifactId(),
                        artifact.getFile().getAbsolutePath());
                c.setPrivileged(true);
                c.getServletContext().setAttribute("mvn.uri", gav);
                c.getServletContext().setAttribute("mvn.artifact", artifact);
                contexts.add(c);
            } catch (ServletException e) {
                throw new RuntimeException(e);
            }
        }

        return tomcat;
    }

    void run(String[] args) {
        try {
            tomcat.start();

            for (StandardContext c : contexts) {
                try {
                    c.start();
                } catch (LifecycleException e) {
                    e.printStackTrace();
                }
            }

            new CountDownLatch(1).await();
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        } finally {
            try { tomcat.stop(); } catch (Exception ignore) {}
        }
    }


    static public void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder()
                .sources(Main.class, VaultConfiguration.class)
                .environment(VaultConfiguration.initStandardEnvironment())
                .run(args);
        ctx.getBean(Main.class).run(args);
        ctx.close();
    }

}
