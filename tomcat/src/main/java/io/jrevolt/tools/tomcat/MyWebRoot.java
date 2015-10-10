package io.jrevolt.tools.tomcat;

import io.jrevolt.launcher.mvn.Artifact;

import org.apache.catalina.Context;
import org.apache.catalina.WebResource;
import org.apache.catalina.webresources.AbstractArchiveResourceSet;
import org.apache.catalina.webresources.JarResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.util.LinkedList;
import java.util.List;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MyWebRoot extends StandardRoot {

	AppInfo app;

	List<AbstractArchiveResourceSet> jars = new LinkedList<>();

	public MyWebRoot(Context context, AppInfo app) {
		super(context);
		this.app = app;
		for (Artifact a : app.dependencies) {
			JarResourceSet resource = new JarResourceSet(this, "/", a.getFile().getAbsolutePath(), "/");
			addJarResources(resource);
			jars.add(resource);
		}
	}

	@Override
	public WebResource getClassLoaderResource(String path) {
		for (AbstractArchiveResourceSet jar : jars) {
			WebResource found = jar.getResource(path);
			if (found.exists()) {
				return found;
			}
		}
		return super.getClassLoaderResource(path);
	}
}
