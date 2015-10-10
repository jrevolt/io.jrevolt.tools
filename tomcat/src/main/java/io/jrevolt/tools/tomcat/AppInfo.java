package io.jrevolt.tools.tomcat;

import io.jrevolt.launcher.mvn.Artifact;

import org.apache.catalina.core.StandardContext;

import java.util.LinkedList;
import java.util.List;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class AppInfo {

    String contextPath;
    Artifact artifact;
    List<Artifact> dependencies = new LinkedList<>();
    StandardContext context;


    public AppInfo(String contextPath, Artifact artifact) {
        this.contextPath = contextPath;
        this.artifact = artifact;
    }
}
