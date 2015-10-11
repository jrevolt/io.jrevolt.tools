package io.jrevolt.tools.tomcat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import org.apache.catalina.SessionIdGenerator;

import java.util.Random;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
@Component
@ConditionalOnMissingBean(SecureSessionIdGenerator.class)
public class UnsecureSessionIdGenerator implements SessionIdGenerator {

	String jvmRoute;
	int sessionIdLength;

	@Override
	public String getJvmRoute() {
		return jvmRoute;
	}

	@Override
	public void setJvmRoute(String jvmRoute) {
		this.jvmRoute = jvmRoute;
	}

	@Override
	public int getSessionIdLength() {
		return sessionIdLength;
	}

	@Override
	public void setSessionIdLength(int sessionIdLength) {
		this.sessionIdLength = sessionIdLength;
	}

	@Override
	public String generateSessionId() {
		return Integer.toString(new Random().nextInt());
	}

	@Override
	public String generateSessionId(String route) {
		return generateSessionId();
	}
}
