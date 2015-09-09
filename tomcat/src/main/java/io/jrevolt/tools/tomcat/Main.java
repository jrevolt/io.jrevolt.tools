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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

    ///

    List<AppInfo> apps = new LinkedList<>();
    Map<Artifact, AppInfo> index = new HashMap<>();

    ///

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
                for (AppInfo app : apps) {
                    f.format("<tr><td><a href='%s'>%1$s</a></td><td>%s</td><td>%s</td><td>%s</td></tr>",
                            app.contextPath, app.artifact.asString(false),
                            app.artifact.getResolvedVersion(),
                            null
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
                updateApps();
                for (AppInfo app : apps) {
                    try {
                        StandardContext c = app.context;
                        c.stop();
                        c.setDocBase(app.artifact.getFile().getAbsolutePath());
                        c.start();
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
            Artifact artifact = Artifact.parse(gav);
            AppInfo app = new AppInfo(
                    path != null ? path : artifact.getArtifactId(),
                    artifact);
            apps.add(app);
            index.put(artifact, app);
        }

        updateApps();

        for (AppInfo app : apps) {
            StandardContext c = (StandardContext) tomcat.addWebapp(
                    app.contextPath,  app.artifact.getFile().getAbsolutePath());
            c.setPrivileged(true);
            app.context = c;
        }




        return tomcat;
    }

    private void updateApps() {
        List<Artifact> artifacts = new LinkedList<>();
        for (AppInfo app : apps) {
            artifacts.add(app.artifact);
        }
        List<Artifact> resolved = RepositorySupport.resolve(null, artifacts);
        for (Artifact artifact : resolved) {
            index.get(artifact).artifact = artifact;
        }
    }

    void run(String[] args) {
        try {
            tomcat.start();

            for (AppInfo app : apps) {
                try {
                    app.context.start();
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
