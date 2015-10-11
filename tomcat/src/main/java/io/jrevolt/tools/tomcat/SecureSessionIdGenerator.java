package io.jrevolt.tools.tomcat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.apache.catalina.util.StandardSessionIdGenerator;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
@Component
@Profile("production")
public class SecureSessionIdGenerator extends StandardSessionIdGenerator {
}
