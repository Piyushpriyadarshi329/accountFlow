package com.accountflow.config;

import java.math.BigDecimal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Renders every monetary value as a JSON string, at its exact scale.
 *
 * <p>Two reasons. A JSON number is an IEEE-754 double to most clients -
 * {@code JSON.parse} in a browser turns 12000.00 into a float, reintroducing
 * exactly the precision loss BigDecimal exists to prevent. And Jackson
 * normalises a numeric BigDecimal, so 12000.00 would serialize as 12000.0 and
 * lose the currency's scale.
 */
@Configuration
public class JacksonConfig {

	@Bean
	JacksonModule moneyModule() {
		SimpleModule module = new SimpleModule("accountflow-money");
		module.addSerializer(BigDecimal.class, new ExactDecimalSerializer());
		return module;
	}

	static final class ExactDecimalSerializer extends ValueSerializer<BigDecimal> {

		@Override
		public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
			generator.writeString(value.toPlainString());
		}

	}

}
