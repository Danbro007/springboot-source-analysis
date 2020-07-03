/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.bind.handler.IgnoreErrorsBindHandler;
import org.springframework.boot.context.properties.bind.handler.IgnoreTopLevelConverterNotFoundBindHandler;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.UnboundElementsSourceFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.PropertySources;
import org.springframework.util.Assert;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

/**
 * Internal class by the {@link ConfigurationPropertiesBindingPostProcessor} to handle the
 * actual {@link ConfigurationProperties} binding.
 *
 * ConfigurationPropertiesBindingPostProcessor 的内部类来处理 ConfigurationProperties 标注的类的实际绑定。
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 */
class ConfigurationPropertiesBinder {

	private final ApplicationContext applicationContext;

	private final PropertySources propertySources;

	private final Validator configurationPropertiesValidator;

	private final boolean jsr303Present;

	private volatile Validator jsr303Validator;

	private volatile Binder binder;

	ConfigurationPropertiesBinder(ApplicationContext applicationContext,
			String validatorBeanName) {
		this.applicationContext = applicationContext;
		// 将 applicationContext 和 配置文件 封装到 PropertySourcesDeducer
		this.propertySources = new PropertySourcesDeducer(applicationContext)
				.getPropertySources();
		// 设置属性校验器
		this.configurationPropertiesValidator = getConfigurationPropertiesValidator(
				applicationContext, validatorBeanName);
		// 检查实现JSR-303规范的bean校验器相关类在classpath中是否存在
		this.jsr303Present = ConfigurationPropertiesJsr303Validator
				.isJsr303Present(applicationContext);
	}
	// 先获取注解(里面包含设置的 @ConfigurationProperties 属性)和校验器，
	// 创建绑定处理器，给绑定器设置校验器，根据注解的属性配置不同的绑定处理器
	// 使用绑定器开始对目标类绑定属性
	public void bind(Bindable<?> target) {
		// 获取 @ConfigurationProperties 注解
		ConfigurationProperties annotation = target
				.getAnnotation(ConfigurationProperties.class);
		Assert.state(annotation != null,
				() -> "Missing @ConfigurationProperties on " + target);
		List<Validator> validators = getValidators(target);
		// 获取绑定处理器，通过 @ConfigurationProperties 里的属性配置不同的绑定处理器，然后给它配置校验器
		BindHandler bindHandler = getBindHandler(annotation, validators);
		// 用绑定器绑定属性
		getBinder().bind(annotation.prefix(), target, bindHandler);
	}

	private Validator getConfigurationPropertiesValidator(
			ApplicationContext applicationContext, String validatorBeanName) {
		if (applicationContext.containsBean(validatorBeanName)) {
			return applicationContext.getBean(validatorBeanName, Validator.class);
		}
		return null;
	}

	private List<Validator> getValidators(Bindable<?> target) {
		List<Validator> validators = new ArrayList<>(3);
		if (this.configurationPropertiesValidator != null) {
			validators.add(this.configurationPropertiesValidator);
		}
		if (this.jsr303Present && target.getAnnotation(Validated.class) != null) {
			validators.add(getJsr303Validator());
		}
		if (target.getValue() != null && target.getValue().get() instanceof Validator) {
			validators.add((Validator) target.getValue().get());
		}
		return validators;
	}

	private Validator getJsr303Validator() {
		if (this.jsr303Validator == null) {
			this.jsr303Validator = new ConfigurationPropertiesJsr303Validator(
					this.applicationContext);
		}
		return this.jsr303Validator;
	}
	// 获取绑定处理器
	private BindHandler getBindHandler(ConfigurationProperties annotation,
			List<Validator> validators) {
		// 一开始先创建一个能忽略 ConverterNotFoundException 异常的绑定处理器
		BindHandler handler = new IgnoreTopLevelConverterNotFoundBindHandler();
		// 如果 @ConfigurationProperties 注解的 ignoreInvalidFields 属性为 true
		// 则说明可以忽略无效的配置属性例如类型错误，此时新建一个IgnoreErrorsBindHandler对象
		if (annotation.ignoreInvalidFields()) {
			// IgnoreErrorsBindHandler 是能忽略绑定错误的绑定处理器
			handler = new IgnoreErrorsBindHandler(handler);
		}
		// 如果 @ConfigurationProperties 注解的 ignoreUnknownFields 属性为 false
		// 则说明未知的属性不能被忽略
		if (!annotation.ignoreUnknownFields()) {
			// 创建一个未绑定元素过滤器
			UnboundElementsSourceFilter filter = new UnboundElementsSourceFilter();
			// NoUnboundElementsBindHandler 是强制所有的属性都要被绑定
			handler = new NoUnboundElementsBindHandler(handler, filter);
		}
		// 如果有校验器则创建一个 ValidationBindHandler 对象，来对绑定结果校验。
		if (!validators.isEmpty()) {
			handler = new ValidationBindHandler(handler,
					validators.toArray(new Validator[0]));
		}
		// 应用 ConfigurationPropertiesBindHandlerAdvisor ，对处理器进行增强
		for (ConfigurationPropertiesBindHandlerAdvisor advisor : getBindHandlerAdvisors()) {
			handler = advisor.apply(handler);
		}
		return handler;
	}

	private List<ConfigurationPropertiesBindHandlerAdvisor> getBindHandlerAdvisors() {
		return this.applicationContext
				.getBeanProvider(ConfigurationPropertiesBindHandlerAdvisor.class)
				.orderedStream().collect(Collectors.toList());
	}
	// 如果绑定器不存在则创建一个新的绑定器配置上配置属性源、占位符解析器、参数转换器 和 属性编辑器初始化器
	private Binder getBinder() {
		if (this.binder == null) {
			this.binder = new Binder(getConfigurationPropertySources(),
					getPropertySourcesPlaceholdersResolver(), getConversionService(),
					getPropertyEditorInitializer());
		}
		return this.binder;
	}

	private Iterable<ConfigurationPropertySource> getConfigurationPropertySources() {
		return ConfigurationPropertySources.from(this.propertySources);
	}

	private PropertySourcesPlaceholdersResolver getPropertySourcesPlaceholdersResolver() {
		return new PropertySourcesPlaceholdersResolver(this.propertySources);
	}

	private ConversionService getConversionService() {
		return new ConversionServiceDeducer(this.applicationContext)
				.getConversionService();
	}

	private Consumer<PropertyEditorRegistry> getPropertyEditorInitializer() {
		if (this.applicationContext instanceof ConfigurableApplicationContext) {
			return ((ConfigurableApplicationContext) this.applicationContext)
					.getBeanFactory()::copyRegisteredEditorsTo;
		}
		return null;
	}

}
