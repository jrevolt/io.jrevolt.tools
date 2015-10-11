package io.jrevolt.tools.tomcat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.session.StandardManager;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
@Component
@Scope("prototype")
public class MyManager extends StandardManager {

	@Autowired
	public MyManager(SessionIdGenerator sessionIdGenerator) {
		setSessionIdGenerator(sessionIdGenerator);
	}
}
