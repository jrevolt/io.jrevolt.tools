package io.jrevolt.tools.tomcat;

import io.jrevolt.launcher.RepositorySupport;
import io.jrevolt.launcher.mvn.Artifact;
import io.jrevolt.launcher.vault.VaultConfiguration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
@SpringBootApplication
public class Main {

    static Logger LOG = LoggerFactory.getLogger(Main.class);

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
    Tomcat tomcat(MyManager manager) throws ServletException {
        Assert.isTrue(basedir.exists() || basedir.mkdirs(), basedir.getAbsolutePath());
        Assert.isTrue(deploydir.exists() || deploydir.mkdirs(), deploydir.getAbsolutePath());

        final Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(basedir.getAbsolutePath());
        tomcat.getHost().setStartStopThreads(3);
        tomcat.getHost().setDeployOnStartup(false);
        ((StandardHost) tomcat.getHost()).setStartChildren(false);

        tomcat.addUser("admin", "admin");
        tomcat.addRole("admin", "manager-gui");
        tomcat.addRole("admin", "admin-gui");

        RemoteIpValve v = new RemoteIpValve();
        v.setProtocolHeader("X-Forwarded-Proto");
        ((StandardEngine) tomcat.getEngine()).addValve(v);

        Context ctx = tomcat.addContext("", null);
        ctx.setManager(manager);

        tomcat.addServlet("", "Versions", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                if (!req.getRequestURI().equals("/")) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                updateApps();

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
                    redeploy(app);
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

        return tomcat;
    }

    private void updateApps() {
        for (AppInfo app : apps) {
            Artifact war = Artifact.parse(app.artifact.asString());
            List<Artifact> resolved = RepositorySupport.resolve(war);
            resolved.remove(war);
            app.artifact = war;
            app.dependencies = resolved;
        }
    }


    void redeploy(AppInfo app) {
        try {
            long started = System.currentTimeMillis();

            if (app.context != null) { app.context.stop(); }
            tomcat.getHost().removeChild(app.context);
            app.context = null;

            LOG.info("Undeployed {} in {} msec", app.contextPath, System.currentTimeMillis() - started);

            started = System.currentTimeMillis();

            StandardContext c = (StandardContext) tomcat.addWebapp(
                  app.contextPath,  app.artifact.getFile().getAbsolutePath());
            c.setPrivileged(true);
            c.setResources(new MyWebRoot(c, app));
            c.setManager(ctx.getBean(MyManager.class));

            app.context = c;

            c.start();

            LOG.info("Deployed {} in {} msec", app.contextPath, System.currentTimeMillis() - started);

        } catch (LifecycleException e) {
            throw new UnsupportedOperationException(e);
        } catch (ServletException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    void run(String[] args) {
        try {
            tomcat.start();
            updateApps();
            for (AppInfo app : apps) {
                ForkJoinPool.commonPool().submit(()->{redeploy(app);});
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
